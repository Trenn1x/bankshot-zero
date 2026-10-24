# Browser smoke test

Build the release with `./scripts/build.sh`, then run:

```bash
python3 scripts/qa-server.py
```

Open **http://127.0.0.1:8766** in the desktop browser. The developer server serves the exact files in `dist/web/`.

Verify the title screen, start a run, move in every direction, aim and fire, ricochet a projectile, dash, pause/resume, toggle audio, choose an upgrade, and restart after a run. Check fullscreen and resizing, and inspect the browser console for errors.

For captured browser screenshots, POST raw JPEG bytes to `/__qa/screenshot?name=title.jpg` or `/__qa/screenshot?name=gameplay.jpg` on the same local origin. The server saves them under `docs/screenshots/`. It accepts safe `.jpg` basenames and uploads up to 5 MB.

This endpoint is available only in the local QA server. It is not included in the browser release archive.

## Mobile test plan — v1.1.0

Run these checks before recording mobile results. Test portrait and landscape viewports, including a narrow phone display, and record the browser and viewport dimensions. Emulated touch checks should be identified as emulated; record any physical-device checks separately.

- Start and restart using only touch. Confirm no keyboard is required.
- Hold the left stick to move, and hold the right stick to aim and fire. Use both sticks together and verify releasing each pointer stops its own action.
- Move, fire, and tap DASH simultaneously. Check cooldown feedback and repeated use after the cooldown.
- Aim at arena edges and verify a projectile ricochets. Confirm portrait rotation keeps movement and aim aligned with the visible arena.
- Pause and resume with the screen button; toggle sound with the screen button. Background and return to the game, checking for stuck movement or fire.
- Tap an upgrade card between waves and confirm play resumes with the selected upgrade.
- Rotate or resize during play. Confirm the full arena and controls fit the viewport and remain reachable, including browser safe areas.
- Confirm gameplay gestures do not scroll, select text, or zoom the page. Check fullscreen where supported.
- Repeat a desktop mouse/keyboard smoke test and inspect the browser console for errors.
- Verify the published itch.io embed with **Mobile friendly** enabled, and confirm the published GitHub Pages build matches the release archive.

## Results

Record the browser, date, checks, and screenshot paths here after the smoke test.

## Release verification — 2026-10-24

- Java engine: 70 regression assertions passed.
- Balance check: five deterministic bot playthroughs completed all eight waves and the final boss in 160–163 seconds of active combat.
- Browser: Java entry point loaded without console errors, title and gameplay rendered, held fire produced seven shots, movement changed player X from560 to265, dash started its cooldown, and pause stopped the game.
- Screenshots: [title](screenshots/title.jpg) and [gameplay](screenshots/gameplay.jpg).
- Packaging: root index.html and game.js, no external runtime requests, project and TeaVM licenses included.

## Published release

- [GitHub Pages](https://trenn1x.github.io/bankshot-zero/) serves index.html, game.js, and icon.svg byte-for-byte identical to the tested build.
- [GitHub v1.0.0](https://github.com/Trenn1x/bankshot-zero/releases/tag/v1.0.0) includes the browser ZIP; build/release and Pages workflows passed.
- [Itch.io](https://trenn1x.itch.io/bankshot-zero) is public, its version API reports1.0.0, and Butler build2199633 is complete.
- Live itch embed loaded Java game, started a run, accepted dash input, and paused successfully.
- [Published game screenshot](screenshots/itch-published.jpg).

## v1.1.0 mobile verification — 2026-10-24

- Java build: EngineTest 70 assertions and TouchStickTest 80 assertions passed; TeaVM compilation and browser ZIP packaging passed.
- Browser viewports: 320×568 and 390×844 portrait phones, 667×375 and 844×390 landscape phones, 768×1024 portrait tablet, and 1280×720 desktop. No horizontal page overflow. All three upgrade cards fit the smallest portrait viewport.
- Browser input integration: independent simultaneous move/fire, pad ownership, third-contact Dash, aim cancellation, movement release, retained aim, pause cleanup, resume without stuck fire, resize cleanup, and orientation cleanup passed.
- Trusted pointer test confirmed movement/pointer capture and release. Desktop mouse fire, keyboard movement, dash, and pause still work.
- Starting muted and then tapping Sound On creates a running AudioContext in the tap handler.
- Screenshots: [portrait](screenshots/mobile-portrait.jpg), [landscape](screenshots/mobile-landscape.jpg).
- These checks used Chromium with mobile viewports, browser PointerEvent integration, and trusted pointer input. Physical iOS/Android hardware has not been tested here.
