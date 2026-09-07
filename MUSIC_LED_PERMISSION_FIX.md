# Music LED Permission Model

The old implementation documented persisting `MediaProjection` result data in Proto DataStore. That approach is intentionally no longer used.

For apps targeting Android 14+, each MediaProjection capture session requires fresh user consent. The returned consent data must not be cached and reused for another capture session. The current implementation keeps the projection token only in memory for the lifetime of the active `MusicLedService` session.

Leaving and re-entering the Music LED settings screen does **not** invalidate an active service session. If the session really ends, the user must explicitly start a new capture session and approve the system consent dialog again.

The app still persists normal Music LED settings such as:

- enabled state
- random effects
- selected effects
- dynamic/own colors
- sensitivity
- LED frequency range

It does not persist MediaProjection consent data.
