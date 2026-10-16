// The production build, under the production Content-Security-Policy, in a real Chrome: what the unit specs cannot
// see, because neither they nor `ng serve` send a CSP. Run in CI after the build (ci.yml); by hand:
//
//   cd tools/ci/smoke && npm ci && node csp-smoke.mjs [path/to/dist/browser]
//
// It serves the build with the CSP from frontend/nginx-snippets/security-headers.conf, answers the API from api.json
// (no backend), and fails if:
//   - the browser refuses anything under the CSP (the desk's props once lost their colours to a refused blob: fetch),
//     or three.js reports a picture it could not load;
//   - a link takes more than NAVIGATION_MS to show the next page (a view transition once froze every navigation 4 s);
//   - the 3D desk never draws its first frame.
import { chromium } from 'playwright-core';
import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const dist = path.resolve(process.argv[2] ?? path.join(root, 'frontend/dist/rexchess/browser'));
/** A click on a link to the next page shown: generous for a shared CI machine, far under the 4 s of a frozen one */
const NAVIGATION_MS = 1500;
/** The desk's first frame: shaders compile in software on a machine with no GPU, which takes long */
const FIRST_FRAME_MS = 90_000;

const conf = fs.readFileSync(path.join(root, 'frontend/nginx-snippets/security-headers.conf'), 'utf8');
const csp = conf.match(/Content-Security-Policy "([^"]+)"/)?.[1];
if (!csp) throw new Error('no Content-Security-Policy found in security-headers.conf');
const api = JSON.parse(fs.readFileSync(path.join(here, 'api.json'), 'utf8'));
const gameId = api.game.id;

const types = {
  '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml',
  '.webp': 'image/webp', '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon', '.glb': 'model/gltf-binary',
  '.woff2': 'font/woff2', '.mp4': 'video/mp4', '.webmanifest': 'application/manifest+json',
};

// the build, as nginx serves it: files where they are, index.html for every route, the CSP on every answer
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  let file = path.join(dist, decodeURIComponent(url.pathname));
  if (!file.startsWith(dist) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(dist, 'index.html');
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream', 'Content-Security-Policy': csp });
  fs.createReadStream(file).pipe(res);
});
await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const site = `http://127.0.0.1:${server.address().port}`;

/** The API, from api.json: who is signed in, and one AI game at its first move */
function answer(pathname) {
  if (pathname === '/api/users/me') return api.me;
  if (pathname === '/api/users/me/saved-games') return [];
  if (pathname === `/api/games/${gameId}`) return api.game;
  if (pathname === `/api/games/${gameId}/chat`) return [];
  return undefined;
}

const failures = [];
const fail = (what) => { failures.push(what); console.log(`FAIL  ${what}`); };
const ok = (what) => console.log(`ok    ${what}`);

const browser = await chromium.launch({
  channel: process.env.CHROME_CHANNEL ?? 'chrome',
  // WebGL on a machine with no GPU: Chrome's software renderer
  args: ['--ignore-gpu-blocklist', '--enable-unsafe-swiftshader'],
});
try {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await context.route('**/api/**', (route) => {
    const { pathname } = new URL(route.request().url());
    if (pathname === '/api/rum') return route.fulfill({ status: 204 });
    const body = answer(pathname);
    if (body === undefined) return route.fulfill({ status: 404, contentType: 'application/json', body: '{"status":404}' });
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
  });
  const page = await context.newPage();
  const refused = [];
  page.on('console', (m) => {
    const text = m.text();
    if (/Content Security Policy|Couldn't load texture/.test(text)) refused.push(text.slice(0, 200));
  });
  page.on('pageerror', (e) => {
    if (/Transition was aborted/.test(String(e))) fail(`a view transition timed out: ${String(e).slice(0, 120)}`);
  });

  // ── a link: the landing page's "Connexion", to the login form ──
  await page.goto(`${site}/`, { waitUntil: 'load' });
  await page.locator('a.nav__cta[href="/login"]').waitFor();
  const clicked = Date.now();
  await page.locator('a.nav__cta[href="/login"]').click();
  await page.locator('input[type="email"]').waitFor({ timeout: 10_000 });
  const navigation = Date.now() - clicked;
  if (navigation > NAVIGATION_MS) fail(`the login page took ${navigation} ms to show after the click (limit ${NAVIGATION_MS} ms)`);
  else ok(`landing → login in ${navigation} ms`);

  // ── the 3D desk, signed in (the app reads the token, it never decodes it) ──
  await page.evaluate(() => {
    localStorage.setItem('rex_access_token', 'smoke-test-token');
    localStorage.setItem('rex_refresh_token', 'smoke-test-refresh');
    localStorage.setItem('rex_token_expires_at', String(Date.now() + 3_600_000));
  });
  await page.goto(`${site}/game/${gameId}`, { waitUntil: 'load' });
  const webgl = await page.evaluate(() => !!document.createElement('canvas').getContext('webgl2'));
  if (!webgl) {
    fail('this Chrome has no WebGL 2: the 3D desk could not be checked');
  } else {
    // the board fades in once its first frame is drawn (chess-board-3d adds `drawn`)
    const drawn = await page.locator('app-chess-board-3d.drawn').waitFor({ timeout: FIRST_FRAME_MS }).then(() => true, () => false);
    if (drawn) ok('the 3D desk drew its first frame');
    else fail(`the 3D desk drew nothing in ${FIRST_FRAME_MS / 1000} s`);
    // the lamp, the books and the rest of the room come in after the first frame
    await page.waitForTimeout(5000);
  }

  if (refused.length) fail(`the browser refused ${refused.length} thing(s), the first:\n      ${[...new Set(refused)].slice(0, 3).join('\n      ')}`);
  else ok('nothing refused under the CSP, every picture loaded');
} finally {
  await browser.close();
  server.close();
}

if (failures.length) {
  console.log(`${failures.length} check(s) failed`);
  process.exit(1);
}
console.log('Smoke checks passed');
