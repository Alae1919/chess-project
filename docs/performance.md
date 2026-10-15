# Performance

The latency targets, how they are measured, and the numbers before and after each change. The changes themselves
follow a plan in phases: measuring first (this page and [monitoring.md](monitoring.md)), then caching and delivery,
page loads, the moves, and the backend.

## Targets

| What | Target | Where to read it |
|---|---|---|
| Landing page, Largest Contentful Paint, p75 | under 2.0 s on phones, 1.5 s on desktops | `rum_vital_milliseconds{name="LCP"}` |
| Interaction to Next Paint, p75 | under 200 ms | `rum_vital_milliseconds{name="INP"}` |
| 3D board, first frame on a desktop | under 2.5 s cold, 0.6 s warm | `rum_timing_milliseconds{name="game.firstFrame"}` |
| Selecting a piece, to its moves showing | no request, under one frame | `rum_timing_milliseconds{name="legal.select"}` |
| `POST /games/{id}/moves`, server, p95 | under 50 ms | `http_server_requests_seconds` |
| The same, as the player sees it, p95 | under 200 ms (players near the server) | `rum_timing_milliseconds{name="move.roundtrip"}` |
| `POST /games/{id}/ai-move`, levels 1–5, server, p95 | under 100 ms (the page still waits 600 ms before showing it) | `chess_ai_search_seconds` |
| A repeat visit, bytes downloaded (API aside) | under 100 KB | DevTools, Network, second visit |

## Measuring

**The API**, from wherever the script runs (on the server, it is close to the backend's own times; from home, it
includes the network, like a player's):

```bash
node tools/load/move-latency.mjs --base http://localhost:8080 --level 1 --moves 40
```

It registers a throwaway account, plays games against the AI with random moves, and prints p50 / p95 / max of the
move, the AI's reply and the legal-moves call, as a table to paste below.

**Page loads**: Lighthouse in Chrome DevTools (or `npx lighthouse <url> --preset=desktop`), on `/`, `/login` and a game,
with "mobile" and "desktop". Then DevTools → Network on a first visit and on a second one, with "Disable cache" off:
the transferred total.

**In production**: the [dashboard](monitoring.md), over a day at least: browser timings vary much more than a lab run.

## Guardrails

What keeps these numbers from sliding back, in CI ([`ci.yml`](../.github/workflows/ci.yml)) and after each deploy:

| Check | Fails on | Where |
|---|---|---|
| Angular's initial-bundle budget | warning past 450 kB, error past 550 kB (408 kB today) | `frontend/angular.json` |
| Size budget of every other file | a lazy chunk over 680 kB, a model over 1.5 MB, a picture over 1 MB, a clip over 5 MB, a font over 60 kB, the build over 22 MB | `frontend/size-budget.json`, `npm run check:sizes` |
| Lighthouse, mobile, on `/` and `/login` | nothing yet: LCP over 2.5 s, TBT over 300 ms, CLS over 0.1 or more than 2.5 MB are warnings, and the reports are kept as a CI artifact | `frontend/lighthouserc.json` |
| Delivery of the live site | a cache or compression header that is not what nginx.conf and the Caddyfile intend | `tools/ci/check-delivery.sh`, after `deploy.yml` |

A budget is raised on purpose only, saying why in the commit. Once a few weeks of Lighthouse reports show what a CI
machine measures, the warnings can become errors.

## Results

Record the date, the commit, where it ran and the numbers. Leave earlier rows: they are the history.

### Baseline (before any change)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | `1d606f2` + monitoring | MacBook, backend via `mvn spring-boot:run`, dev Postgres | `move-latency.mjs`, level 1, 19 moves | `/moves` p50 39 ms, p95 92 ms · `/ai-move` p50 624 ms, p95 645 ms (the 600 ms minimum) · `/legal-moves` p50 27 ms, p95 107 ms |
| 2026-10-15 | `1d606f2` + monitoring | Same, app on `ng serve` (development build), desktop, one game | browser timings | 3D first frame 10.4 s cold · desk assets 5.9 s · game start 1.5 s · piece selection 89–121 ms · move round trip 112 ms · AI reply 659 ms |
| 2026-10-15 | `1d606f2` + monitoring | `ng build --configuration production` | bundle sizes | initial 465 kB raw, 133 kB transferred; three.js chunk 620 kB raw, 137 kB transferred |

### After phase 2 (caching and delivery)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 2 | prod stack on a laptop (Caddy, nginx 1.28), `/login` | second visit | 19 files, 0 bytes downloaded (all from the browser's cache); 300 bytes for the `index.html` check. Before: every file under `/assets` revalidated, and downloaded again after each deploy |
| 2026-10-15 | phase 2 | same | compression | build `.gz` files: 5.9 MB → 3.5 MB (lamp.glb 1.37 MB → 0.97 MB); API JSON zstd |
| 2026-10-15 | phase 2 | same | fonts | 3 variable-font files from the site (Jost 27 kB, Cormorant 38 kB + 39 kB italic) instead of a Google Fonts stylesheet and its files from two other origins |

### After phase 3 (page loads)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 3 | `ng build --configuration production` | bundle sizes | initial 465 → 408 kB raw (133 → 119 kB transferred): no animations package. three.js (620 kB) leaves the game page's and the account page's chunks: it loads with the 3D board only |
| 2026-10-15 | phase 3 | `ng serve`, desktop, landing | first load | stills and act I's clip only; act III's clip once act II is passed, three.js and the 6 piece models approaching act IV. Before: all of it, 7.9 MB of video included, at once |
| 2026-10-15 | phase 3 | `ng serve`, desktop, desk view | 3D first frame | ~2.4–3 s for the first board of a page load, after the home page prefetched the files (was 10.4 s); ~1.6–2 s for the next board, with no request at all (desk files 1159 → 91 ms; what is left is room lighting ~0.3 s and shader compilation ~0.6 s on the GPU) |

### Phase 3, continued: the 3D board's next games

`node tools/demo-video/board-timings.mjs 4`: four desk-view games in one page load, the home page in between, in Chrome
with its window shown. Each figure is the range over two runs.

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 3 | `ng serve`, MacBook Pro, i5-7360U, Intel Iris Plus 640 (ANGLE on Metal), Chrome | where a later game's time goes, before | 1.9–2.5 s to the first frame. ~1.6 s is the first draw uploading every picture to the new WebGL context: the browser decodes an `<img>` again each time, 0.3–0.7 s for each 2048 px wood. Room lighting ~0.5 s: three's PMREM ran three times per game (the stand-in, the room's light, and again for the blurred background). Compiling the 13 shader programs ~0.3 s. `compile()` already waited on the driver as `compileAsync` does, so changing to it gains nothing |
| 2026-10-15 | phase 3 | same | 3D first frame, desk view | later games 1.9–2.5 → **1.0–1.2 s**; the first game of a page load 4.1–4.3 → 3.5–3.6 s. `desk: props placed, room lighting` 300–380 → ~100 ms. Pictures are decoded once into bitmaps (uploads ~1.6 → ~0.8 s); the room's light is baked into `room-light.webp` and copied to the GPU in one draw, and is the background too (no PMREM at all) |
| 2026-10-15 | phase 3 | same | the picture | screenshots of the desk view before and after: 0.9% of pixels more than 3 levels apart (of 255), 0.05% more than 10 (two runs of the old code: 0.04% and 0.04%, the player's name); the differences are WebP noise in the blurred room. The baked file is 574 KB, in place of the 487 KB panorama (lossless would be 2.1 MB, and the same as before to the pixel) |
| 2026-10-15 | phase 3 | same | still over the 0.6 s target | what a later game still pays is uploading the pictures (~0.8 s here: the woods, the desk, the props) and compiling (~0.3 s). Only keeping them on the GPU between games would remove it, which means keeping the WebGL context, the textures and the materials alive (an estimated 150 MB of GPU memory while the player is elsewhere) |

### After phase 4 (moves)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 4 | MacBook, `mvn spring-boot:run`, `move-latency.mjs`, level 1 | API | `/ai-move` p50 624 → 40 ms, p95 645 → 81 ms (the 600 ms pause is now the page's, with no server thread asleep) · `/moves` p50 35 ms |
| 2026-10-15 | phase 4 | `ng serve`, 2D and 3D boards | interaction | selecting a piece: no request (was one round trip per piece, 89–121 ms); the move shows on the board at once and is taken back if refused; a new game is not fetched a second time after it is created |

### After phase 5 (backend)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 5 | MacBook (heavily loaded: load average ~300), `mvn spring-boot:run`, `move-latency.mjs`, level 1, 2 games | API | `/moves` p50 35 → 21 ms · `/ai-move` p50 40 → 26 ms · `/legal-moves` p50 18 → 5 ms (no user or game row read to authorise it) |
| 2026-10-15 | phase 5 | same | auth cache | 148 hits for 1 miss: one user read per minute instead of two or three queries per request |
| 2026-10-15 | phase 5 | `MovePersistenceCostTest` | saving a move | 4 statements, the same at move 20 as at move 2 (it loaded every earlier move before) |
| 2026-10-15 | phase 5 | dev database, `EXPLAIN` | indexes | match history uses `idx_games_*_status_updated`; name search can use `idx_users_username_lower` (the table is too small for the planner to bother yet) |

Production numbers come once the dashboard has a day of data.
