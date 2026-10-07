# Architecture

How REXCHESS is put together, and why. For the chess engine itself see [how-the-ai-works.md](how-the-ai-works.md); for
the endpoints see [api.md](api.md).

```
                       ┌──────────────────────────── Spring Boot (one process) ────────────────────────────┐
                       │                                                                                   │
 Angular app           │  api/controller ──► application ──► domain (rules, FEN, SAN)                      │
 ┌───────────┐  REST   │       │                  │  │                                                     │
 │ NgRx      │────────►│       │                  │  └────► engine.player.AiPlayer ──► engine.core (AI)    │
 │ services  │         │       │                  │                                                        │
 │ 2D/3D     │◄────────│  infrastructure/websocket│                                                        │
 │ board     │   WS    │   /ws/game/{id}          ▼                                                        │
 └───────────┘         │   /ws/lobby      infrastructure/persistence/GameStore  (live games, in memory)    │
                       │                          │                                                        │
                       │                          ▼  after every change                                    │
                       │                  persistence (JPA) ──────────────► PostgreSQL (Flyway migrations) │
                       └───────────────────────────────────────────────────────────────────────────────────┘
```

## The server

### Layers

The code under `chess-engine/src/main/java/com/chess/` follows one rule: chess knowledge stays low, HTTP stays high.

| Package | Role |
|---|---|
| `api` | Controllers and the DTOs they exchange with the browser. No chess logic. |
| `application` | The use cases: creating and playing games, matchmaking, invitations, clocks, abandonment, Elo, accounts, analysis. |
| `domain` | The rules of chess on an object-based board: legal moves, check, mate, draws, FEN, SAN. Knows nothing of Spring, HTTP or the database. |
| `engine` | The AI. `engine.core` is a separate, bitboard implementation of the game, with search and evaluation; `engine.player` adapts it to a game; `engine.uci` runs it as a standalone UCI program. No Spring imports, so it also runs outside the server. |
| `infrastructure` | Technical plumbing: the in-memory game store, the two WebSocket handlers, configuration, error mapping. |
| `persistence` | JPA entities and repositories. |
| `security` | JWT authentication, the security filter chain, the rate limit on login and registration. |

There are two chess implementations on purpose. `domain` is clear and easy to check against the rules, and decides
what is legal. `engine.core` is built for speed (about 600,000 positions a second, against 10,000 to 30,000 for the
object board) and exists only to choose the AI's moves. `AiPlayer` replays the game's moves into a bitboard
position and searches it.

### A game's life

A game that is being played lives in memory, in a `GameSession` held by `GameStore`. Every request that changes it
(a move, a resignation, an undo) goes through `GameApplicationService`, which updates the session and writes the new state
to PostgreSQL before answering. The database is therefore always current, and the memory is a working copy:

* A session that is not in memory (evicted, or after a restart) is rebuilt from the database the next time someone
  asks for it. `GameSessionJanitor` evicts finished games and idle ones every minute.
* On startup `GameRecovery` brings back every online game that was still active, because their clocks and
  disconnect countdowns live in memory and a game nobody touches would otherwise sit "active" with a frozen clock. The
  players get the usual disconnect countdown to come back.
* The search for an AI move runs outside the game's lock, so resigning or undoing while the AI thinks takes effect at
  once. A second AI request for a game that is already thinking is answered `409 ai-busy`.

Modes are `ai`, `local` (two people on one screen) and `online`. Every mode records its human players in the white and
black seats (an AI game fills the human's seat; a local game fills both with the same user), so one rule covers
authorisation for all of them: only a game's players may act on it (`GameAccess`). In an online game each player
may also move only their own pieces.

### Real time

Moves and other actions are sent over **REST**; the server **pushes** the results over WebSockets. Keeping the
write path on plain HTTP means every action is validated, authorised and answered in the same way, and the
WebSocket only has to carry news.

| Socket | Used for |
|---|---|
| `/ws/game/{gameId}` | Everything about one game: moves, game over, draw offers, chat, an opponent leaving or coming back |
| `/ws/lobby` | News for one user outside a game: a match was found, an invitation arrived, was declined, cancelled or expired |

Browsers cannot set headers on a WebSocket, so the access token travels as `?token=`. The reverse proxies are
configured to leave that query string out of their logs. The server pings every open socket, because a reverse proxy
closes a connection that has been silent for a minute, which the other player would have seen as a disconnect. The
browser side (`ReconnectingSocket`) reconnects with a growing delay and refreshes the token first if it is about to
expire. The event list is in [api.md](api.md#websocket-events).

### Online play

* **Random matchmaking.** A player joins a queue for a time control. Every second a scheduled task pairs players of the
  same time control whose ratings are within a window that starts at 200 points and widens by 100 for every 30
  seconds spent waiting, up to 600. Colours are assigned and both players hear `MATCH_FOUND` on their lobby socket.
  Queue rows of players who are no longer in the lobby are purged every 30 seconds, since a server restart closes no
  sockets.
* **Friend challenge.** A player searches for a username and sends an invitation (optionally choosing a colour; a
  rematch swaps colours). The invitee is told over the lobby socket, and accepting creates the game. Unanswered invitations
  expire. News is sent only after the database transaction has committed, so nobody can open a game that the
  database does not hold yet.
* **Clocks belong to the server.** `ClockWatcher` looks at every live game twice a second and ends the ones whose clock
  has run out; a clock only runs out when somebody looks, and nobody looks while a player has simply stopped moving.
* **Called off, not lost.** An online game where a side takes more than 30 seconds to make its first move is aborted,
  with no rating change.
* **Leaving.** When a player's last socket on a game closes, the opponent is told and a 60-second countdown starts.
  Returning cancels it; otherwise the player who stayed wins. Clocks keep running, so a timed game may end on time
  first.
* **Ratings.** Standard Elo with K = 32 and a floor of 100, starting at 1200. Only finished online games change
  ratings; the change is stored in the history and returned with the game result.
* **No engine help against a person.** Evaluation and hints are refused (`403`) while an online game is active, and undo
  is not available online.

### Security

* Passwords are hashed with BCrypt. Login returns a short-lived **access token** (a JWT, 15 minutes) and a
  **refresh token** (a JWT, 7 days). Refresh tokens are stored as SHA-256 hashes, so a database leak does not expose
  them. Each works once: refreshing trades it for a new pair, and presenting one that was already traded in is taken
  as a stolen copy and ends every session of that user. Logout revokes the token. A refresh token is not accepted as
  an access token, on REST or on a WebSocket. The server has no sessions and no cookies, which is also why CSRF
  protection is off.
* `JWT_SECRET` has no default: the server refuses to start without one.
* Browsers may call the API and open WebSockets only from the origins in `ALLOWED_ORIGINS`.
* Login and registration are limited per address (20 a minute each, counted by the backend), with a coarser limit in
  nginx in front. The limit's counts are in memory, so each instance counts on its own.
* Unauthenticated requests get `401`, which tells the client to refresh its token; `403` is kept for "signed in but not
  allowed", which a refresh cannot fix.
* In production only Caddy is reachable from outside (HTTPS, HSTS). nginx adds a Content-Security-Policy and
  related headers, and the backend container runs as a non-root user. The health endpoint is the only actuator
  endpoint exposed, and nginx does not route it publicly.

### Database

PostgreSQL, with the schema owned by **Flyway** (`src/main/resources/db/migration`, `V1` to `V8`). Hibernate only
validates it (`ddl-auto: validate`), so a mismatch between entities and tables stops the server at startup instead of
corrupting data. In production Flyway refuses to adopt a database that has tables but no migration history.

| Table | Holds |
|---|---|
| `users`, `user_preferences` | Accounts, rating and counters; board and gameplay preferences |
| `refresh_tokens` | Hashed refresh tokens |
| `games`, `game_moves`, `captured_pieces` | Every game with its clock and result, and its moves |
| `elo_history` | One row per rating change |
| `achievement_definitions`, `user_achievements` | The ten achievements and who holds them |
| `chat_messages` | In-game chat |
| `matchmaking_queue`, `game_invitations` | Online play |

`open-in-view` is off: a request must not hold a database connection while it does other work, and an AI search can
take seconds. Code that reads lazy collections therefore opens its own transaction.

## The web app

An Angular 17 single-page app (standalone components, lazy-loaded routes) in `frontend/src/app/`.

| Folder | Holds |
|---|---|
| `core/` | Models (the shapes the API returns), services (one per area of the API, plus the two sockets), the auth guard, the HTTP interceptors, and pure helper functions in `utils/` |
| `store/` | NgRx state, one slice each for `game`, `account` and `lobby`, plus `session`, a meta-reducer that clears everything when the user logs out |
| `shared/` | Components used by several pages: the 2D board, the 3D board, the promotion picker, the game-over dialog, the navbar and the phone tab bar |
| `features/` | The pages: `landing`, `auth` (login and registration), `home` (set up a game), `game`, `online` (matchmaking and invitations), `account` |

* **State.** Effects call the services and subscribe to the game socket, turning each event (a move, a chat message,
  a draw offer, an opponent leaving, game over) into a store action, so the store is the single source of truth for the
  game on screen. After a dropped connection the game is reloaded to pick up what was missed. Logic that does not need
  Angular (clock arithmetic, move history, promotion rules, drag and drop, game results) lives in `core/utils` as plain
  functions with their own specs.
* **Auth.** An interceptor adds the bearer token; another catches a `401`, refreshes the token once and replays the
  request.
* **Boards.** `chess-board` draws the flat board and `chess-board-3d` draws a Three.js scene, both with the same
  interactions: click or drag to move, legal-move highlights, a promotion picker, and stepping back through the moves of
  the game. Mode and style are saved as user preferences.
* **Phones.** Phones get a different game screen (a compact player bar, a dock, bottom sheets, a tab bar) selected by
  the breakpoints in `shared/styles/_mq.scss` and `core/utils/viewport.ts`.
* **Production serving.** nginx serves the compiled files with long caching for the hashed bundles and `no-cache`
  for `index.html`, and proxies `/api` and `/ws` to the backend.

## Decisions worth knowing about

| Decision | Reason | Cost |
|---|---|---|
| One backend instance | Live games, queue and sockets are in memory; two backends would each know different games | Not horizontally scalable until that state is in Redis or the database. Fine for the size of this project |
| Moves over REST, pushes over WebSocket | One validated, authorised write path | One extra HTTP round trip per move |
| The engine does not depend on Spring | It can be tested, benchmarked and played against other engines on its own, as a UCI program | An adapter (`AiPlayer`) between the game and the engine |
| Network trained offline, shipped as a 386 KB file | No GPU or Python at runtime; integer arithmetic gives identical results on every machine | Retraining is a manual step ([training/README.md](../training/README.md)) |
| Flyway owns the schema | Changes are reviewed, versioned and repeatable | A migration for every schema change |
| Games are saved after every change | A crash loses at most the move in flight | One database write per move |
