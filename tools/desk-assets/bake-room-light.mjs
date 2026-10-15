// Bakes the desk room's light into a file: the panorama made into the blurred reflections three.js lights the desk
// with (a PMREM, "cubeUV" layout), as three makes it in the browser. The game then loads the result instead of
// working it out on the graphics card for every game (see roomLight in desk-environment.ts).
//
//   node tools/desk-assets/bake-room-light.mjs        (from the repo root; run it again whenever room-pano.webp changes)
//
// Needs the frontend's packages (three) and tools/demo-video's (Playwright), Google Chrome (the baking runs on its
// graphics card) and cwebp. Writes frontend/src/assets/desk/room-light.webp.
//
// The file holds the reflections as 8-bit sRGB, as precise as the panorama they come from. Its rows run bottom up, as
// WebGL reads them: the picture looks upside down, and the game uploads it as it is. WebP at quality 95: 0.6 MB
// (lossless is 2.1 MB), and the desk view off by more than 3 levels in under 1% of its pixels, all in the blurred room.
import { createRequire } from 'node:module';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const require = createRequire(path.resolve('tools/demo-video/package.json'));
const { chromium } = require('playwright');
const THREE_JS = 'frontend/node_modules/three/build/three.module.js';
const PANO = 'tools/desk-assets/room-pano.webp';
const OUT = 'frontend/src/assets/desk/room-light.webp';

const page = `<!doctype html><script type="module">
import * as THREE from '/three.module.js';
const renderer = new THREE.WebGLRenderer();
const pano = await new THREE.TextureLoader().loadAsync('/pano.webp');
// as the scene had it (see loadDesk in luxe-board-scene.ts)
pano.mapping = THREE.EquirectangularReflectionMapping;
pano.colorSpace = THREE.SRGBColorSpace;
const pmrem = new THREE.PMREMGenerator(renderer);
const target = pmrem.fromEquirectangular(pano);
const { width, height } = target;
const half = new Uint16Array(width * height * 4);
renderer.readRenderTargetPixels(target, 0, 0, width, height, half);
const toFloat = (h) => {
  const e = (h >> 10) & 31, m = h & 1023, s = h >> 15 ? -1 : 1;
  return e === 0 ? s * 2 ** -14 * (m / 1024) : e === 31 ? (m ? NaN : s * Infinity) : s * 2 ** (e - 15) * (1 + m / 1024);
};
const toSrgb = (c) => {
  c = Math.min(Math.max(c, 0), 1);
  return Math.round(255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055));
};
const canvas = document.createElement('canvas');
canvas.width = width;
canvas.height = height;
const g = canvas.getContext('2d');
const img = g.createImageData(width, height);
let over = 0;
for (let i = 0; i < width * height; i++) {
  for (let ch = 0; ch < 3; ch++) {
    const v = toFloat(half[i * 4 + ch]);
    if (v > 1) over++;
    img.data[i * 4 + ch] = toSrgb(v);
  }
  img.data[i * 4 + 3] = 255;
}
g.putImageData(img, 0, 0);
window.baked = { width, height, over, png: canvas.toDataURL('image/png') };
</script>`;

const browser = await chromium.launch({ channel: 'chrome', headless: false });
try {
  const tab = await browser.newPage();
  await tab.route('http://bake.local/**', (route) => {
    const url = new URL(route.request().url());
    if (url.pathname === '/three.module.js') return route.fulfill({ path: THREE_JS, contentType: 'text/javascript' });
    if (url.pathname === '/pano.webp') return route.fulfill({ path: PANO, contentType: 'image/webp' });
    return route.fulfill({ body: page, contentType: 'text/html' });
  });
  tab.on('console', (m) => console.log('[page]', m.text()));
  tab.on('pageerror', (e) => console.log('[page error]', e.message));
  await tab.goto('http://bake.local/');
  const baked = await tab.waitForFunction(() => window.baked, null, { timeout: 60000 }).then((h) => h.jsonValue());
  // brighter than white cannot come out of a picture: if some did, 8 bits would not be enough
  if (baked.over) throw new Error(`${baked.over} values above 1: the room's light does not fit in 8 bits`);
  const tmp = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'room-light-')), 'room-light.png');
  fs.writeFileSync(tmp, Buffer.from(baked.png.split(',')[1], 'base64'));
  execFileSync('cwebp', ['-quiet', '-q', '95', '-m', '6', '-sharp_yuv', '-metadata', 'none', tmp, '-o', OUT]);
  console.log(`wrote ${OUT}: ${baked.width}×${baked.height}, ${fs.statSync(OUT).size} bytes`);
} finally {
  await browser.close();
}
