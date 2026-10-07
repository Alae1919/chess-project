# REXCHESS

A full-stack chess game: play against an AI engine written for this project, play other people online, and
watch the position evaluated as you go. The board can be 3D or flat, and it works on a phone.

* **Play the AI at six levels**, from about 1100 to about 2900 Elo (on Stockfish's scale). The engine is a
  bitboard alpha-beta search with a small neural network, trained on Lichess games, as its evaluation.
  [How it works](docs/how-the-ai-works.md).
* **Play people online**: random matchmaking by rating, or challenge a friend by username. Clocks, draw offers,
  resignation, chat, rematch, and a countdown if your opponent drops off.
* **Analyse as you play**: an evaluation bar, a hint button, and undo in games against the AI.
* **Keep a record**: an Elo rating that moves with online results, match history, statistics and achievements.
* **Your board, your way**: a 3D board (Three.js) or a 2D one, three styles of each, drag or click to move,
  step back through the moves of the game.
* **Installable on a phone**: a layout of its own for small screens, and a web app manifest.

The interface is in French.

## How it fits together

```
 browser ──► Caddy (HTTPS) ──► nginx ──┬── the Angular app (static files)
                                       └── /api, /ws ──► Spring Boot ──► PostgreSQL
                                                          (REST + 2 WebSockets,
                                                           the chess engine inside)
```

| Part | What it is | Built with |
|---|---|---|
| [`frontend/`](frontend) | The web app | Angular 17, NgRx, Three.js |
| [`chess-engine/`](chess-engine) | The server: game rules, accounts, online play, and the AI engine | Java 17, Spring Boot 3.2, PostgreSQL 15, Flyway |
| [`training/`](training) | Trains the engine's evaluation network | Python, PyTorch |
| [`tools/`](tools) | The engine as a standalone UCI program, to play in any chess GUI | |

More in [docs/architecture.md](docs/architecture.md).

## Run it on your machine

You need Docker. The first start downloads Maven and npm packages, so give it a few minutes.

```bash
docker compose -f docker-compose.dev.yml up
```

Then open <http://localhost:4200>, register an account, and start a game. To try online play, open the site in a
second browser (or a private window), register another account, and challenge the first by username.

| What | Where |
|---|---|
| The app | <http://localhost:4200> |
| The API | <http://localhost:8080/api> |
| API explorer (Swagger UI) | <http://localhost:8080/swagger-ui.html> |
| PostgreSQL | `localhost:5432`, database `chess_db`, user `chess_user`, password `chess_pass` |

Changes to the source are picked up while it runs. To work without Docker for the app itself, or to run the
tests, see [docs/development.md](docs/development.md).

## Tests

```bash
cd chess-engine && mvn -B -ntp verify        # Java 17; needs the dev database from the step above
cd frontend && npm ci && npm run test:ci     # headless Chrome
cd frontend && npm run build -- --configuration production
```

The backend tests boot the real application against PostgreSQL. The chess engine is also checked against
independent references: perft node counts for move generation, and positions and mates computed with
[python-chess](https://python-chess.readthedocs.io/). GitHub Actions runs all of this on every push and pull request
([`ci.yml`](.github/workflows/ci.yml)).

## Deploy

The production stack is one Docker Compose file ([`docker-compose.prod.yml`](docker-compose.prod.yml)) with Caddy
for HTTPS in front. [docs/DEPLOY.md](docs/DEPLOY.md) walks through putting it on a single AWS EC2 server, including
automatic deploys from GitHub once CI passes, and backups.

```bash
cp .env.example .env        # set SITE_ADDRESS, POSTGRES_PASSWORD and JWT_SECRET
docker compose -f docker-compose.prod.yml up -d --build
```

## Documentation

| | |
|---|---|
| [docs/architecture.md](docs/architecture.md) | How the server, the web app and the database fit together, and why |
| [docs/api.md](docs/api.md) | The REST endpoints and the WebSocket events |
| [docs/how-the-ai-works.md](docs/how-the-ai-works.md) | The chess engine: board, search, neural network, difficulty levels |
| [docs/development.md](docs/development.md) | Setting up, running, testing, configuration, and common snags |
| [docs/DEPLOY.md](docs/DEPLOY.md) | Deploying to AWS |
| [training/README.md](training/README.md) | Training the evaluation network |

## Good to know

* **One server, by design.** Live games, the matchmaking queue and the WebSocket connections are held in the
  backend's memory, so the backend runs as a single instance. Running several would need that state moved into Redis or
  the database first. Games in progress are saved to the database and picked up again after a restart.
* **The AI's Elo figures are measured against Stockfish** held back to a chosen rating, which is not the scale of
  Lichess or of a chess club. Treat them as an ordering with a rough magnitude.
* **Not built yet:** a leaderboard, PGN export, spectating, and a demo login.
