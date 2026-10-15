#!/usr/bin/env node
// Writes a .gz next to every text-like file of the build (and the 3D models), at the highest level,
// once. nginx serves it as it is (gzip_static, see nginx.conf) instead of compressing the file again
// on every request at a lower level. Run by npm after every build (postbuild in package.json).

import { readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { extname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { constants, gzipSync } from 'node:zlib';

const dist = fileURLToPath(new URL('../dist/rexchess/browser', import.meta.url));
// images and video are compressed already: gzip would only cost time
const COMPRESSIBLE = new Set(['.js', '.mjs', '.css', '.html', '.svg', '.json', '.webmanifest', '.txt', '.glb', '.ico']);
// below this, the header gzip adds and the work of unpacking are not worth it
const MIN_BYTES = 1024;

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else yield path;
  }
}

let written = 0, before = 0, after = 0;
try {
  statSync(dist);
} catch {
  console.log(`precompress: no build at ${dist}, nothing to do`);
  process.exit(0);
}
for (const path of files(dist)) {
  if (!COMPRESSIBLE.has(extname(path))) continue;
  const raw = readFileSync(path);
  if (raw.length < MIN_BYTES) continue;
  const gz = gzipSync(raw, { level: constants.Z_BEST_COMPRESSION });
  if (gz.length >= raw.length * 0.95) continue; // barely smaller: serve the file itself
  writeFileSync(path + '.gz', gz);
  written++; before += raw.length; after += gz.length;
}
const kb = (n) => `${(n / 1024).toFixed(0)} kB`;
console.log(`precompress: ${written} files, ${kb(before)} → ${kb(after)} gzipped`);
