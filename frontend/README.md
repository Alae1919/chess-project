# REXCHESS web app

The Angular 17 front end of REXCHESS: standalone components, lazy-loaded routes, NgRx for state, and Three.js for the
3D board. It talks to the Spring Boot backend in [`../chess-engine`](../chess-engine) over REST and two WebSockets. The
interface is in French.

For the whole project start at the [root README](../README.md). How the app is structured:
[docs/architecture.md](../docs/architecture.md#the-web-app).

## Run it

```bash
npm ci
npm start          # http://localhost:4200
```

The backend must be running on port 8080. `ng serve` forwards `/api` and `/ws` using [`proxy.conf.json`](proxy.conf.json),
which points at `backend:8080`, the name Docker gives the backend in `docker-compose.dev.yml`. Outside Docker, use a proxy
file that targets `localhost:8080`: see [docs/development.md](../docs/development.md#running-the-parts-yourself).

## Scripts

| | |
|---|---|
| `npm start` | Dev server with live reload |
| `npm run build -- --configuration production` | Production build into `dist/rexchess`. Also type-checks the templates |
| `npm test` | Specs in watch mode (Karma, Jasmine) |
| `npm run test:ci` | Specs once, in headless Chrome |

The production build fails if the initial bundle exceeds 1 MB or a component's stylesheet exceeds 20 kB.

Before `start`, `build` and the tests, `npm run assets:hash` writes `src/app/core/assets/asset-hashes.generated.ts`
(a content hash of every file under `src/assets`; not committed). After `build`, `scripts/precompress.mjs` writes a
`.gz` next to each text file and model of the build, which nginx serves as it is. Running `ng` directly skips the
first step: if the build cannot find `asset-hashes.generated`, run `npm run assets:hash`.

## Assets and caching

Browsers keep anything whose URL names one exact version for a year, and check everything else on every load
([nginx.conf](nginx.conf)). So:

* **From code**, load a file under `src/assets` through `assetUrl('assets/...')` (`core/assets/asset-url.ts`), which
  adds its content hash (`?v=...`). A photo in two widths: `stillSources(stem, [small, large])`.
* **From a stylesheet**, use a relative `url('../../assets/...')`: the build copies the file to `media/` with a hash in
  its name. An absolute `/assets/...` URL would not be versioned.
* **Fonts** are in `src/assets/fonts` and declared in `src/styles.scss`, the same way.

## Routes

| Path | Page | Sign-in needed |
|---|---|---|
| `/` | Landing | no |
| `/login`, `/register` | Sign in, create an account | no |
| `/home` | Set up a game, resume a saved one (`?mode=saved`) | no |
| `/game`, `/game/:id` | A game | yes |
| `/online` | Matchmaking and invitations | yes |
| `/account` | Profile, statistics, achievements, preferences | yes |

## Layout of `src/app`

```
core/       models, services (REST, game socket, lobby socket), guard, interceptors, pure helpers in utils/
store/      NgRx: game, account, lobby, and session (clears the store when the user logs out)
shared/     boards (2D and 3D), promotion picker, game-over dialog, navbar, tab bar, board styles
features/   landing, auth, home, game, online, account
```

## The landing page

`/` is a film in five acts, driven by the scroll: a tall track with a sticky stage, and one GSAP timeline scrubbed by
ScrollTrigger (`features/landing/pages/landing.page.ts`). The scroll container is the app shell's `.app-main`, not the
window. Acts I and V are a Three.js scene of a lone king under a spotlight (`scenes/king-stage.ts`), built from the
same pieces and marble as the game's board; acts II and III are footage stills; act IV is the game's own 3D board
(`LuxeBoardScene`, Ebony & Ivory style) with the engine's lines drawn on it.

* **Real figures only.** Every number, move and line on the page comes from `GET /api/showcase`, the engine's search
  of the story's position (Légal's trap). While it loads the page says so; if it fails, no figures are shown.
* **Footage.** The stills are in `src/assets/landing` (WebP, 800 and 1600 px). To use video clips, add them there and
  name them in `landing-media.ts`; they play while their shot is on screen, with the still as poster.
* **Lighter versions.** Phones and data-saver browsers get stills of the king scene instead of live 3D, and no video.
  With `prefers-reduced-motion`, each act is shown at rest and switched in place as you scroll; nothing animates.
  Without WebGL, act IV falls back to the flat board.

## The game's 3D view (the "study")

In 3D mode the game page is a room: a photo of a table behind a transparent WebGL canvas, the board lying flat on it,
and the panels floating over the photo as glass. The scene (`shared/three/luxe-board-scene.ts`, option
`environment: 'study'`) draws only the board, the pieces and their shadows, lit by a warm key light and a lamp, so the
photo does the rest. The camera is fitted to the free space between the panels and the dock (the game page measures them
and passes a `safeArea` to `chess-board-3d`), so the board is as large as it can be without sliding under anything.
The flat 2D board and the top view keep the plain dark background.

* **The background.** `src/assets/game/study-2000.webp` (wide screens) and `study-1000.webp` (under 1000 px), set as
  `--plate` in `game.page.scss`. To change the room, replace both files, keeping the names, with a photo of a table
  seen from about 30 degrees above, light from the left, the middle of the picture empty (that is where the board goes).
  Every piece is then stood upright on its square, its base centred, and the whole set scaled by one factor so the king is
  1.4 squares tall (a base never exceeds 0.38 of a square), so the file's units and origin do not matter. Knights are
  turned to face the opponent: if yours look the wrong way, rotate the model by 180 degrees about Y in the file.
* **Compressed files.** Draco and Meshopt are decoded, and the Draco decoder is copied to `assets/draco` by the build.
  Both need WebAssembly and a worker, which the production CSP (`nginx-snippets/security-headers.conf`) does not allow:
  to serve a compressed set, add `'wasm-unsafe-eval'` to `script-src` and a `worker-src 'self' blob:`. Uncompressed
  files need nothing.
* **The wood.** The study is tuned for the `classic-wood` board style (the backend only accepts `marble-gold`,
  `classic-wood` and `ebony-ivory`, so no new style was added).
* **Loading.** Left alone, three compiles each shader the first time something using it is drawn, one after the
  other, and the page stands still for all of it (about 2.5 s for the study on a Windows/ANGLE GPU). So the scene
  compiles every shader at once and without blocking (`compile()`), draws nothing until they are ready, and keeps a
  hidden pawn in each style's materials (`keepLooksWarm`): a shader program is deleted when the last material using it
  goes, so without them every style change compiled its programs again. A style change builds the new look before it
  frees the old one for the same reason. What is left is painting a style's textures the first time it is shown (up to
  about 0.8 s on that machine, once per style and visit).

### The desk view (wide screens)

The study's photo can only be right for one camera, so on wide screens the page asks for `environment: 'desk'`
instead: the scene draws the desk and the room itself, and the player can move the camera around the board. Phones keep
the photo and a fixed camera.

* **The desk** is a real surface (`shared/three/desk-environment.ts`): a plane of walnut at the height of the board's
  underside, with a varnish sheen, taking the shadows of the lights. Its far edge sinks into a soft shadow, and the room
  shows beyond it.
* **The room** is a 360 degree picture (`tools/desk-assets/room-pano.webp`), made ahead of time into three's blurred
  reflections (PMREM) by `tools/desk-assets/bake-room-light.mjs`: the game loads the result (`assets/desk/room-light.webp`)
  and uses it both as the background (slightly blurred, like the background of a photograph) and as the light and
  reflections on the board, with no work on the graphics card beyond one copy. Run the script again after changing the
  panorama. Far enough away not to need parallax. Nothing is drawn until the room and the wood have arrived (8 s at most),
  so the page's own photo stays visible until then; until they have, the materials are compiled against a plain stand-in
  reflection map of the same height, so the real one swaps in without compiling everything again.
* **The camera** (`shared/three/desk-camera.ts`, plain numbers, specs in `desk-camera.spec.ts`) goes round the board: turn
  15 degrees either way, rise from 25 to 69 degrees (more of the same slides into the top-down view), zoom 0.85 to 1.15,
  shift 1.6 squares sideways and 1 forward. Everything is clamped there, because beyond it the room behind the desk would
  show its edges. Drag on the bare desk (or the board) to turn and rise, drag with the right button or Shift to shift, wheel or
  pinch to zoom, Ctrl + arrows to turn (plus Shift to shift), double-click the desk to come back. Pressing a piece of the
  player's still picks it up instead. The "Vue" button in the dock holds the named views. Playing Black turns the board on the
  desk, not the camera, so the room stays where it is.
* **The board's wood.** On the desk the `classic-wood` style wears photographs instead of painted wood (`assets/desk/wood-*.webp`:
  maple squares, rosewood squares, a dark walnut frame, graded towards the reference's colours; `board-looks.ts` holds
  the list, so another style can be given photographs the same way). They come in with the room and the board is dressed
  in them once (`dress()`), before the first frame. Each square shows its own 512 px piece of the plank, the grain
  always running down the files. The gold lettering lies on the frame and turns against the board, so it reads the
  same way for Black. No style was added: the backend only accepts the three it knows.
* **The props** (`shared/three/desk-props.ts`): a brass table lamp on the left, a stack of old books on the right and a
  black mug on its coaster near the player, as in the reference, from `.glb` files in `assets/desk/props/`. Each is
  stood on its base, sized to the desk (1 unit is about 4.5 cm) and placed by `DESK_PROPS`; a file that does not load
  is left out. Where something sticks out and would pull the middle off the base, `axis` names the band of the height
  to centre on: the lamp's cord lies out along the desk (centred on the part above it, and turned so the cord runs off
  to the left, not across the board), the mug's handle (centred on its coaster). The lamp also gets a flame under its
  shade (`flame`, a fraction of its height) and becomes the
  room's warm point light. Glass is drawn without refraction (`transmission` is cleared): three then draws the whole
  scene a second time every frame, which cost the first lamp's glass two thirds of the frame rate for a picture that
  looks the same. The books are from Sketchfab under CC BY 4.0: their credit is in `assets/desk/props/CREDITS.md` and
  must stay with them (there is no credits page in the app yet). To add a model, shrink it first with
  `tools/desk-assets/optimize-props.sh` (textures to 1024 px as WebP, no Draco or Meshopt).
* **The pictures** are made by `tools/desk-assets/prepare.py` from the raw images in `design/` (the panorama is turned so
  the man sits left of centre, lowered 10 degrees and lifted a little; the desk wood is flattened and made to tile). To
  change the room or the wood, replace the raw file and run it, then `node tools/desk-assets/bake-room-light.mjs` for the
  room; nothing needs them at run time.
* **Looking at it:** `node tools/demo-video/desk-shots.mjs` (Docker dev stack up) photographs the first view, the
  named views, the mouse and wheel, a piece dragged across the board and the menu, into `tools/demo-video/out/desk/`.

## In production

The [`Dockerfile`](Dockerfile) builds the app and serves it with nginx ([`nginx.conf`](nginx.conf)), which also proxies `/api`
and `/ws` to the backend and sets the security headers ([`nginx-snippets/`](nginx-snippets)). The Content-Security-Policy
allows no inline scripts, which is why `inlineCritical` is off in `angular.json`: Angular's critical-CSS loader is an
inline handler.
