# MusicPartner — Plan & Progress

_Last updated: 2026-11-12_

## 1. The idea

Two friends in different places listen to **the same song at the same moment**.

- The music can come from **any app**: YouTube Music, Spotify, YouTube, a browser tab, and so on.
- **Both** people can play, pause and skip.
- The main goals are **connectivity** and **sync**.
- **Android only.** iPhone is not a target.

## 2. Key decisions

| Decision | Choice | Why |
|---|---|---|
| App or website | **Native Android app** | A website can't hear or control audio from other apps. Only a native app can use Android's audio capture and media-control features. |
| Language / UI | **Kotlin + Jetpack Compose** | The core features are low-level Android APIs, so Kotlin is the most direct option. |
| How "any source" works | The **host phone captures its own music audio** and streams it to the friend | The friend doesn't need the song, the music app, or a subscription. |
| Audio capture | `AudioPlaybackCapture` (Android 10+) through `MediaProjection` | This is the only official way to capture other apps' audio. |
| Play/pause/skip on any app | `MediaSessionManager` + notification-access permission | One way to control YouTube Music, Spotify, YouTube and Chrome. |
| Codec | **AAC-LC, 48 kHz stereo, 128 kbps**, using the phone's built-in MediaCodec | Hardware/OS codec on every Android phone, good quality, about 58 MB per hour. |
| Transport over the internet | **WebSocket relay server** (Node.js) | Works on any mobile network or Wi-Fi without NAT/TURN problems, and is simple and reliable. WebRTC could replace it later to lower delay (see roadmap). |
| Minimum Android | **Android 10 (API 29)** | The first version with AudioPlaybackCapture. |

### Two editions (added 2026-11-12, because of Google Play Protect)
Play Protect's fraud protection **blocks** sideloaded APKs (sent through WhatsApp, Drive, a browser or a file manager) that declare a notification-listener service. That service is what full media-session control needs. So the app is built in two flavors:

| | **Lite** (`com.musicpartner.lite`) | **Full** (`com.musicpartner`) |
|---|---|---|
| How to install | Share the APK any way you like. Play Protect may warn ("Install anyway"), but shouldn't block it. | USB / adb only (Play Protect blocks sideloading it) |
| Host + listener | ✅ | ✅ |
| Play/pause/skip from both phones | ✅ via media-button events (like Bluetooth headphones) to the last-playing app | ✅ via MediaSession transport controls |
| Song title / app name shown | ❌ only "Music is playing / Paused" | ✅ |
| Capture verdict | ✅ (uses `AudioManager.isMusicActive`) | ✅ |

The two editions have different package names, so both can be installed on one phone. They talk to each other normally (same protocol).

## 3. How it works

```
HOST PHONE                                   RELAY SERVER                 FRIEND'S PHONE
───────────                                  ────────────                 ──────────────
YT Music / Spotify / YouTube plays
        │
AudioPlaybackCapture (PCM 48k stereo)
        │
AAC encoder (MediaCodec) ──── audio frames ──► room ABC123 ──────────────► jitter buffer
                                              (drops frames for slow      → AAC decoder
                                               listeners, so delay           → speaker
                                               never piles up)
MediaHub ◄──── play/pause/next/prev ───────── room ABC123 ◄──────────────── buttons
(controls the music app)
MediaHub ───── now playing (title/app) ─────► cached + relayed ──────────► "Now playing" card
```

### Roles
- **Host:** the phone that plays music. Gets a 6-character room code.
- **Listener:** the friend's phone. Joins with the code. Up to 8 listeners per room.
- **Test mode:** the host side without any network. Checks whether audio capture works for a given music app.

### Wire protocol (`server/server.js` ⇄ `app/.../net/Protocol.kt`)
Text messages are JSON with a `t` field:

| From → To | Message |
|---|---|
| host → server | `{t:"host"}` creates a room; `{t:"host",code,key}` reclaims it after a network drop; `{t:"end"}` closes it |
| server → host | `{t:"room",code,key}`, `{t:"peers",count}`, `{t:"cmd",action}`, `{t:"error",code,msg}` |
| host → listeners | `{t:"format",codec:"aac",sampleRate,channels,csd}`, `{t:"nowPlaying",title,artist,app,playing}` (the server caches both for late joiners) |
| listener → server | `{t:"join",code}`, `{t:"cmd",action}` where action is `play` / `pause` / `toggle` / `next` / `prev` |
| server → listener | `{t:"joined"}`, `{t:"hostAway"}`, `{t:"hostBack"}`, `{t:"ended",reason}`, `{t:"error"}` |

Binary audio frame (host → listeners): `[1 byte = 1][8-byte pts in µs, big-endian][raw AAC frame]`.

### Resilience
- The app reconnects automatically with backoff: 1, 2, 4, 8, then every 10 s.
- If the host drops, the room stays alive for **2 minutes**. The host reclaims the same code with a secret key, and the friend sees "waiting for your friend" without having to rejoin.
- Slow networks: both the phone (128 KB send-queue cap) and the server (256 KB per listener) drop frames instead of queueing them. A bad connection causes short gaps, never a growing delay.
- Listener jitter buffer: playback starts at about 130 ms buffered. If more than about 640 ms piles up, the oldest audio is dropped back down to about 170 ms.
- Foreground services, a wake lock and a Wi-Fi lock keep both phones streaming with the screen off.

## 4. Steps and status

| # | Step | Status |
|---|---|---|
| 0 | Build toolchain + APK builds cleanly | ✅ Done (compiles, lint has 0 errors) |
| 1 | **Capture feasibility test.** Capture system audio and show automatically whether each music app allows capture. | ✅ Code written. ⏳ Needs a test on a real phone |
| 2 | Local hotspot / Wi-Fi mode without internet | ⏸ **Skipped for now**, at your request |
| 3 | **Shared controls.** Play/pause/next/prev from both phones, plus "now playing". | ✅ Code written. ⏳ Needs a test on a real phone |
| 4 | **Internet streaming.** Relay server, room codes, AAC streaming, reconnect. | ✅ Code written. ✅ Server tested (automated). ⏳ App needs a test on a real phone |
| 5 | Same-moment playback on the host (delay compensation) | 🔜 Planned |
| 6 | Lower delay with WebRTC + Opus (UDP instead of TCP) | 🔜 Planned |
| 7 | Hotspot / LAN mode (old step 2) | 🔜 Planned |
| 8 | Polish: album art, app icon, onboarding, battery-optimisation prompt | 🔜 Planned |

## 5. What has been built (as of 2026-11-12)

### Relay server: `server/`
- `server.js`: rooms with 6-character codes (no confusable 0/O/1/I), host reclaim with a secret key, 2-minute grace period, per-listener backpressure, a whitelist of allowed commands, 64 KB message limit, ping/pong heartbeat, and a plain HTTP health check at `/` for hosting platforms.
- `test/smoke.js`: an end-to-end test covering room creation, wrong code, cached format/now-playing for late joiners, audio relay, command relay and filtering, host reconnect (wrong key refused, right key accepted), host ending the session, and room expiry. **All tests pass.**

### Android app: `app/`
| File | What it does |
|---|---|
| `MainActivity.kt` | Permission flow (record-audio, notifications, screen-capture consent forced to "entire screen" on Android 14+) and starting/stopping the services |
| `ui/AppScreen.kt` | Compose UI: setup (music-control access, server address), Share / Test / Join, live session screen (room code, listeners, connection, upload kbps, audio level meter, capture verdict, now playing, ⏮ Play/Pause ⏭, Stop) |
| `Session.kt` | Shared state (StateFlows) between the services and the UI |
| `capture/CaptureService.kt` | Host / test foreground service: MediaProjection → AudioRecord playback capture → AAC → relay. Runs friends' commands. **CaptureVerdict** detects "app is playing but nothing is captured", which means that app blocks capture. |
| `audio/AacEncoder.kt` | PCM → AAC-LC via MediaCodec |
| `audio/StreamPlayer.kt` | Listener: jitter buffer → AAC decoder → AudioTrack, with drop-to-catch-up and underrun handling |
| `media/MediaHub.kt` | Finds the app that is playing, reads title/artist/app name, sends play/pause/next/prev |
| `media/MediaNotificationListener.kt` | Empty service. Exists only so the user can grant "notification access", which Android requires before one app can control another app's media. |
| `listen/ListenerService.kt` | Friend's foreground service: join room, play stream, send commands, handle host away/back/ended |
| `net/RelayClient.kt` | OkHttp WebSocket with auto-reconnect and send-queue cap |
| `net/Protocol.kt` | Message builders and parsers (matches the server) |
| `util/Notifications.kt`, `util/Locks.kt` | Foreground notification with a Stop action; wake lock and Wi-Fi lock |

**Build status (2026-11-12):** ✅ `gradlew assembleDebug` succeeds with no compile errors. ✅ Android Lint reports 0 errors (only warnings about newer library versions and the backup attribute). The debug APK is at `app/build/outputs/apk/debug/app-debug.apk` (about 23 MB). ⏳ Not yet run on a real phone.

Build toolchain installed on this PC, with no admin rights needed:
- JDK 17 (Temurin): `%USERPROFILE%\.jdks\jdk-17.0.21+9`
- Android SDK (platform 35, build-tools 35, platform-tools/adb): `%LOCALAPPDATA%\Android\Sdk`. This is the same location Android Studio uses, so it will pick it up automatically.
- `local.properties` points Gradle at the SDK.

## 6. How to run

### A. Relay server
Local test (both phones on the same Wi-Fi as the PC):
```
cd server
npm install
npm start          # listens on :8080
npm test           # automated relay tests
```
In the app, set the server address to `ws://<PC's Wi-Fi IP>:8080` (for example `ws://192.168.1.5:8080`). You may need to allow Node through Windows Firewall.

**Deployed:** the relay runs on Railway at **`wss://musicpartner-production.up.railway.app`** (health check: https://musicpartner-production.up.railway.app/ returns `MusicPartner relay OK`). It deploys from `server/` on the `main` branch using `server/railway.json`. Verified on 2026-11-13: the host created a room, a listener joined, the listener's command reached the host, and audio frames reached the listener. The app uses this address by default.

**For friends in different places**, the server needs a public address. Any of these work:
- **Render / Railway / Fly.io:** deploy the `server/` folder with start command `npm start`. The platform sets `PORT`. Use the `wss://…` URL in the app. (Free tiers may sleep when idle, so the first connection can take about 30 s.)
- **Quick test from your PC:** `cloudflared tunnel --url http://localhost:8080` gives a public `https://….trycloudflare.com`. Use it as `wss://….trycloudflare.com` in the app.

### B. Android app
Build from the command line (PowerShell, in the project folder):
```
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\jdk-17.0.21+9"
.\gradlew.bat assembleLiteDebug assembleFullDebug
```
This produces `app/build/outputs/apk/lite/debug/app-lite-debug.apk` and `app/build/outputs/apk/full/debug/app-full-debug.apk`. Ready-to-share copies are in `dist/` (`MusicPartner-Lite.apk`, `MusicPartner-Full.apk`).

Install:
- **Lite (anyone, no cable):** send `dist/MusicPartner-Lite.apk` through WhatsApp or Drive, open it, allow "install unknown apps", and tap "Install anyway" if Play Protect warns.
- **Full (USB):** turn on Developer options → USB debugging, plug in, then run `& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r dist\MusicPartner-Full.apk`

Android Studio can also open this folder directly if you prefer an IDE.

### C. First test (step 1)
1. Open MusicPartner and tap **Turn on** next to music control. Enable MusicPartner in the list.
2. Tap **Test capture only (no internet)** and accept the record-audio and screen-capture prompts.
3. Play a song in YouTube Music, then switch back. The bar should move and show "✓ Capturing audio from YouTube Music".
4. Repeat with Spotify, YouTube and Chrome. If the app shows "⚠ … is playing but no sound is reaching MusicPartner", that app blocks capture.
5. Record the results in the table in section 7.

### D. Real session (steps 3 + 4)
1. Host: the server address is pre-filled with the Railway relay. Tap **Start sharing**, and send the room code to your friend.
2. Friend: keep the same server address, enter the code, and tap **Join**.
3. Play music on the host phone. Try Play/Pause/⏭ from **both** phones, and try with the screen off.

## 7. Known limitations / risks

1. **Some apps block capture.** Android lets apps opt out. Spotify is widely reported to do this. YouTube, YouTube Music and Chrome are generally reported to allow it. *Verify with test mode:*

   | App | Capture works? | Controls work? |
   |---|---|---|
   | YouTube Music | ? | ? |
   | Spotify | ? | ? |
   | YouTube | ? | ? |
   | Chrome (web player) | ? | ? |

2. **The friend hears it slightly later** than the host, about 0.2–0.6 s depending on the network. Over a distance nobody can notice, because there is nothing to compare against. Step 5 would make the moment match exactly, if needed.
3. **Capture sees all media audio** on the host phone. A game or video playing in another app is shared too. Notification sounds and calls are not.
4. **Data use:** about 58 MB per hour on both phones at 128 kbps.
5. **Battery optimisation:** some brands (Xiaomi, Oppo, Vivo, Samsung) aggressively kill background apps. Users may need to set MusicPartner to "Unrestricted" battery use.
6. **Security:** room codes are random but there is no account or login. Anyone with the code can listen. That's fine for friends; for wider release, add room passwords or auth.
7. **Google Play:** notification-access and screen-capture permissions need a clear explanation in the store listing. Personal sideloading is unaffected.

## 8. Roadmap details

- **Step 5, host delay compensation:** the host would hear the music through MusicPartner, delayed to match the friend, instead of directly. Needs a check whether the original app's audio can be muted while capture continues. Clock sync (NTP-style ping exchange over the relay) would let each listener report its playout delay.
- **Step 6, WebRTC + Opus:** a peer-to-peer UDP path with a TURN fallback. Expected delay drops from about 300 ms to about 100–150 ms and data use to about 30 MB per hour. The relay server becomes the signalling server.
- **Step 7, hotspot/LAN mode:** the host runs the relay inside the app, and the friend connects to the host's hotspot IP. No internet needed. The protocol stays the same.
- **Step 8, polish:** album art in "now playing", seek bar/position, proper launcher icon, onboarding screens, a "share code" button (WhatsApp, etc.), QR code join.
