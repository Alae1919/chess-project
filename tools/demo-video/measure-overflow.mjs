// Which phone pages need scrolling? Prints scrollHeight vs clientHeight of the page and of any open sheet.
import { chromium } from 'playwright';
const APP = 'http://localhost:4200', API = 'http://localhost:8080/api';
const api = async (r, { method = 'GET', token, body } = {}) => {
  const res = await fetch(API + r, { method, headers: { 'Content-Type': 'application/json', ...(token && { Authorization: `Bearer ${token}` }) }, body: body && JSON.stringify(body) });
  const t = await res.text(); if (!res.ok) throw new Error(r + ' ' + res.status + t); return t ? JSON.parse(t) : null;
};
const id = Math.random().toString(36).slice(2, 8);
const u = { username: `ov_${id}`, email: `ov_${id}@rexchess.dev`, password: 'overflow-check-2026' };
await api('/auth/register', { method: 'POST', body: u });
const tokens = await api('/auth/login', { method: 'POST', body: { email: u.email, password: u.password } });
const browser = await chromium.launch();
const sizes = [{ w: 390, h: 844 }, { w: 390, h: 700 }, { w: 360, h: 640 }];
const pages = [
  ['landing', '/', null, false], ['login', '/login', null, false], ['register', '/register', null, false],
  ['home', '/home', null, true], ['setup sheet', '/home', '.mode-card >> nth=0', true], ['online', '/online', null, true],
  ['online friend', '/online', 'text=Défier un ami', true], ['account', '/account', null, true],
];
for (const s of sizes) {
  console.log(`\n== ${s.w}x${s.h}`);
  for (const [name, url, click, auth] of pages) {
    const ctx = await browser.newContext({ viewport: { width: s.w, height: s.h }, isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
    if (auth) await ctx.addInitScript((t) => { localStorage.setItem('rex_access_token', t.accessToken); localStorage.setItem('rex_refresh_token', t.refreshToken); localStorage.setItem('rex_token_expires_at', String(Date.now() + t.expiresIn)); }, tokens);
    const p = await ctx.newPage();
    await p.goto(APP + url, { waitUntil: 'networkidle' }); await p.waitForTimeout(800);
    if (click) { await p.click(click); await p.waitForTimeout(700); }
    const m = await p.evaluate(() => {
      const main = document.querySelector('.app-main'); const doc = document.documentElement; const sheet = document.querySelector('.options-panel, .sheet');
      const r = (e) => e ? e.scrollHeight - e.clientHeight : null;
      return { page: r(main), sheet: sheet && sheet.offsetParent !== null ? r(sheet) : null };
    });
    const flag = (m.page > 2 || (m.sheet ?? 0) > 2) ? '  <-- scrolls' : '';
    console.log(`${name.padEnd(14)} page overflow ${m.page}px${m.sheet != null ? `, sheet overflow ${m.sheet}px` : ''}${flag}`);
    await ctx.close();
  }
}
await browser.close();
