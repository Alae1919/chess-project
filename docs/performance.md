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

### After phase 4 (moves)

| Date | Commit | Where | Measure | Result |
|---|---|---|---|---|
| 2026-10-15 | phase 4 | MacBook, `mvn spring-boot:run`, `move-latency.mjs`, level 1 | API | `/ai-move` p50 624 → 40 ms, p95 645 → 81 ms (the 600 ms pause is now the page's, with no server thread asleep) · `/moves` p50 35 ms |
| 2026-10-15 | phase 4 | `ng serve`, 2D and 3D boards | interaction | selecting a piece: no request (was one round trip per piece, 89–121 ms); the move shows on the board at once and is taken back if refused; a new game is not fetched a second time after it is created |

Production numbers come once the dashboard has a day of data.
