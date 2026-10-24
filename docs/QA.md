# Browser smoke test

Build the release with `./scripts/build.sh`, then run:

```bash
python3 scripts/qa-server.py
```

Open **http://127.0.0.1:8766** in the desktop browser. The developer server serves the exact files in `dist/web/`.

Verify the title screen, start a run, move in every direction, aim and fire, ricochet a projectile, dash, pause/resume, toggle audio, choose an upgrade, and restart after a run. Check fullscreen and resizing, and inspect the browser console for errors.

For captured browser screenshots, POST raw JPEG bytes to `/__qa/screenshot?name=title.jpg` or `/__qa/screenshot?name=gameplay.jpg` on the same local origin. The server saves them under `docs/screenshots/`. It accepts safe `.jpg` basenames and uploads up to 5 MB.

This endpoint is available only in the local QA server. It is not included in the browser release archive.

## Results

Record the browser, date, checks, and screenshot paths here after the smoke test.

## Release verification — 2026-10-24

- Java engine: 70 regression assertions passed.
- Balance check: five deterministic bot playthroughs completed all eight waves and the final boss in 160–163 seconds of active combat.
- Browser: Java entry point loaded without console errors, title and gameplay rendered, held fire produced seven shots, movement changed player X from560 to265, dash started its cooldown, and pause stopped the game.
- Screenshots: [title](screenshots/title.jpg) and [gameplay](screenshots/gameplay.jpg).
- Packaging: root index.html and game.js, no external runtime requests, project and TeaVM licenses included.
