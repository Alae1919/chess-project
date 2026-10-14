// Screenshots of the phone layout, for checking it by eye.
//
//   node mobile-shots.mjs [only]     (backend on :8080, frontend on :4200)
//
// Creates a throwaway account and an AI game through the API, then photographs each
// page in an upright and a sideways phone viewport. Writes out/mobile/*.png.
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, 'out', 'mobile');
const APP = 'http://localhost:4200';
const API = 'http://localhost:8080/api';
const ONLY = process.argv[2];

const SMALL = { viewport: { width: 360, height: 640 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };
const PORTRAIT = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };
const LANDSCAPE = { viewport: { width: 844, height: 390 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true };

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

async function setup() {
  const id = Math.random().toString(36).slice(2, 8);
  const user = { username: `mob_${id}`, email: `mob_${id}@rexchess.dev`, password: 'mobile-shots-2026' };
  await api('/auth/register', { method: 'POST', body: user });
  const tokens = await api('/auth/login', { method: 'POST', body: { email: user.email, password: user.password } });
  const game = await api('/games', {
    method: 'POST', token: tokens.accessToken,
    body: {
      mode: 'ai', aiDifficulty: 5, playerColor: 'white',
      timeControl: { type: 'rapid', initialMs: 600000, incrementMs: 0 },
      enableUndo: true, confirmMoves: false, showLegalMoves: true, realTimeAnalysis: false,
    },
  });
  for (const mv of ['e2e4', 'g1f3', 'f1c4', 'd2d3']) {
    try {
      await api(`/games/${game.id}/moves`, { method: 'POST', token: tokens.accessToken, body: { move: mv } });
      await api(`/games/${game.id}/ai-move`, { method: 'POST', token: tokens.accessToken });
    } catch (e) { console.log('move skipped:', e.message.slice(0, 80)); }
  }
  // a second game, resigned, for the result sheet
  const over = await api('/games', {
    method: 'POST', token: tokens.accessToken,
    body: {
      mode: 'ai', aiDifficulty: 2, playerColor: 'white',
      timeControl: { type: 'blitz', initialMs: 300000, incrementMs: 0 },
      enableUndo: true, confirmMoves: false, showLegalMoves: true, realTimeAnalysis: false,
    },
  });
  await api(`/games/${over.id}/resign`, { method: 'POST', token: tokens.accessToken });
  return { tokens, game, over };
}

async function shoot(browser, emulation, name, url, { tokens, prefs, before } = {}) {
  if (ONLY && !name.includes(ONLY)) return;
  // the account's copy of the board preferences wins over the browser's
  if (prefs) {
    await api('/users/me/preferences', {
      method: 'PATCH', token: tokens.accessToken,
      body: { boardMode: prefs.mode, boardStyle2d: prefs.style2d, boardStyle3d: prefs.style3d },
    });
  }
  const ctx = await browser.newContext({ ...emulation, locale: 'fr-FR' });
  await ctx.addInitScript(([t, p]) => {
    if (t) {
      localStorage.setItem('rex_access_token', t.accessToken);
      localStorage.setItem('rex_refresh_token', t.refreshToken);
      localStorage.setItem('rex_token_expires_at', String(Date.now() + t.expiresIn));
    }
    if (p) localStorage.setItem('rex_board_prefs', JSON.stringify(p));
  }, [tokens, prefs]);
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.log(`[${name}] page error:`, e.message));
  page.on('console', (m) => { if (m.type() === 'error') console.log(`[${name}] console error:`, m.text().slice(0, 200)); });
  await page.goto(APP + url, { waitUntil: 'networkidle' });
  await sleep(900);
  if (before) { await before(page); await sleep(700); }
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log('shot', name);
  await ctx.close();
}

fs.mkdirSync(OUT, { recursive: true });
const { tokens, game, over } = await setup();
const flat = { mode: '2d', style2d: 'luxe', style3d: 'marble-gold' };
const deep = { mode: '3d', style2d: 'luxe', style3d: 'marble-gold' };
const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });

await shoot(browser, PORTRAIT, 'p-landing', '/', {});
await shoot(browser, PORTRAIT, 'p-home', '/home', { tokens });
await shoot(browser, PORTRAIT, 'p-home-resume', `/game/${game.id}`, { tokens, prefs: flat, before: async (p) => { await sleep(800); await p.click('.mbar__btn'); await sleep(1500); } });
await shoot(browser, PORTRAIT, 'p-home-saved', '/home?mode=saved', { tokens });
await shoot(browser, PORTRAIT, 'p-setup', '/home', { tokens, before: (p) => p.click('text=Contre l\'IA') });
await shoot(browser, PORTRAIT, 'p-online', '/online', { tokens });
await shoot(browser, PORTRAIT, 'p-online-search', '/online', { tokens, before: (p) => p.click('text=Trouver un adversaire') });
await shoot(browser, PORTRAIT, 'p-online-friend', '/online', { tokens, before: (p) => p.click('text=Défier un ami') });
await shoot(browser, PORTRAIT, 'p-account-prefs', '/account', { tokens, before: (p) => p.click('.phone-tabs >> text=Réglages') });
await shoot(browser, PORTRAIT, 'p-account', '/account', { tokens });
await shoot(browser, PORTRAIT, 'p-login', '/login', {});
await shoot(browser, PORTRAIT, 'p-game-2d', `/game/${game.id}`, { tokens, prefs: flat });
await shoot(browser, PORTRAIT, 'p-game-3d', `/game/${game.id}`, { tokens, prefs: deep });
await shoot(browser, PORTRAIT, 'p-game-more', `/game/${game.id}`, { tokens, prefs: flat, before: (p) => p.click('.m-dock >> text=Plus') });
await shoot(browser, PORTRAIT, 'p-game-notation', `/game/${game.id}`, { tokens, prefs: flat, before: (p) => p.click('.m-moves') });
await shoot(browser, PORTRAIT, 'p-game-over', `/game/${over.id}`, { tokens, prefs: flat });
await shoot(browser, LANDSCAPE, 'l-game-2d', `/game/${game.id}`, { tokens, prefs: flat });
await shoot(browser, LANDSCAPE, 'l-game-3d', `/game/${game.id}`, { tokens, prefs: deep });
await shoot(browser, { viewport: { width: 1440, height: 900 } }, 'd-game-2d', `/game/${game.id}`, { tokens, prefs: flat });
await shoot(browser, { viewport: { width: 1440, height: 900 } }, 'd-home', '/home', { tokens });

await shoot(browser, SMALL, 's-home', '/home', { tokens });
await shoot(browser, SMALL, 's-home-saved', '/home?mode=saved', { tokens });
await shoot(browser, SMALL, 's-setup', '/home', { tokens, before: (p) => p.click('.mode-card >> nth=0') });
await shoot(browser, SMALL, 's-online', '/online', { tokens });
await shoot(browser, SMALL, 's-register', '/register', {});
await shoot(browser, SMALL, 's-account', '/account', { tokens });
await shoot(browser, SMALL, 's-landing', '/', {});
await shoot(browser, SMALL, 's-game', `/game/${game.id}`, { tokens, prefs: flat });
await browser.close();
