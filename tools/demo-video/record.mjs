// Records the REXCHESS demo video.
//
// Drives the running app (frontend on :4200 via `ng serve`, backend on :8080) with
// Playwright, captures each scene with the Chrome DevTools screencast, and assembles
// out/rexchess-demo.mp4 with ffmpeg. Every move, click and message in the video is
// real: two demo accounts are (re)created through the API before recording.
//
// Start the stack first, e.g. `docker compose -f docker-compose.dev.yml up` from the repo
// root (or the backend and `ng serve` locally with a proxy to localhost:8080), then:
//
//   npm install && npm run record          (HEADED=1 to watch it happen)
//
// The demo accounts alice@rexchess.dev and bob@rexchess.dev are deleted and recreated each run.
import { chromium } from 'playwright';
import ffmpegPath from 'ffmpeg-static';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const APP = process.env.APP_URL ?? 'http://localhost:4200';
const API = process.env.API_URL ?? 'http://localhost:8080/api';
const OUT = path.join(HERE, 'out');
const WORK = path.join(OUT, 'work');
const CARDS = pathToFileURL(path.join(HERE, 'cards.html')).href;

// Pages are laid out at 1280x720 CSS px and rendered at 1.5x, so frames are a real 1920x1080.
// (The screencast ignores an emulated device scale factor; it honours the browser-wide one.)
const VIEWPORT = { width: 1280, height: 720 };
const W = 1920, H = 1080, FPS = 30;
const FADE = 0.6;  // cross-fade between scenes (s)
const PANE = { w: 928, h: 522, y: 250, left: 20, right: 972 };

const ALICE = { username: 'alice', email: 'alice@rexchess.dev', password: 'demo-alice-2026' };
const BOB = { username: 'bob', email: 'bob@rexchess.dev', password: 'demo-bob-2026' };

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const now = () => Date.now() / 1000;
const log = (...a) => console.log(`[${new Date().toISOString().slice(11, 19)}]`, ...a);

// ── ffmpeg ────────────────────────────────────────────────────────────────────

function ffmpeg(args) {
  return new Promise((resolve, reject) => {
    const p = spawn(ffmpegPath, ['-y', '-hide_banner', '-loglevel', 'error', ...args], { stdio: ['ignore', 'inherit', 'inherit'] });
    p.on('exit', (code) => (code === 0 ? resolve() : reject(new Error(`ffmpeg exited with ${code}`))));
  });
}

const X264 = ['-c:v', 'libx264', '-preset', 'slow', '-crf', '18', '-pix_fmt', 'yuv420p', '-r', String(FPS)];
const fwd = (p) => p.replace(/\\/g, '/');

/** Screencast frames (variable rate, wall-clock stamped) -> constant-rate clip covering [t0, t1]. */
async function encodeFrames(frames, t0, t1, name) {
  let first = 0;
  frames.forEach((f, i) => { if (f.at <= t0) first = i; });
  const shown = frames.slice(first).filter((f, i) => i === 0 || f.at < t1);
  if (!shown.length) throw new Error(`${name}: no frames captured`);

  const lines = ['ffconcat version 1.0'];
  shown.forEach((f, i) => {
    const from = i === 0 ? t0 : f.at;
    const to = i + 1 < shown.length ? shown[i + 1].at : t1;
    lines.push(`file '${fwd(f.file)}'`, `duration ${Math.max(to - from, 0.001).toFixed(4)}`);
  });
  lines.push(`file '${fwd(shown[shown.length - 1].file)}'`);
  const list = path.join(WORK, `${name}.ffconcat`);
  fs.writeFileSync(list, lines.join('\n'));

  const out = path.join(WORK, `${name}.mp4`);
  const duration = Math.round((t1 - t0) * FPS) / FPS;
  await ffmpeg(['-f', 'concat', '-safe', '0', '-i', list,
    '-vf', `fps=${FPS},scale=${W}:${H}:flags=lanczos,setsar=1`, '-t', duration.toFixed(3), ...X264, out]);
  return { file: out, duration };
}

// ── screencast recorder ───────────────────────────────────────────────────────

class Recorder {
  constructor(page, name) {
    this.page = page;
    this.name = name;
    this.dir = path.join(WORK, name);
    this.frames = [];
    this.writes = [];
    this.skew = Infinity; // wall clock minus screencast clock
  }

  async start() {
    fs.rmSync(this.dir, { recursive: true, force: true });
    fs.mkdirSync(this.dir, { recursive: true });
    this.cdp = await this.page.context().newCDPSession(this.page);
    this.cdp.on('Page.screencastFrame', (f) => {
      const file = path.join(this.dir, `${String(this.frames.length).padStart(6, '0')}.jpg`);
      this.skew = Math.min(this.skew, now() - f.metadata.timestamp);
      this.frames.push({ file, ts: f.metadata.timestamp });
      this.writes.push(fs.promises.writeFile(file, Buffer.from(f.data, 'base64')));
      this.cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }).catch(() => {});
    });
    await this.cdp.send('Page.startScreencast', { format: 'jpeg', quality: 92, maxWidth: W, maxHeight: H, everyNthFrame: 1 });
    await sleep(250);
  }

  async stop() {
    await this.cdp.send('Page.stopScreencast');
    await Promise.all(this.writes);
    await this.cdp.detach();
    for (const f of this.frames) f.at = f.ts + this.skew;
    log(`${this.name}: ${this.frames.length} frames`);
    return this.frames;
  }
}

/** Record one page from now until the returned function is called. */
async function recordScene(page, name) {
  const rec = new Recorder(page, name);
  await rec.start();
  const t0 = now();
  return async (tailMs = 0) => {
    await sleep(tailMs);
    const t1 = now();
    return encodeFrames(await rec.stop(), t0, t1, name);
  };
}

// ── API: fresh demo accounts ──────────────────────────────────────────────────

async function api(route, { method = 'GET', token, body } = {}) {
  const res = await fetch(API + route, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  return { ok: res.ok, status: res.status, data: text ? JSON.parse(text) : null };
}

/** Delete the demo account if an earlier run left it behind. */
async function resetUser(u) {
  const login = await api('/auth/login', { method: 'POST', body: { email: u.email, password: u.password } });
  // deleting an account asks for the password again
  if (login.ok) await api('/users/me', { method: 'DELETE', token: login.data.accessToken, body: { password: u.password } });
}

async function registerUser(u) {
  const r = await api('/auth/register', { method: 'POST', body: u });
  if (!r.ok) throw new Error(`could not register ${u.username}: ${r.status} ${JSON.stringify(r.data)}`);
}

// ── browser helpers ───────────────────────────────────────────────────────────

let browser;

async function newPage(emulation = { viewport: null }) {
  const ctx = await browser.newContext({ ...emulation, locale: 'fr-FR' });
  await ctx.addInitScript({ path: path.join(HERE, 'overlay.js') });
  const page = await ctx.newPage();
  page.pos = { x: VIEWPORT.width * 0.62, y: VIEWPORT.height * 0.55 };
  page.on('pageerror', (e) => log('page error:', e.message));
  return page;
}

const ease = (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2);

async function moveTo(page, x, y, ms) {
  const { x: x0, y: y0 } = page.pos;
  const dist = Math.hypot(x - x0, y - y0);
  const duration = ms ?? Math.min(900, Math.max(320, 260 + dist * 0.55));
  const steps = Math.max(6, Math.round(duration / 18));
  for (let i = 1; i <= steps; i++) {
    const e = ease(i / steps);
    await page.mouse.move(x0 + (x - x0) * e, y0 + (y - y0) * e);
    await sleep(12);
  }
  page.pos = { x, y };
}

async function clickAt(page, x, y) {
  await moveTo(page, x, y);
  await sleep(120);
  await page.mouse.down();
  await sleep(70);
  await page.mouse.up();
  await sleep(160);
}

async function clickEl(page, target) {
  const loc = typeof target === 'string' ? page.locator(target).first() : target;
  await loc.waitFor({ state: 'visible', timeout: 15000 });
  // wait for smooth scrolling / animations to settle so the click lands where the element is
  let b = await loc.boundingBox();
  for (let i = 0; i < 40; i++) {
    await sleep(60);
    const next = await loc.boundingBox();
    const still = Math.abs(next.x - b.x) < 0.5 && Math.abs(next.y - b.y) < 0.5;
    b = next;
    if (still) break;
  }
  await clickAt(page, b.x + b.width / 2, b.y + b.height / 2);
}

async function typeInto(page, target, text) {
  await clickEl(page, target);
  await page.keyboard.type(text, { delay: 75 });
}

const caption = (page, title, sub = '') => page.evaluate(([t, s]) => window.__demo.caption(t, s), [title, sub]);
const hideCaption = (page) => page.evaluate(() => window.__demo.hideCaption());
const smoothScroll = (page, selector, block = 'start') =>
  page.evaluate(([s, b]) => document.querySelector(s)?.scrollIntoView({ behavior: 'smooth', block: b }), [selector, block]);

/** Put the drawn cursor where the mouse is (after a full page load it starts off-screen). */
async function showCursor(page) {
  await page.mouse.move(page.pos.x + 1, page.pos.y);
  await page.mouse.move(page.pos.x, page.pos.y);
}

// ── board helpers ─────────────────────────────────────────────────────────────

const toSquare = (alg) => ({ row: 8 - Number(alg[1]), col: alg.charCodeAt(0) - 97 });

/**
 * Screen point that lands on a square. On the 3D board it projects points of the square
 * through the scene's camera and keeps the first one the scene's own picking agrees with
 * (a nearer piece can hide the centre); on the 2D board it is the square's centre.
 */
async function squarePoint(page, alg) {
  const p = await page.evaluate(({ row, col }) => {
    const host3d = document.querySelector('.board-stage app-chess-board-3d');
    if (host3d) {
      const scene = window.ng.getComponent(host3d).scene;
      const V3 = scene.camera.position.constructor;
      const rect = host3d.querySelector('canvas').getBoundingClientRect();
      const offsets = [[0, 0], [0, 0.2], [0, -0.2], [0.2, 0], [-0.2, 0], [0.2, 0.2], [-0.2, 0.2], [0.2, -0.2], [-0.2, -0.2], [0, 0.38], [0, -0.38]];
      for (const h of [0.1, 0.45]) {
        for (const [dx, dz] of offsets) {
          const v = scene.boardGroup.localToWorld(new V3(-3.5 + col + dx, h, -3.5 + row + dz)).project(scene.camera);
          const x = rect.left + ((v.x + 1) / 2) * rect.width;
          const y = rect.top + ((1 - v.y) / 2) * rect.height;
          const hit = scene.pick({ clientX: x, clientY: y });
          if (hit && hit.row === row && hit.col === col) return { x, y };
        }
      }
      return null;
    }
    const board = document.querySelector('.board-stage app-chess-board');
    const flipped = window.ng.getComponent(board).flipped;
    const r = board.querySelectorAll('.sq')[flipped ? 63 - (row * 8 + col) : row * 8 + col].getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
  }, toSquare(alg));
  if (!p) throw new Error(`no screen point for ${alg}`);
  return p;
}

/** Legal targets shown for the selected piece, once the server has answered. */
async function waitForHints(page, timeout = 8000) {
  const handle = await page.waitForFunction(() => {
    const host3d = document.querySelector('.board-stage app-chess-board-3d');
    if (host3d) {
      const vm = window.ng.getComponent(host3d).vm;
      return vm?.selected && vm.legalMoves?.length ? vm.legalMoves : null;
    }
    return document.querySelector('.board-stage .sq.hint, .board-stage .sq.capture') ? true : null;
  }, null, { timeout });
  return handle.jsonValue();
}

async function pieceAt(page, alg) {
  return page.evaluate(({ row, col }) => {
    const host3d = document.querySelector('.board-stage app-chess-board-3d');
    return window.ng.getComponent(host3d).vm?.board?.squares?.[row]?.[col] ?? null;
  }, toSquare(alg));
}

async function clickSquare(page, alg) {
  const p = await squarePoint(page, alg);
  await clickAt(page, p.x, p.y);
}

async function playMove(page, from, to) {
  await clickSquare(page, from);
  await waitForHints(page);
  await sleep(550); // let the hints be seen
  await clickSquare(page, to);
}

const movesShown = (page) =>
  page.evaluate(() => [...document.querySelectorAll('.notation-table__mv')].filter((td) => td.textContent.trim()).length);

async function waitForMoves(page, n, timeout = 20000) {
  await page.waitForFunction(
    (n) => [...document.querySelectorAll('.notation-table__mv')].filter((td) => td.textContent.trim()).length >= n,
    n, { timeout },
  );
}

async function waitForBoard(page) {
  await page.waitForSelector('.board-stage', { timeout: 20000 });
  await page.waitForFunction(() => {
    const host3d = document.querySelector('.board-stage app-chess-board-3d');
    return host3d ? !!window.ng?.getComponent(host3d)?.scene : !!document.querySelector('.board-stage .sq');
  }, null, { timeout: 20000 });
}

async function canvasCenter(page) {
  const b = await page.locator('.board-stage canvas').boundingBox();
  return { x: b.x + b.width / 2, y: b.y + b.height / 2 };
}

/** Drag the 3D board vertically (down = towards the top-down view). */
async function dragBoard(page, dy) {
  const c = await canvasCenter(page);
  const start = { x: c.x + 40, y: c.y - dy / 2 };
  await moveTo(page, start.x, start.y);
  await page.mouse.down();
  const steps = 40;
  for (let i = 1; i <= steps; i++) {
    const e = ease(i / steps);
    await page.mouse.move(start.x + 30 * Math.sin(e * Math.PI), start.y + dy * e);
    await sleep(16);
  }
  await page.mouse.up();
  page.pos = { x: start.x, y: start.y + dy };
}

// ── scenes ────────────────────────────────────────────────────────────────────

async function recordCard(card, seconds) {
  const page = await newPage();
  await page.goto(`${CARDS}`);
  await page.evaluate(() => Promise.all([
    document.fonts.load('600 84px "Cormorant Garamond"'),
    document.fonts.load('italic 500 30px "Cormorant Garamond"'),
    document.fonts.load('500 16px "Jost"'),
    document.fonts.load('400 16px "Jost"'),
  ]));
  const stop = await recordScene(page, `card-${card}`);
  await page.evaluate((id) => document.getElementById(id).classList.remove('hidden'), card);
  const clip = await stop(seconds * 1000);
  await page.context().close();
  return clip;
}

/** Landing page -> sign up -> pick the engine -> a few moves on the 3D board -> board styles. */
async function sceneSoloGame(alice) {
  await alice.goto(`${APP}/`, { waitUntil: 'domcontentloaded' });
  await alice.waitForSelector('.hero canvas');
  await alice.evaluate(() => document.fonts.ready);
  const stop = await recordScene(alice, 'solo');
  await showCursor(alice);

  // Landing
  await sleep(500);
  await caption(alice, 'REXCHESS — a full-stack chess platform', 'Angular 17 · Three.js · Spring Boot 3 · PostgreSQL');
  await moveTo(alice, 1010, 300, 1400);
  await moveTo(alice, 820, 470, 1300);
  await sleep(900);
  await caption(alice, 'Built end to end: UI, chess engine, API and real-time play', 'Portfolio project');
  await smoothScroll(alice, '#features');
  await sleep(2600);
  await smoothScroll(alice, '.cta', 'center');
  await sleep(1500);
  await hideCaption(alice);
  await clickEl(alice, '.cta a.btn-p');

  // Sign up
  await alice.waitForSelector('#username');
  await sleep(400);
  await caption(alice, 'Create an account', 'Spring Security · JWT access & refresh tokens');
  await typeInto(alice, '#username', ALICE.username);
  await typeInto(alice, '#email', ALICE.email);
  await typeInto(alice, '#password', ALICE.password);
  await sleep(300);
  await clickEl(alice, '.auth-submit');
  await alice.waitForURL('**/home');
  await hideCaption(alice);

  // Pick an opponent
  await alice.waitForSelector('.mode-card');
  await sleep(700);
  await caption(alice, 'Pick an opponent', 'Java engine (6 levels) · same screen · ranked online');
  for (const i of [1, 2]) {
    const b = await alice.locator('.mode-card').nth(i).boundingBox();
    await moveTo(alice, b.x + b.width * 0.55, b.y + b.height / 2);
    await sleep(350);
  }
  await clickEl(alice, alice.locator('.mode-card').nth(0));
  await clickEl(alice, alice.locator('.opt-btn', { hasText: 'Moyen' }));
  await clickEl(alice, alice.locator('.opt-btn', { hasText: 'Classique' }));
  await sleep(300);
  await hideCaption(alice);
  await clickEl(alice, '.start-btn');

  // Play the engine on the 3D board
  await alice.waitForURL('**/game/**');
  await waitForBoard(alice);
  await sleep(1300); // board intro
  await caption(alice, 'Every move is checked by the Java engine', 'Legal-move hints · SAN notation · server-side clocks');
  const plan = [['e2', 'e4'], ['g1', 'f3'], ['f1', 'c4'], ['e1', 'g1'], ['b1', 'c3'], ['d2', 'd3'], ['h2', 'h3'], ['a2', 'a3']];
  let played = 0;
  for (const [from, to] of plan) {
    if (played === 4) break;
    const piece = await pieceAt(alice, from);
    if (piece?.color !== 'white') continue;
    await clickSquare(alice, from);
    const targets = await waitForHints(alice, 2500).catch(() => []); // a piece with no moves shows none
    const t = toSquare(to);
    if (!targets.some((s) => s.row === t.row && s.col === t.col)) continue;
    await sleep(550);
    await clickSquare(alice, to);
    await waitForMoves(alice, played * 2 + 2); // our move and the engine's reply
    played++;
    await sleep(700);
  }

  // Orbit to the top-down view and back
  await caption(alice, 'Drag to orbit, or look straight down', 'Custom Three.js scene');
  await dragBoard(alice, 230);
  await sleep(1700);
  await dragBoard(alice, -230);
  await sleep(1100);

  // Board mode and style
  await caption(alice, '3D or 2D, in several styles', 'Saved to your account');
  await clickEl(alice, alice.locator('button.act-btn').first());
  await sleep(500);
  await clickEl(alice, alice.locator('.bsp__style', { hasText: 'Bois classique' }));
  await sleep(1800);
  await clickEl(alice, alice.locator('.bsp__mode', { hasText: '2D' }));
  await sleep(1300);
  await clickEl(alice, alice.locator('.bsp__style', { hasText: 'Luxe' }));
  await sleep(900);
  await alice.keyboard.press('Escape');
  await sleep(600);
  await hideCaption(alice);
  return stop(1200);
}

/** Alice challenges Bob, Bob accepts, and they play a short game in two browsers side by side. */
async function sceneOnline(alice, bob) {
  // Off camera: Bob signs in and waits in the lobby; Alice opens the lobby.
  await bob.goto(`${APP}/login`);
  await bob.fill('#email', BOB.email);
  await bob.fill('#password', BOB.password);
  await bob.click('.auth-submit');
  await bob.waitForURL('**/home');
  await bob.goto(`${APP}/online`);
  await alice.goto(`${APP}/online`);
  await Promise.all([alice.waitForSelector('.lobby-page'), bob.waitForSelector('.lobby-page')]);
  await sleep(2000); // lobby sockets connect
  await showCursor(alice);
  bob.pos = { x: 900, y: 420 };
  await showCursor(bob);

  const recA = new Recorder(alice, 'online-alice');
  const recB = new Recorder(bob, 'online-bob');
  await Promise.all([recA.start(), recB.start()]);
  const t0 = now();
  const captions = [];
  const say = (cap, sub) => captions.push({ at: now() - t0, cap, sub });

  await sleep(600);
  say('Challenge a friend by username', 'REST API + lobby WebSocket');
  await clickEl(alice, alice.locator('.tab', { hasText: 'Friend' }));
  await typeInto(alice, '.search-input', BOB.username);
  const result = alice.locator('.search-result-item', { hasText: BOB.username });
  await clickEl(alice, result);
  await sleep(400);
  await clickEl(alice, alice.locator('.time-btn', { hasText: 'Blitz' }));
  await clickEl(alice, alice.locator('.btn-primary', { hasText: 'Send Challenge' }));

  await bob.waitForSelector('.invitation-toast', { timeout: 15000 });
  say('The invitation arrives instantly', 'Accept, and both players are taken to the game');
  await sleep(1400);
  await clickEl(bob, '.invitation-toast .btn-accept');

  await Promise.all([alice.waitForURL('**/game/**'), bob.waitForURL('**/game/**')]);
  await Promise.all([waitForBoard(alice), waitForBoard(bob)]);
  await sleep(1400);
  say('Moves and chat sync in real time', 'Server-owned clocks · reconnect & abandon handling');

  const whiteName = await alice.evaluate(() => {
    const bars = document.querySelectorAll('.game-left .player-bar');
    return bars[bars.length - 1].querySelector('.player-bar__name').textContent.trim();
  });
  const aliceWhite = whiteName === ALICE.username;
  const white = aliceWhite ? alice : bob;
  const black = aliceWhite ? bob : alice;
  // Alice delivers mate either way: the scholar's mate as White, the fool's mate as Black
  const plies = aliceWhite
    ? [['e2', 'e4'], ['e7', 'e5'], ['f1', 'c4'], ['b8', 'c6'], ['d1', 'h5'], ['g8', 'f6'], ['h5', 'f7']]
    : [['f2', 'f3'], ['e7', 'e5'], ['g2', 'g4'], ['d8', 'h4']];
  log(`online: alice plays ${aliceWhite ? 'white' : 'black'}`);

  for (let i = 0; i < plies.length; i++) {
    const mover = i % 2 === 0 ? white : black;
    if (i === plies.length - 1) say('Checkmate is detected on the server', 'Both Elo ratings are updated');
    await playMove(mover, ...plies[i]);
    await Promise.all([waitForMoves(alice, i + 1), waitForMoves(bob, i + 1)]);
    await sleep(500);

    if (i === 1) {
      // a quick exchange in the chat
      await clickEl(bob, bob.locator('.tabs__btn', { hasText: 'Chat' }));
      await clickEl(alice, alice.locator('.tabs__btn', { hasText: 'Chat' }));
      await typeInto(alice, '.chat-pane .chat-input', 'Good luck, have fun!');
      await alice.keyboard.press('Enter');
      await bob.waitForSelector('.chat-pane .chat-msg');
      await sleep(500);
      await typeInto(bob, '.chat-pane .chat-input', 'You too!');
      await bob.keyboard.press('Enter');
      await alice.waitForFunction(() => document.querySelectorAll('.chat-pane .chat-msg').length >= 2);
      await sleep(900);
      await clickEl(alice, alice.locator('.tabs__btn', { hasText: 'Notation' }));
      await clickEl(bob, bob.locator('.tabs__btn', { hasText: 'Notation' }));
      await sleep(300);
    }
  }

  await Promise.all([alice.waitForSelector('app-game-over-dialog'), bob.waitForSelector('app-game-over-dialog')]);
  await sleep(3800);
  const t1 = now();
  const [framesA, framesB] = await Promise.all([recA.stop(), recB.stop()]);
  const [left, right] = await Promise.all([
    encodeFrames(framesA, t0, t1, 'online-alice'),
    encodeFrames(framesB, t0, t1, 'online-bob'),
  ]);
  return composeSplit(left, right, captions, t1 - t0);
}

async function sceneAccount(alice) {
  await alice.goto(`${APP}/account`);
  await alice.waitForSelector('.history-row', { timeout: 15000 });
  await alice.evaluate(() => document.fonts.ready);
  await sleep(800);
  const stop = await recordScene(alice, 'account');
  alice.pos = { x: 700, y: 360 };
  await showCursor(alice);
  await sleep(400);
  await caption(alice, 'Profile, history and rating changes', 'Stats · match history · achievements');
  await sleep(1600);
  const row = await alice.locator('.history-row').first().boundingBox();
  await moveTo(alice, row.x + row.width - 40, row.y + row.height / 2, 900);
  await sleep(600);
  await smoothScroll(alice, '.two-col', 'start');
  await sleep(2200);
  await hideCaption(alice);
  return stop(700);
}

// ── post-production ───────────────────────────────────────────────────────────

async function overlayImage(name, query) {
  const page = await newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  await page.goto(`${CARDS}?${new URLSearchParams(query)}`);
  await page.evaluate(() => Promise.all([
    document.fonts.load('500 64px "Cormorant Garamond"'),
    document.fonts.load('500 34px "Jost"'),
    document.fonts.load('400 18px "Jost"'),
  ]));
  const file = path.join(WORK, `${name}.png`);
  await page.screenshot({ path: file, omitBackground: !query.splitbg });
  await page.context().close();
  return file;
}

/** Both players' recordings side by side, with a header, labels and timed captions. */
async function composeSplit(left, right, captions, total) {
  const duration = Math.round(total * FPS) / FPS;
  const bg = await overlayImage('split-bg', { splitbg: 1 });
  const frame = await overlayImage('split-frame', {
    split: 'frame', ln: ALICE.username, lm: '· browser 1 · 2D board', rn: BOB.username, rm: '· browser 2 · 3D board',
  });
  const caps = [];
  for (const [i, c] of captions.entries()) {
    const end = i + 1 < captions.length ? captions[i + 1].at : duration;
    caps.push({ ...c, end, file: await overlayImage(`split-cap-${i}`, { split: 'cap', cap: c.cap, sub: c.sub }) });
  }

  const inputs = ['-loop', '1', '-framerate', String(FPS), '-i', bg, '-i', left.file, '-i', right.file,
    '-loop', '1', '-framerate', String(FPS), '-i', frame];
  caps.forEach((c) => inputs.push('-loop', '1', '-framerate', String(FPS), '-i', c.file));

  const f = [
    `[1:v]scale=${PANE.w}:${PANE.h}:flags=lanczos[l]`,
    `[2:v]scale=${PANE.w}:${PANE.h}:flags=lanczos[r]`,
    `[0:v][l]overlay=${PANE.left}:${PANE.y}[a]`,
    `[a][r]overlay=${PANE.right}:${PANE.y}[b]`,
    `[b][3:v]overlay=0:0[v0]`,
  ];
  caps.forEach((c, i) => {
    const d = 0.35;
    const out = Math.max(c.at + d, c.end - d);
    f.push(`[${4 + i}:v]format=rgba,fade=t=in:st=${c.at.toFixed(3)}:d=${d}:alpha=1,fade=t=out:st=${out.toFixed(3)}:d=${d}:alpha=1[c${i}]`);
    f.push(`[v${i}][c${i}]overlay=0:0:enable='between(t,${c.at.toFixed(3)},${c.end.toFixed(3)})'[v${i + 1}]`);
  });

  const out = path.join(WORK, 'online.mp4');
  await ffmpeg([...inputs, '-filter_complex', f.join(';'), '-map', `[v${caps.length}]`, '-t', duration.toFixed(3), ...X264, out]);
  return { file: out, duration };
}

/** Join the scenes with short cross-fades. */
async function assemble(clips, outFile) {
  const inputs = clips.flatMap((c) => ['-i', c.file]);
  const f = clips.map((_, i) => `[${i}:v]settb=AVTB,fps=${FPS},setpts=PTS-STARTPTS[s${i}]`);
  let prev = 's0';
  let length = clips[0].duration;
  for (let i = 1; i < clips.length; i++) {
    const offset = length - FADE;
    f.push(`[${prev}][s${i}]xfade=transition=fade:duration=${FADE}:offset=${offset.toFixed(3)}[x${i}]`);
    prev = `x${i}`;
    length += clips[i].duration - FADE;
  }
  f.push(`[${prev}]fade=t=in:st=0:d=0.4,fade=t=out:st=${(length - 0.8).toFixed(3)}:d=0.8[final]`);
  await ffmpeg([...inputs, '-filter_complex', f.join(';'), '-map', '[final]', ...X264, '-movflags', '+faststart', outFile]);
  return length;
}

// ── main ──────────────────────────────────────────────────────────────────────

async function main() {
  fs.mkdirSync(WORK, { recursive: true });
  for (const [url, what] of [[`${APP}/`, 'frontend'], [`${API}/auth/login`, 'backend']]) {
    const ok = await fetch(url, { method: what === 'backend' ? 'POST' : 'GET' }).then(() => true, () => false);
    if (!ok) throw new Error(`${what} is not reachable at ${url}`);
  }

  log('resetting demo accounts');
  await resetUser(ALICE);
  await resetUser(BOB);
  await registerUser(BOB); // Alice signs up on camera

  browser = await chromium.launch({
    headless: !process.env.HEADED,
    // a real GPU for the WebGL board; keep background pages rendering while both players record
    args: ['--use-angle=d3d11', '--enable-gpu', '--ignore-gpu-blocklist', '--force_high_performance_gpu',
      `--force-device-scale-factor=${W / VIEWPORT.width}`, `--window-size=${VIEWPORT.width},${VIEWPORT.height}`,
      '--disable-renderer-backgrounding', '--disable-background-timer-throttling', '--disable-backgrounding-occluded-windows'],
  });

  try {
    const clips = [];
    log('scene: title card');
    clips.push(await recordCard('title', 4.6));

    const alice = await newPage();
    const bob = await newPage();
    log('scene: landing, sign up, game vs engine');
    clips.push(await sceneSoloGame(alice));
    log('scene: online game');
    clips.push(await sceneOnline(alice, bob));
    log('scene: account');
    clips.push(await sceneAccount(alice));
    log('scene: end card');
    clips.push(await recordCard('end', 6));

    const outFile = path.join(OUT, 'rexchess-demo.mp4');
    log('assembling');
    const length = await assemble(clips, outFile);
    log(`done: ${outFile} (${length.toFixed(1)} s)`);
  } catch (err) {
    // leave a picture of every open page behind to see where it went wrong
    const pages = browser.contexts().flatMap((c) => c.pages());
    for (const [i, p] of pages.entries()) {
      await p.screenshot({ path: path.join(OUT, `failure-${i}.png`) }).catch(() => {});
    }
    throw err;
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
