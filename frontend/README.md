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

## In production

The [`Dockerfile`](Dockerfile) builds the app and serves it with nginx ([`nginx.conf`](nginx.conf)), which also proxies `/api`
and `/ws` to the backend and sets the security headers ([`nginx-snippets/`](nginx-snippets)). The Content-Security-Policy
allows no inline scripts, which is why `inlineCritical` is off in `angular.json`: Angular's critical-CSS loader is an
inline handler.
