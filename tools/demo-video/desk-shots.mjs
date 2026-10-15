// Photographs the 3D game page in the desk view, in each camera pose, for checking it by eye.
//
//   node desk-shots.mjs [only] [look]     (backend on :8080, frontend on :4200; Docker dev stack)
//
// Creates a throwaway account and a local game through the API, opens the game, and writes out/desk/*.png:
// the first view (to hold against design/refs/desk/00-reference.png), then each preset, then real drags,
// the wheel and a pinch-free pan. The account (and its games) is deleted afterwards.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, 'out', 'desk');
const APP = 'http://localhost:4200';
const API = 'http://localhost:8080/api';
const ONLY = process.argv[2];
const LOOK = process.argv[3] ?? 'classic-wood';
fs.mkdirSync(OUT, { recursive: true });

async function api(route, { method = 'GET', token, body } = {}) {
  const res = await fetch(API + route, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) },
    body: body && JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${route}: ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const psql = (sql) => execFileSync('docker', ['exec', 'chess_db_dev', 'psql', '-U', 'chess_user', '-d', 'chess_db', '-c', sql], { stdio: 'pipe' }).toString();

const id = Math.random().toString(36).slice(2, 8);
const user = { username: `desk_${id}`, email: `desk_${id}@rexchess.dev`, password: 'desk-shots-2026' };

async function main() {
  await api('/auth/register', { method: 'POST', body: user });
  const tokens = await api('/auth/login', { method: 'POST', body: { email: user.email, password: user.password } });
  const game = await api('/games', {
    method: 'POST', token: tokens.accessToken,
    body: {
      mode: 'local', playerColor: 'white', timeControl: { type: 'rapid', initialMs: 600000, incrementMs: 0 },
      enableUndo: true, confirmMoves: false, showLegalMoves: true, realTimeAnalysis: false,
    },
  });
  await api('/users/me/preferences', { method: 'PATCH', token: tokens.accessToken, body: { boardMode: '3d', boardStyle3d: LOOK } });

  const browser = await chromium.launch({ args: ['--use-angle=d3d11', '--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'] });
  const ctx = await browser.newContext({ viewport: { width: 1529, height: 1029 }, locale: 'fr-FR' });
  await ctx.addInitScript(([t]) => {
    localStorage.setItem('rex_access_token', t.accessToken);
    localStorage.setItem('rex_refresh_token', t.refreshToken);
    localStorage.setItem('rex_token_expires_at', String(Date.now() + 3600e3));
  }, [tokens]);
  const page = await ctx.newPage();
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) console.log('[page]', m.text().slice(0, 200)); });
  await page.goto(`${APP}/game/${game.id}`);
  await page.waitForSelector('app-chess-board-3d canvas', { timeout: 30000 });
  const scene = () => page.evaluate(() => window.ng.getComponent(document.querySelector('app-chess-board-3d')).scene);
  // the board is drawn once the shaders, the pieces, the room and the wood are in
  await page.waitForFunction(() => {
    const s = window.ng?.getComponent(document.querySelector('app-chess-board-3d'))?.scene;
    return s && s.intro === false && s.deskReady === true && s.compiling === false;
  }, null, { timeout: 60000 });
  await sleep(1500);

  const shot = async (name) => {
    if (ONLY && !name.includes(ONLY)) return;
    await sleep(900); // the camera eases to its goal
    await page.screenshot({ path: path.join(OUT, `${name}.png`) });
    console.log('wrote', name);
  };
  const view = (p) => page.evaluate((p) => window.ng.getComponent(document.querySelector('app-chess-board-3d')).setView(p), p);
  const state = () => page.evaluate(() => {
    const s = window.ng.getComponent(document.querySelector('app-chess-board-3d')).scene;
    return { view: s.deskView, goal: s.deskGoal, top: s.topTarget, cam: s.camera.position.toArray().map((v) => +v.toFixed(2)) };
  });

  await shot('01-first-view');
  for (const p of ['low', 'side', 'overhead', 'player']) { await view(p); await shot(`02-preset-${p}`); }

  // real drags on the empty desk (left of the board): turn, rise, wheel, pan
  const mouse = page.mouse;
  await mouse.move(1380, 700); await mouse.down(); await mouse.move(1180, 700, { steps: 12 }); await mouse.up();
  console.log('after drag left', JSON.stringify(await state()));
  await shot('03-drag-left');
  await mouse.move(1380, 800); await mouse.down(); await mouse.move(1380, 700, { steps: 12 }); await mouse.up();
  await shot('04-drag-up-lower');
  await mouse.move(760, 600);
  for (let i = 0; i < 6; i++) { await mouse.wheel(0, -120); await sleep(60); }
  await shot('05-wheel-in');
  await mouse.move(1380, 700); await mouse.down({ button: 'right' }); await mouse.move(1280, 640, { steps: 10 }); await mouse.up({ button: 'right' });
  await shot('06-pan');
  await mouse.dblclick(1400, 760);
  await shot('07-double-click-reset');
  console.log('after reset', JSON.stringify(await state()));

  // a piece dragged across the board is for moving the piece: the camera must not follow it
  const px = (row, col) => page.evaluate(([row, col]) => {
    const s = window.ng.getComponent(document.querySelector('app-chess-board-3d')).scene;
    const rect = s.canvas.getBoundingClientRect();
    const v = new s.camera.position.constructor(-3.5 + col, 0.4, -3.5 + row).applyMatrix4(s.boardGroup.matrixWorld).project(s.camera);
    return { x: rect.left + (v.x * 0.5 + 0.5) * rect.width, y: rect.top + (0.5 - v.y * 0.5) * rect.height };
  }, [row, col]);
  const before = await state();
  const from = await px(7, 6), to = await px(5, 5); // the knight on g1 to f3 (a piece in front, which nothing hides)
  await mouse.move(from.x, from.y); await mouse.down(); await mouse.move(to.x, to.y, { steps: 14 }); await mouse.up();
  await sleep(1500);
  const after = await state();
  const moved = await page.evaluate(() => {
    const s = window.ng.getComponent(document.querySelector('app-chess-board-3d')).scene;
    return { f3: s.pieces.has('5,5'), g1: s.pieces.has('7,6') };
  });
  console.log('piece drag g1-f3:', JSON.stringify(moved), '| camera goal unchanged:', JSON.stringify(before.goal) === JSON.stringify(after.goal));
  await shot('08-after-piece-drag');

  // the Vue menu
  await page.getByRole('button', { name: 'Vue', exact: true }).click();
  await shot('09-view-menu');
  await page.getByRole('menuitem', { name: 'Rasant' }).click();
  await shot('10-menu-low');
  await page.getByRole('button', { name: 'Vue', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Du dessus' }).click();
  await shot('11-menu-overhead');
  console.log('after overhead', JSON.stringify(await state()));

  await browser.close();
  return tokens;
}

try {
  await main();
} finally {
  // the throwaway account and its games
  try {
    const q = (s) => psql(s);
    q(`delete from games where white_username = '${user.username}' or black_username = '${user.username}';`);
    q(`delete from users where username = '${user.username}';`);
  } catch (e) { console.log('cleanup skipped:', String(e.message).slice(0, 160)); }
}
