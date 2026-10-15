#!/usr/bin/env node
// Plays games against the AI through the REST API and reports how long the server took to answer:
// the player's move, the AI's reply, and the legal-moves call a square selection makes.
// The numbers are seen from where this runs: on the server itself they are close to the backend's
// own; from home they include the network, like a player's.
//
//   node tools/load/move-latency.mjs                                  # http://localhost:8080, level 1, 40 moves
//   node tools/load/move-latency.mjs --base https://chess.example.com --level 4 --moves 60 --games 2
//
// It registers a throwaway account (lat_<random>, at example.invalid) for each run.
// Needs Node 18 or later (built-in fetch).

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, a, i, all) => (a.startsWith('--') ? [...pairs, [a.slice(2), all[i + 1]]] : pairs), []),
);
const base = (args.base ?? 'http://localhost:8080').replace(/\/$/, '');
const level = Number(args.level ?? 1);
const movesPerGame = Number(args.moves ?? 40);
const games = Number(args.games ?? 1);

const timings = { 'POST /moves': [], 'POST /ai-move': [], 'GET /legal-moves': [] };

async function call(method, path, body, token) {
  const started = performance.now();
  const res = await fetch(base + '/api' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const ms = performance.now() - started;
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${path}: ${res.status} ${text.slice(0, 200)}`);
  return { ms, data: text ? JSON.parse(text) : null };
}

async function register() {
  const id = Math.random().toString(36).slice(2, 10);
  const { data } = await call('POST', '/auth/register', {
    username: `lat_${id}`, email: `lat_${id}@example.invalid`, password: `pw-${id}-${id}`,
  });
  return data.accessToken;
}

// a finished game has a result (and no legal moves)
const isOver = (game) => game.result != null || !(game.legalMoves ?? []).length;

async function playOne(token) {
  let { data: game } = await call('POST', '/games', {
    mode: 'ai', aiDifficulty: level, playerColor: 'white',
    timeControl: { type: 'rapid', initialMs: 3_600_000, incrementMs: 0 },
    enableUndo: false, confirmMoves: false, showLegalMoves: true, realTimeAnalysis: false,
  }, token);

  for (let i = 0; i < movesPerGame && !isOver(game); i++) {
    const legal = await call('GET', `/games/${game.id}/legal-moves`, null, token);
    timings['GET /legal-moves'].push(legal.ms);
    const choices = game.legalMoves ?? [];
    if (!choices.length) break;
    const move = choices[Math.floor(Math.random() * choices.length)];

    const played = await call('POST', `/games/${game.id}/moves`, { move }, token);
    timings['POST /moves'].push(played.ms);
    game = played.data;
    if (isOver(game)) break;

    const reply = await call('POST', `/games/${game.id}/ai-move`, null, token);
    timings['POST /ai-move'].push(reply.ms);
    game = reply.data;
  }
}

function pct(sorted, p) {
  if (!sorted.length) return NaN;
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

const token = await register();
for (let g = 0; g < games; g++) await playOne(token);

console.log(`\n${base}, AI level ${level}, ${games} game(s), up to ${movesPerGame} moves each\n`);
console.log('| Call | n | p50 ms | p95 ms | max ms |');
console.log('|---|---|---|---|---|');
for (const [name, list] of Object.entries(timings)) {
  const s = [...list].sort((a, b) => a - b);
  const f = (v) => (Number.isNaN(v) ? '-' : v.toFixed(0));
  console.log(`| ${name} | ${s.length} | ${f(pct(s, 50))} | ${f(pct(s, 95))} | ${f(s[s.length - 1] ?? NaN)} |`);
}
