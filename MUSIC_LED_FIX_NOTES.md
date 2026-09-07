# Music LED – XVV Fix Notes

## What was fixed

1. MediaProjection consent is session-scoped. The app never stores or reconstructs the returned consent Intent. A fresh consent result is passed from `MusicLedScreen` to `MusicLedService` for each new capture session.
2. The service uses `START_NOT_STICKY`; it will not resurrect itself without a fresh MediaProjection grant.
3. The Music LED service exposes an in-process active-session state so leaving/re-entering the Music LED screen does not create a fake "stopped" state while the service is actually alive.
4. The previous asynchronous 180 ms LED smoke test was removed. It could turn the LED off after a successful start and race with the real beat trigger.
5. Captured audio no longer has to pass the beat detector before the LED becomes active. Audible playback keeps the LED master enabled; beat detection changes effect/frequency.
6. AudioPlaybackCapture uses `USAGE_MEDIA` and `USAGE_GAME`, with sample-rate fallback 48 kHz → 44.1 kHz → 32 kHz → 16 kHz.
7. AudioRecord initialization, recording state, read failures and audio level are logged.
8. LED sysfs failures are logged separately from capture failures.
9. The service turns the LED off after sustained silence and when the capture session ends.

## Important Android behavior

Android 14+ requires user consent for every MediaProjection capture session. A cached consent Intent must not be reused. The service therefore cannot silently recreate a dead projection session. This is an Android platform rule, not an application limitation invented by the UI.

Playback capture also depends on the source app's capture policy. Some audio sources can opt out of playback capture; the LED app cannot bypass that policy.

## Useful log markers

- `CAPTURE_READY` – AudioRecord was initialized and started.
- `AUDIO_LEVEL` – RMS-derived playback level seen by AudioRecord.
- `AUDIO_ACTIVE led=ON` – captured audio is above silence threshold.
- `BEAT_TRIGGER` – adaptive beat detector fired and LED effect was updated.
- `LED_CAPTURE_PATH_READY` – initial LED configuration was written.
- `LED_MASTER_WRITE_FAILED` – sysfs master enable/disable did not succeed.
- `CAPTURE_SECURITY_FAILURE` / `CAPTURE_STATE_FAILURE` – capture/session failure.

A healthy session should look approximately like:

```
CAPTURE_READY ...
LED_CAPTURE_PATH_READY ...
AUDIO_LEVEL db=-...
AUDIO_ACTIVE led=ON
BEAT_TRIGGER effect=... freq=...
```

If `AUDIO_LEVEL` stays around `-100 dB` while music is definitely playing, the problem is playback capture/source policy rather than the beat algorithm. If audio levels are normal and `AUDIO_ACTIVE` appears but `LED_MASTER_WRITE_FAILED` also appears, focus on the AWINIC sysfs/permissions path.
