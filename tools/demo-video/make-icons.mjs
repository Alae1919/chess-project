import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
const svg = readFileSync('../../frontend/src/assets/icon.svg', 'utf8');
const b = await chromium.launch();
for (const [size, name] of [[180, 'apple-touch-icon'], [192, 'icon-192'], [512, 'icon-512']]) {
  const p = await b.newPage({ viewport: { width: size, height: size } });
  await p.setContent(`<body style="margin:0">${svg.replace('<svg ', `<svg width="${size}" height="${size}" `)}</body>`);
  await p.screenshot({ path: `../../frontend/src/assets/${name}.png` });
}
await b.close();
