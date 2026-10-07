# API reference

The server speaks JSON over REST, plus two WebSockets for news. This page is a map of it; the running server is the
exact reference. With the dev stack up, **Swagger UI** at <http://localhost:8080/swagger-ui.html> lists every endpoint
with its request and response shapes (the OpenAPI document is at `/v3/api-docs`). In production the proxy only
forwards `/api` and `/ws`, so those pages are not public.

Why moves go over REST and news over WebSockets: [architecture.md](architecture.md#real-time).

## Conventions

* **Base path** `/api`. Bodies and answers are JSON.
* **Authentication**: send `Authorization: Bearer <accessToken>` on everything except `/api/auth/**`. A missing or expired
  token is `401`. See [Authentication](#authentication).
* **Games are identified by a UUID string**; so are invitations.
* **Moves are in UCI notation**: `e2e4`, and a fifth letter for a promotion, `e7e8q`. Squares in the game JSON are
  `{row, col}` pairs.
* **Errors** are [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457):

  ```json
  { "type": "https://chess-engine/errors/illegal-move", "title": "Illegal Move", "status": 422, "detail": "..." }
  ```

  The last segment of `type` is a stable code a client can switch on.

| Status | `type` code | Meaning |
|---|---|---|
| 400 | `bad-request`, `invalid-fen`, or a validation error | The request is malformed or breaks a rule on a field |
| 401 | | Not signed in, or the token expired: refresh it |
| 403 | `not-a-player` | Signed in, but not a player of this game, or the action is not allowed in this mode |
| 404 | `game-not-found`, `not-found` | No such game or record |
| 409 | `not-your-turn`, `game-over`, `ai-busy`, `draw-declined`, `conflict` | The request is fine but the game's state refuses it |
| 422 | `illegal-move` | The move is not legal in this position |
| 429 | | Too many login or registration attempts from one address |

## Authentication

Passwords are 6 to 100 characters. Usernames are 3 to 30 characters: letters, digits, `_` and `-`.

| | Endpoint | Body | Answer |
|---|---|---|---|
| POST | `/api/auth/register` | `{username, email, password}` | `201` and tokens |
| POST | `/api/auth/login` | `{email, password}` | tokens |
| POST | `/api/auth/refresh` | `{refreshToken}` | a new pair of tokens |
| POST | `/api/auth/logout` | `{refreshToken}` | `204`; that refresh token stops working |

Tokens come as `{accessToken, refreshToken, expiresIn}` (`expiresIn` in milliseconds). The access token lasts 15
minutes, the refresh token 7 days. **A refresh token works once**: refreshing returns a new pair, and presenting an old
one again ends all of that user's sessions.

## Games

| | Endpoint | Purpose |
|---|---|---|
| POST | `/api/games` | Create a game. Returns `201` and the game |
| GET | `/api/games/{id}` | The game's full state |
| GET | `/api/games/{id}/legal-moves` | Legal moves for the side to move, as UCI strings |
| POST | `/api/games/{id}/moves` | Play a move: `{"move": "e2e4"}`. Returns the game |
| POST | `/api/games/{id}/ai-move` | Let the AI play its move. `409 ai-busy` if it is already thinking |
| POST | `/api/games/{id}/resign` | Resign |
| POST | `/api/games/{id}/draw-offer` | Offer a draw (accepts one the opponent already offered) |
| POST | `/api/games/{id}/draw-offer/accept` | Accept the opponent's offer |
| POST | `/api/games/{id}/draw-offer/decline` | Decline it |
| DELETE | `/api/games/{id}/moves/last?plies=1` | Undo. `plies=2` takes back a move against the AI and its reply. Not in online games |
| POST | `/api/games/{id}/save` | Save the game to resume later |
| GET | `/api/games/{id}/evaluation` | The engine's score for the current position and its best move. Not during a live online game |
| DELETE | `/api/games/{id}` | Delete a game. Not for online games |
| GET | `/api/games/{id}/chat` | The game's chat messages |
| POST | `/api/games/{id}/chat` | Send one: `{"content": "..."}`, up to 500 characters |

Only a game's players may call any of these. In an online game each player may move only on their own turn.

### Creating a game

```json
{
  "mode": "ai",
  "aiDifficulty": 4,
  "playerColor": "random",
  "timeControl": { "type": "rapid", "initialMs": 600000, "incrementMs": 0 },
  "enableUndo": true,
  "confirmMoves": false,
  "showLegalMoves": true,
  "realTimeAnalysis": false,
  "fen": null
}
```

| Field | Values |
|---|---|
| `mode` | `ai` or `local` (two people on one screen). Online games are created by matchmaking and invitations, not here |
| `aiDifficulty` | 1 to 6, see [how-the-ai-works.md](how-the-ai-works.md#difficulty-levels). Default 4 |
| `playerColor` | `white`, `black` or `random`. `random` is settled by the server once |
| `timeControl.type` | `blitz`, `rapid`, `classical` or `unlimited`. `initialMs` up to a day, `incrementMs` up to an hour |
| `enableUndo`, `confirmMoves`, `showLegalMoves`, `realTimeAnalysis` | The game's options, stored with it |
| `fen` | Optional custom starting position |

### The game object

What every game endpoint returns (abridged):

```json
{
  "id": "42a7ec90-d66d-48ef-913e-79035223489f",
  "mode": "ai",
  "status": "active",
  "playerWhite": { "username": "alice", "elo": 1200, "timeRemainingMs": 598000, "isAi": false },
  "playerBlack": { "username": "AI", "isAi": true, "aiDifficulty": 4, "timeRemainingMs": 600000 },
  "currentTurn": "white",
  "timeControl": { "type": "rapid", "initialMs": 600000, "incrementMs": 0 },
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "moves": [ { "from": {"row": 0, "col": 0}, "to": {"row": 0, "col": 0}, "algebraicNotation": "e4", "...": "..." } ],
  "moveHistory": ["e4"],
  "lastMove": "e2e4",
  "legalMoves": ["a7a6", "..."],
  "halfMoveClock": 0,
  "fullMoveNumber": 1,
  "drawOfferedBy": null,
  "result": null
}
```

`status` is `waiting`, `active`, `paused`, `finished` or `aborted` (a game called off before its first move). When the
game is over, `result` is `{winner, reason, whiteEloChange, blackEloChange}`. `winner` is `white` or `black`, or null
for a draw. The rating changes are set only for finished online games. `reason` is one of `checkmate`, `stalemate`,
`resignation`, `timeout`, `abandonment`, `draw_agreement`, `threefold_repetition`, `fifty_move_rule`,
`insufficient_material`.

## Online play

**Matchmaking.** Join the queue with a time control; the match arrives on the [lobby socket](#lobby-socket).

| | Endpoint | Body | |
|---|---|---|---|
| POST | `/api/matchmaking/queue` | `{timeControlType, timeControlInitialMs, timeControlIncrementMs}` | `201` and the queue status |
| DELETE | `/api/matchmaking/queue` | | Leave the queue, `204` |
| GET | `/api/matchmaking/queue/status` | | Where you are in the queue; `404` if you are not in it. A fallback to polling |

**Invitations.** Challenge a friend by username.

| | Endpoint | Body | |
|---|---|---|---|
| GET | `/api/users/search?username=ab` | | Up to 10 users whose name starts with `ab`: `{id, username, elo, avatarUrl}` |
| POST | `/api/invitations` | `{inviteeUsername, timeControlType, timeControlInitialMs, timeControlIncrementMs, inviterColor?}` | `201` and the invitation |
| GET | `/api/invitations/pending` | | Invitations you have received and not answered |
| POST | `/api/invitations/{id}/respond` | `{"response": "accept"}` or `"decline"` | Accepting returns the new game; declining returns the invitation |
| DELETE | `/api/invitations/{id}` | | The inviter cancels. `204` |

## Account

All under `/api/users/me`.

| | Endpoint | Purpose |
|---|---|---|
| GET | `/me` | Full profile: rating, rank, stats, preferences, achievements |
| PATCH | `/me` | Change `username`, `country` or `avatarUrl` |
| PATCH | `/me/preferences` | Board mode and styles, sound, animations, and the default game options. Any field may be left out |
| GET | `/me/stats` | Games played, wins, losses, draws, win rate, streaks, rating history |
| GET | `/me/elo-history` | `[{date, elo}]` |
| GET | `/me/achievements` | The ten achievements, with `unlocked` and progress |
| GET | `/me/match-history?page=0&size=20` | Finished games, newest first. At most 50 per page |
| GET | `/me/saved-games` | Games saved to resume |
| DELETE | `/me/saved-games/{id}` | Delete one |
| POST | `/me/change-password` | `{oldPassword, newPassword}`, `204` |
| DELETE | `/me` | Delete the account. Asks for the password again: `{password}`. `204` |

Board preferences take `boardMode` `2d` or `3d`, `boardStyle3d` one of `marble-gold`, `classic-wood`, `ebony-ivory`, and
`boardStyle2d` one of `classic-wood`, `luxe`, `slate-blue`.

## WebSockets

Both sockets are **server to client only**: the server pushes events, and anything the client sends is ignored. Moves and
other actions go through the REST endpoints above.

Authenticate with the access token, as `?token=<accessToken>` in the URL (browsers cannot set headers on a WebSocket) or
as an `Authorization: Bearer` header where the client can. A refresh token is refused. Only the site's own origin
(`ALLOWED_ORIGINS`) may connect from a browser.

Every message is `{"type": "...", "payload": ...}`.

### Game socket

`/ws/game/{gameId}`: everything about one game, sent to every player connected to it. Connecting also counts as being
present for the abandonment countdown (see [architecture.md](architecture.md#online-play)).

| `type` | `payload` | When |
|---|---|---|
| `MOVE_MADE` | the game | A move was played, by a person or the AI |
| `GAME_OVER` | the game | The game ended: mate, draw, resignation, time, or a player who stayed away |
| `DRAW_OFFERED` | the game | A draw was offered (`drawOfferedBy` says by whom) |
| `DRAW_DECLINED` | the game | It was declined |
| `CHAT_MESSAGE` | `{id, gameId, senderId, senderUsername, content, sentAt}` | Someone wrote in the chat |
| `OPPONENT_DISCONNECTED` | `{color, timeoutMs}` | The opponent's last connection closed; they lose in `timeoutMs` unless they come back |
| `OPPONENT_RECONNECTED` | `{color}` | They came back |

A client that reconnects after a drop should reload the game with `GET /api/games/{id}`: events sent while it was away
are not replayed.

### Lobby socket

`/ws/lobby`: news for one user outside any game. Connect while you are in the matchmaking queue or want to receive
invitations; the server treats a queue entry whose owner is not connected to the lobby as abandoned.

| `type` | `payload` | When |
|---|---|---|
| `MATCH_FOUND` | `{gameId, opponentUsername, opponentElo, playerColor, timeControlType, timeControlInitialMs, timeControlIncrementMs}` | Matchmaking paired you, or an invitation you sent or received was accepted. Open the game |
| `INVITE_RECEIVED` | the invitation, with `inviterElo` | Someone challenged you |
| `INVITE_DECLINED` | the invitation | Your invitation was declined |
| `INVITE_CANCELLED` | the invitation | An invitation sent to you was cancelled |
| `INVITE_EXPIRED` | the invitation | One expired unanswered, sent to both sides |
