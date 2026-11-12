'use strict';
// End-to-end smoke test for the relay: host + listener, audio, commands, host reconnect, room end.
process.env.QUIET = '1';
process.env.HOST_GRACE_MS = '1500';
const assert = require('assert');
const WebSocket = require('ws');
const { start } = require('../server');

function client(url) {
  const ws = new WebSocket(url);
  const inbox = [];
  const waiters = [];
  ws.on('message', (data, isBinary) => {
    const msg = isBinary ? { binary: Buffer.from(data) } : JSON.parse(data.toString());
    const i = waiters.findIndex((w) => w.match(msg));
    if (i >= 0) waiters.splice(i, 1)[0].resolve(msg);
    else inbox.push(msg);
  });
  return {
    ws,
    open: () => new Promise((r, j) => { ws.once('open', r); ws.once('error', j); }),
    send: (m) => ws.send(Buffer.isBuffer(m) ? m : JSON.stringify(m)),
    next(match, ms = 2000) {
      const i = inbox.findIndex(match);
      if (i >= 0) return Promise.resolve(inbox.splice(i, 1)[0]);
      return new Promise((resolve, reject) => {
        const w = { match, resolve };
        waiters.push(w);
        setTimeout(() => reject(new Error('timeout waiting for message')), ms);
      });
    },
    close: () => ws.close(),
  };
}

const t = (type) => (m) => m.t === type;

(async () => {
  const relay = start(0);
  await new Promise((r) => relay.server.once('listening', r));
  const url = `ws://127.0.0.1:${relay.server.address().port}`;

  // Host creates a room.
  const host = client(url);
  await host.open();
  host.send({ t: 'host' });
  const room = await host.next(t('room'));
  assert.match(room.code, /^[A-Z2-9]{6}$/);
  console.log('ok  host created room', room.code);

  // Host publishes format before anyone joins (must be cached).
  host.send({ t: 'format', codec: 'aac', sampleRate: 48000, channels: 2, csd: 'EZA=' });
  host.send({ t: 'nowPlaying', title: 'Song', artist: 'Artist', app: 'YouTube Music', playing: true });

  // Wrong code is rejected.
  const stranger = client(url);
  await stranger.open();
  stranger.send({ t: 'join', code: 'ZZZZZZ' });
  assert.equal((await stranger.next(t('error'))).code, 'not_found');
  stranger.close();
  console.log('ok  unknown room rejected');

  // Listener joins (lowercase code should work) and gets cached format + nowPlaying.
  const listener = client(url);
  await listener.open();
  listener.send({ t: 'join', code: room.code.toLowerCase() });
  await listener.next(t('joined'));
  assert.equal((await listener.next(t('format'))).sampleRate, 48000);
  assert.equal((await listener.next(t('nowPlaying'))).title, 'Song');
  assert.equal((await host.next((m) => m.t === 'peers' && m.count === 1)).count, 1);
  console.log('ok  listener joined, received cached format + nowPlaying, host sees 1 peer');

  // Audio frames go host -> listener unchanged.
  const frame = Buffer.from([1, 0, 0, 0, 0, 0, 0, 0, 42, 9, 8, 7]);
  host.send(frame);
  const got = await listener.next((m) => m.binary);
  assert.deepEqual(got.binary, frame);
  console.log('ok  audio frame relayed');

  // Commands go listener -> host; unknown commands are dropped.
  listener.send({ t: 'cmd', action: 'rm -rf' });
  listener.send({ t: 'cmd', action: 'pause' });
  assert.equal((await host.next(t('cmd'))).action, 'pause');
  console.log('ok  pause command relayed, invalid command dropped');

  // Host drops and reclaims the room with its key.
  host.close();
  await listener.next(t('hostAway'));
  const host2 = client(url);
  await host2.open();
  host2.send({ t: 'host', code: room.code, key: 'wrong' });
  assert.equal((await host2.next(t('error'))).code, 'room_gone');
  host2.send({ t: 'host', code: room.code, key: room.key });
  assert.equal((await host2.next(t('room'))).code, room.code);
  await listener.next(t('hostBack'));
  console.log('ok  host reconnect: wrong key refused, right key reclaims, listener notified');

  // Host ends the session -> listener told and room removed.
  host2.send({ t: 'end' });
  assert.equal((await listener.next(t('ended'))).reason, 'host_ended');
  assert.equal(relay.rooms.size, 0);
  console.log('ok  host ended session');

  // Room expires if the host never comes back.
  const host3 = client(url);
  await host3.open();
  host3.send({ t: 'host' });
  const room3 = await host3.next(t('room'));
  const l3 = client(url);
  await l3.open();
  l3.send({ t: 'join', code: room3.code });
  await l3.next(t('joined'));
  host3.close();
  assert.equal((await l3.next(t('ended'), 4000)).reason, 'host_left');
  console.log('ok  room expires after host grace period');

  await relay.close();
  console.log('\nall relay tests passed');
  process.exit(0);
})().catch((e) => {
  console.error('FAIL', e);
  process.exit(1);
});
