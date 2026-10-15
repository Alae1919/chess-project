# Monitoring

How fast the app is, for players and on the server, and an alert when it slows down. Everything ends up in
[Grafana Cloud](https://grafana.com/products/cloud/)'s free tier; the production server only runs one more small
container, [Grafana Alloy](https://grafana.com/docs/alloy/latest/), which sends it there.

```
 browser ── web vitals, move timings ──► POST /api/rum ─┐
                                                        ▼
 Caddy ─► nginx ─► Spring Boot ── /actuator/prometheus ─┴─► Alloy ──► Grafana Cloud
   │        │          │                                     ▲        (metrics, logs,
   └────────┴──────────┴────── container logs ───────────────┘         dashboard, alerts)
 host CPU, memory, disk ─────────────────────────────────────┘
 Postgres ───────────────────────────────────────────────────┘
```

## What is measured

### In the browser (`frontend/src/app/core/telemetry/telemetry.service.ts`)

Sent in small batches to `POST /api/rum` when the page is hidden, and every 30 s. Nothing identifies the player: a
name, a number in ms, the route's template (`/game/:id`, never a game's id) and whether it was a phone.

| Name | What |
|---|---|
| `LCP`, `INP`, `CLS`, `TTFB`, `FCP` | [Web Vitals](https://web.dev/articles/vitals), per route |
| `move.roundtrip` | The player's move, from sending it to the server's answer |
| `ai.reply` | Asking the AI to move, to the server's answer (the page then waits until 600 ms have passed before showing the move) |
| `legal.select` | Selecting a piece, to its legal moves arriving. Only for a game that came without its list of legal moves: otherwise there is no request |
| `game.start` | "Start a game", to the game page being shown |
| `game.firstFrame` | The 3D board's first frame. `cold` for the first board since the page loaded, `warm` after |
| `desk.assets` | The desk's room, wood and props, from asking for them to all being in |
| `ws.reconnect` | How long a dropped socket stayed down (`game` or `lobby`) |

The server keeps only the names, routes and variants it knows (`RumRecorder`), so the number of series stays fixed.
A new timing goes on both lists.

### On the server (`/actuator/prometheus`)

| Metric | What |
|---|---|
| `http_server_requests_seconds` | Every API call, by endpoint template, method and status |
| `chess_ai_search_seconds` | Time to choose an AI move, by `level` and `source` (`book` or `search`) |
| `chess_ai_searches_active` | AI searches running now |
| `chess_move_persist_seconds` | Saving one move (inside the game's turn lock: it delays the answer) |
| `chess_analysis_seconds` | An evaluation, by `outcome` (`ok`, `cached`, `busy`, `error`) |
| `chess_engine_wait_seconds` | Time a search waited for a free core (one search per core, `EngineCpuGate`), by `kind` (`ai`, `analysis`) |
| `cache_gets_total`, `cache_size` | The in-memory caches, by `cache` (`auth-users`, `analysis`) and `result` (`hit`, `miss`) |
| `chess_ws_broadcast_seconds` | Sending one game event to every socket on the game |
| `chess_ws_rtt_seconds` | Round trip of the keepalive ping, by `socket`: the network between server and players |
| `chess_ws_open`, `chess_games_live` | Open sockets, game sessions in memory |
| `rum_vital_milliseconds`, `rum_cls`, `rum_timing_milliseconds` | The browser timings above |
| `hikaricp_*`, `jvm_*`, `tomcat_*` | Database pool, memory and GC, threads |

Latency meters publish a few fixed histogram buckets (10 ms to 5 s) rather than full percentile histograms, so p50/p95/p99
come from `histogram_quantile` and the series fit the free tier. `/actuator/prometheus` is not public: nginx only proxies
`/api` and `/ws`.

### Logs

* **nginx**: one line per request with `rt=` (the whole request, in seconds), `urt=` (the backend's part) and `rid=`
  (the request id). The healthcheck is left out.
* **Backend**: in production, one JSON object per line. Every line logged during a request has its `requestId` (the
  same as nginx's `rid`), and each request ends with an `http.access` line: `method`, `path`, `status`, `durationMs`.
* **Caddy**: its own JSON access log, with `duration`.

To follow one slow request: find it in nginx's log, then search the backend's logs for its `rid`.

## Setting it up

1. Create a free account at grafana.com and a stack (pick the region closest to the server: Frankfurt or London for
   `eu-west-3`).
2. In the stack's page (My Account → your stack), open **Prometheus → Details** and **Loki → Details**: note each URL
   and user number.
3. **Administration → Users and access → Cloud access policies**: create a policy with `metrics:write` and
   `logs:write`, and a token for it.
4. On the server, in `~/chess-project/.env`, uncomment and fill the monitoring block (see `.env.example`):
   `COMPOSE_PROFILES=monitoring` and the five `GRAFANA_CLOUD_*` settings.
5. Start it: `docker compose -f docker-compose.prod.yml up -d`. With `COMPOSE_PROFILES` set, the automatic deploys
   start it too.
6. Check it: `docker compose -f docker-compose.prod.yml logs alloy` shows no errors, and in Grafana **Explore**,
   `up{job=~".*backend.*"}` is 1 and `{container="chess_backend"}` shows log lines.
7. **Dashboards → New → Import**: upload `monitoring/dashboards/latency.json` and pick the stack's Prometheus data source.
8. **Alerts & IRM → Alert rules → Import**: import `monitoring/alerts.yml` (Prometheus rule format), then set a contact
   point (email is enough) under **Alerting → Contact points**.
9. **Testing & synthetics → Synthetics**: add HTTP checks on `https://your-site/` and `https://your-site/api/showcase`
   from two or three locations, every few minutes. These tell you the site is down even when Alloy is too.

Alloy reads the containers' logs through the Docker socket (read-only) and the host's `/proc`, `/sys` and `/` (read-only),
and uses at most 160 MB of memory. Without `COMPOSE_PROFILES=monitoring` it does not run, and nothing else changes.

## Trying it locally

The metrics need no Grafana: start the dev stack and read them.

```bash
docker compose -f docker-compose.dev.yml up
curl -s localhost:8080/actuator/prometheus | grep -E '^(chess|rum)_'
```

Play a move or two at <http://localhost:4200>, switch to another tab (that sends the browser's timings), and run the
`curl` again: `rum_timing_milliseconds_count{...name="move.roundtrip"...}` has gone up.

To time the API from the command line, see [performance.md](performance.md#measuring).
