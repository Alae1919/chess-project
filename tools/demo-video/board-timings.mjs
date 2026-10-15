// Times the 3D board in the desk view: several games, one after the other, in the same page load.
//
//   node board-timings.mjs [games] [look]     (backend on :8080, frontend on :4200 or APP=<url>; Docker dev stack)
//
// Opens Google Chrome with its window shown (a real graphics card: headless Chrome draws in software), creates a
// throwaway account and local games through the API, then plays them in turn, going back to the home page in between,
// as a player does. For each game it prints game.firstFrame (cold or warm, as the page reports it) and the steps the
// desk records (see loadDesk in luxe-board-scene.ts). The account (and its games) is deleted afterwards.
import { chromium } from 'playwright';
import { execFileSync } from 'node:child_process';

const APP = process.env.APP ?? 'http://localhost:4200';
const API = 'http://localhost:8080/api';
const GAMES = Number(process.argv[2] ?? 3);
const LOOK = process.argv[3] ?? 'classic-wood';
/** A Chrome profile to keep between runs (its shader cache with it); a fresh one when not given */
const PROFILE = process.env.PROFILE;

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
const user = { username: `timing_${id}`, email: `timing_${id}@rexchess.dev`, password: `timing-${id}-${Date.now()}` };

/** Go to `path` the way a link in the app does: the router, not a page load */
const route = (page, path) => page.evaluate((p) => {
  history.pushState({}, '', p);
  dispatchEvent(new PopStateEvent('popstate', { state: {} }));
}, path);

async function main() {
  await api('/auth/register', { method: 'POST', body: user });
  const tokens = await api('/auth/login', { method: 'POST', body: { email: user.email, password: user.password } });
  const games = [];
  for (let i = 0; i < GAMES; i++) {
    games.push(await api('/games', {
      method: 'POST', token: tokens.accessToken,
      body: {
        mode: 'local', playerColor: 'white', timeControl: { type: 'rapid', initialMs: 600000, incrementMs: 0 },
        enableUndo: true, confirmMoves: false, showLegalMoves: true, realTimeAnalysis: false,
      },
    }));
  }
  await api('/users/me/preferences', { method: 'PATCH', token: tokens.accessToken, body: { boardMode: '3d', boardStyle3d: LOOK } });

  const options = { channel: 'chrome', headless: false, viewport: { width: 1529, height: 1029 }, locale: 'fr-FR' };
  const browser = PROFILE ? null : await chromium.launch(options);
  const ctx = PROFILE ? await chromium.launchPersistentContext(PROFILE, options) : await browser.newContext(options);
  await ctx.addInitScript(([t]) => {
    localStorage.setItem('rex_access_token', t.accessToken);
    localStorage.setItem('rex_refresh_token', t.refreshToken);
    localStorage.setItem('rex_token_expires_at', String(Date.now() + 3600e3));
    // what the page reports (see TelemetryService.flush), kept for this script to read
    window.__rum = [];
    const send = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = (url, body) => {
      if (String(url).endsWith('/rum')) try { window.__rum.push(...JSON.parse(body).timings); } catch { /* not ours */ }
      return send(url, body);
    };
  }, [tokens]);
  const page = await ctx.newPage();
  page.on('console', (m) => { if (m.type() === 'error') console.log('[page]', m.text().slice(0, 200)); });

  await page.goto(`${APP}/`);
  await sleep(4000); // the home page fetches the game's files ahead (prefetchSceneAssets)
  const gpu = await page.evaluate(() => {
    const gl = document.createElement('canvas').getContext('webgl2');
    const ext = gl?.getExtension('WEBGL_debug_renderer_info');
    return ext ? gl.getParameter(ext.UNMASKED_RENDERER_WEBGL) : 'unknown';
  });
  console.log('GPU:', gpu);

  const rows = [];
  for (const [i, game] of games.entries()) {
    await page.evaluate(() => performance.clearMeasures());
    await route(page, `/game/${game.id}`);
    await page.waitForSelector('app-chess-board-3d canvas', { timeout: 30000 });
    await page.waitForFunction(() => {
      const s = window.ng?.getComponent(document.querySelector('app-chess-board-3d'))?.scene;
      return s && s.firstFrameDrawn && s.deskReady === true && s.compiling === false;
    }, null, { timeout: 60000 });
    await sleep(500);
    const r = await page.evaluate(() => {
      const c = window.ng.getComponent(document.querySelector('app-chess-board-3d'));
      c.telemetry.flush();
      const frame = window.__rum.filter((t) => t.name === 'game.firstFrame').pop();
      const desk = window.__rum.filter((t) => t.name === 'desk.assets').pop();
      const steps = Object.fromEntries(performance.getEntriesByType('measure')
        .filter((m) => m.name.startsWith('desk: ')).map((m) => [m.name.slice(6), Math.round(m.duration)]));
      return { desk: c.scene.desk, firstFrame: frame?.value, variant: frame?.variant, deskReady: desk?.value, steps };
    });
    rows.push(r);
    console.log(`game ${i + 1}: first frame ${r.firstFrame} ms (${r.variant}) · desk ready ${r.deskReady} ms · ${JSON.stringify(r.steps)}${r.desk ? '' : ' · NOT the desk view'}`);
    await route(page, '/');
    await sleep(2500);
  }
  await (browser ?? ctx).close();
  return rows;
}

try {
  await main();
} finally {
  try {
    psql(`delete from games where white_username = '${user.username}' or black_username = '${user.username}';`);
    psql(`delete from users where username = '${user.username}';`);
  } catch (e) { console.log('cleanup skipped:', String(e.message).slice(0, 160)); }
}
