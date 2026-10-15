#!/usr/bin/env node
// Fails when a file of the production build, or the build as a whole, is larger than size-budget.json allows.
// Angular's own budgets (angular.json) cover the initial bundle; this covers everything else: the lazy chunks
// (three.js), the models, pictures, videos and fonts. Run after `npm run build -- --configuration production`.

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const dist = join(root, 'dist/rexchess/browser');
const budget = JSON.parse(readFileSync(join(root, 'size-budget.json'), 'utf8'));

const UNITS = { B: 1, kB: 1000, MB: 1000 ** 2 };
function bytes(text) {
  const m = /^([\d.]+)\s*(B|kB|MB)$/.exec(text);
  if (!m) throw new Error(`size-budget.json: "${text}" is not a size like 500kB or 1.5MB`);
  return Number(m[1]) * UNITS[m[2]];
}
const show = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(2)} MB` : `${(n / 1e3).toFixed(1)} kB`);

function* files(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) yield* files(path);
    else if (!name.endsWith('.gz')) yield path; // the precompressed copies are not what is downloaded raw
  }
}

const rules = budget.files.map((r) => ({ ...r, re: new RegExp(r.match), limit: bytes(r.max) }));
const over = [];
let total = 0;
for (const path of files(dist)) {
  const size = statSync(path).size;
  total += size;
  const rule = rules.find((r) => r.re.test(path));
  if (rule && size > rule.limit) over.push(`${relative(dist, path)}: ${show(size)}, over ${rule.max}`);
}
if (total > bytes(budget.total)) over.push(`the whole build: ${show(total)}, over ${budget.total}`);

if (over.length) {
  console.error('Over the size budget (frontend/size-budget.json):\n  ' + over.join('\n  '));
  process.exit(1);
}
console.log(`check-sizes: every file within its budget; the build is ${show(total)} (budget ${budget.total})`);
