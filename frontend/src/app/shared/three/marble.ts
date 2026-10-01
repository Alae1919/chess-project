// src/app/shared/three/marble.ts
// Procedural marble texture, drawn on a 2D canvas (seeded so it is reproducible).

/**
 * @param size  canvas edge in px
 * @param base  base fill colour
 * @param vein  vein colour prefix, e.g. 'rgba(140,108,80,' — the alpha and ')' are appended
 * @param mott  two radial mottling colours, alternated
 * @param seed  PRNG seed
 * @param k     vein intensity (1 = full, <1 = subtler / crisper for small sizes)
 */
export function makeMarble(
  size: number,
  base: string,
  vein: string,
  mott: [string, string],
  seed = 1,
  k = 1,
): HTMLCanvasElement {
  let s = seed;
  const rnd = () => {
    s = (s * 16807) % 2147483647;
    return s / 2147483647;
  };

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = base;
  ctx.fillRect(0, 0, size, size);

  for (let i = 0; i < 40; i++) {
    const gx = rnd() * size;
    const gy = rnd() * size;
    const gr = size * (0.1 + rnd() * 0.35);
    const g = ctx.createRadialGradient(gx, gy, 0, gx, gy, gr);
    g.addColorStop(0, mott[i % 2]);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, size, size);
  }

  const draw = (px: number, py: number, ang: number, len: number, w: number, a: number, depth: number): void => {
    ctx.beginPath();
    ctx.moveTo(px, py);
    for (let i = 0; i < len; i++) {
      ang += (rnd() - 0.5) * 0.55;
      px += Math.cos(ang) * size * 0.012;
      py += Math.sin(ang) * size * 0.012;
      ctx.lineTo(px, py);
      if (depth < 2 && rnd() < 0.03) draw(px, py, ang + (rnd() - 0.5) * 1.6, (len * 0.4) | 0, w * 0.6, a * 0.8, depth + 1);
    }
    ctx.strokeStyle = vein + a + ')';
    ctx.lineWidth = w;
    ctx.stroke();
  };

  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  for (let i = 0; i < Math.round(9 * k); i++) {
    const px = rnd() * size;
    const py = rnd() * size;
    const ang = -0.7 + (rnd() - 0.5) * 1.2;
    const len = 40 + rnd() * 80;
    const saved = s;
    ctx.filter = 'blur(' + 3 / k + 'px)';
    draw(px, py, ang, len, size * 0.012, 0.12, 2);
    s = saved;
    ctx.filter = k < 1 ? 'blur(1.2px)' : 'none';
    draw(px, py, ang, len, 0.6 + rnd() * 1.6, (0.55 + rnd() * 0.35) * k, 0);
  }
  return canvas;
}
