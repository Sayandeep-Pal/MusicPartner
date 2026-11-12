'use strict';
/**
 * MusicPartner relay server.
 *
 * One host (the phone playing music) creates a room and gets a 6-character code.
 * Friends join with that code. The server:
 *   - relays binary audio frames host -> listeners (dropping frames for slow listeners
 *     so latency never piles up),
 *   - relays control commands listeners -> host (play / pause / next / prev),
 *   - relays "format" and "nowPlaying" host -> listeners and caches them for late joiners,
 *   - keeps a room alive for HOST_GRACE_MS after the host drops so a phone that briefly
 *     loses signal can reclaim the same room with its secret key.
 */
const http = require('http');
const crypto = require('crypto');
const { WebSocketServer, WebSocket } = require('ws');

const HOST_GRACE_MS = Number(process.env.HOST_GRACE_MS) || 120_000;
const MAX_LISTENERS = Number(process.env.MAX_LISTENERS) || 8;
const MAX_LISTENER_BUFFER = 256 * 1024; // bytes queued for one listener before we drop audio
const HEARTBEAT_MS = 20_000;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // no 0/O, 1/I
const COMMANDS = new Set(['play', 'pause', 'toggle', 'next', 'prev']);

function log(...args) {
  if (process.env.QUIET !== '1') console.log(new Date().toISOString(), ...args);
}

function start(port = Number(process.env.PORT) || 8080) {
  const rooms = new Map();

  function newCode() {
    for (;;) {
      let code = '';
      for (const b of crypto.randomBytes(6)) code += CODE_ALPHABET[b % CODE_ALPHABET.length];
      if (!rooms.has(code)) return code;
    }
  }

  function send(ws, msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(typeof msg === 'string' ? msg : JSON.stringify(msg));
    }
  }

  function broadcast(room, text) {
    for (const l of room.listeners) send(l, text);
  }

  function sendPeers(room) {
    send(room.host, { t: 'peers', count: room.listeners.size });
  }

  function attachHost(ws, room) {
    ws.role = 'host';
    ws.room = room;
    room.host = ws;
    if (room.graceTimer) {
      clearTimeout(room.graceTimer);
      room.graceTimer = null;
      broadcast(room, JSON.stringify({ t: 'hostBack' }));
      log('host reclaimed', room.code);
    }
    send(ws, { t: 'room', code: room.code, key: room.key });
    sendPeers(room);
  }

  function createRoom(ws) {
    const room = {
      code: newCode(),
      key: crypto.randomBytes(16).toString('hex'),
      host: null,
      listeners: new Set(),
      format: null,
      nowPlaying: null,
      graceTimer: null,
    };
    rooms.set(room.code, room);
    log('room created', room.code);
    attachHost(ws, room);
  }

  function closeRoom(room, reason) {
    if (room.graceTimer) clearTimeout(room.graceTimer);
    rooms.delete(room.code);
    for (const l of room.listeners) {
      send(l, { t: 'ended', reason });
      l.room = null;
      l.close(1000, 'room closed');
    }
    room.listeners.clear();
    log('room closed', room.code, reason);
  }

  function onHello(ws, msg) {
    if (msg.t === 'host') {
      if (msg.code) {
        const room = rooms.get(String(msg.code).toUpperCase());
        if (room && msg.key && room.key === msg.key) {
          if (room.host && room.host !== ws) {
            room.host.room = null;
            room.host.close(4000, 'replaced');
          }
          return attachHost(ws, room);
        }
        return send(ws, { t: 'error', code: 'room_gone', msg: 'Your previous room expired. Creating a new one.' });
      }
      return createRoom(ws);
    }

    if (msg.t === 'join') {
      const code = String(msg.code || '').trim().toUpperCase();
      const room = rooms.get(code);
      if (!room) return send(ws, { t: 'error', code: 'not_found', msg: `No room with code ${code}.` });
      if (room.listeners.size >= MAX_LISTENERS) return send(ws, { t: 'error', code: 'full', msg: 'This room is full.' });
      ws.role = 'listener';
      ws.room = room;
      room.listeners.add(ws);
      send(ws, { t: 'joined', code: room.code, hostOnline: !!room.host });
      if (room.format) send(ws, room.format);
      if (room.nowPlaying) send(ws, room.nowPlaying);
      sendPeers(room);
      log('listener joined', room.code, `(${room.listeners.size})`);
      return;
    }

    send(ws, { t: 'error', code: 'hello_first', msg: 'Send "host" or "join" first.' });
  }

  function onText(ws, text) {
    let msg;
    try {
      msg = JSON.parse(text);
    } catch {
      return send(ws, { t: 'error', code: 'bad_json', msg: 'Invalid message.' });
    }
    if (!msg || typeof msg.t !== 'string') return;
    if (!ws.role) return onHello(ws, msg);

    const room = ws.room;
    if (!room) return;

    if (ws.role === 'host') {
      if (msg.t === 'format') {
        room.format = text;
        broadcast(room, text);
      } else if (msg.t === 'nowPlaying') {
        room.nowPlaying = text;
        broadcast(room, text);
      } else if (msg.t === 'end') {
        room.host = null;
        ws.room = null;
        closeRoom(room, 'host_ended');
      }
    } else if (ws.role === 'listener' && msg.t === 'cmd' && COMMANDS.has(msg.action)) {
      send(room.host, { t: 'cmd', action: msg.action });
    }
  }

  function onBinary(ws, data) {
    const room = ws.room;
    if (ws.role !== 'host' || !room) return;
    for (const l of room.listeners) {
      // A listener on a slow connection gets frames dropped instead of an ever-growing delay.
      if (l.readyState === WebSocket.OPEN && l.bufferedAmount < MAX_LISTENER_BUFFER) {
        l.send(data, { binary: true });
      }
    }
  }

  function onClose(ws) {
    const room = ws.room;
    if (!room) return;
    if (ws.role === 'host' && room.host === ws) {
      room.host = null;
      broadcast(room, JSON.stringify({ t: 'hostAway' }));
      room.graceTimer = setTimeout(() => closeRoom(room, 'host_left'), HOST_GRACE_MS);
      log('host dropped', room.code, `(grace ${HOST_GRACE_MS}ms)`);
    } else if (ws.role === 'listener') {
      room.listeners.delete(ws);
      sendPeers(room);
      log('listener left', room.code, `(${room.listeners.size})`);
    }
  }

  const server = http.createServer((req, res) => {
    res.writeHead(200, { 'content-type': 'text/plain' });
    res.end(`MusicPartner relay OK. rooms=${rooms.size}\n`);
  });

  const wss = new WebSocketServer({ server, maxPayload: 64 * 1024 });

  wss.on('connection', (ws) => {
    ws.isAlive = true;
    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (data, isBinary) => {
      if (isBinary) onBinary(ws, data);
      else onText(ws, data.toString());
    });
    ws.on('close', () => onClose(ws));
    ws.on('error', () => {});
  });

  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (ws.isAlive === false) {
        ws.terminate();
        continue;
      }
      ws.isAlive = false;
      ws.ping();
    }
  }, HEARTBEAT_MS);

  server.listen(port, () => log(`relay listening on :${server.address().port}`));

  return {
    server,
    rooms,
    close() {
      clearInterval(heartbeat);
      for (const room of rooms.values()) if (room.graceTimer) clearTimeout(room.graceTimer);
      for (const ws of wss.clients) ws.terminate();
      wss.close();
      return new Promise((r) => server.close(() => r()));
    },
  };
}

module.exports = { start };

if (require.main === module) start();
