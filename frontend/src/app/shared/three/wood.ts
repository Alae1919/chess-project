// src/app/shared/three/wood.ts
// Procedural wood grain, drawn on a 2D canvas (seeded so it is reproducible).

/**
 * @param size   canvas edge in px
 * @param base   base fill colour
 * @param grain  grain colour prefix, e.g. 'rgba(120,76,36,' — the alpha and ')' are appended
 * @param seed   PRNG seed
 */
export function makeWood(size: number, base: string, grain: string, seed = 1): HTMLCanvasElement {
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

  // broad tonal bands, like growth rings seen side on
  for (let i = 0; i < 9; i++) {
    const y = rnd() * size;
    const h = size * (0.05 + rnd() * 0.12);
    const g = ctx.createLinearGradient(0, y - h, 0, y + h);
    g.addColorStop(0, 'rgba(0,0,0,0)');
    g.addColorStop(0.5, grain + (0.05 + rnd() * 0.08) + ')');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, y - h, size, h * 2);
  }

  // fine grain lines, gently wobbling along their length
  ctx.lineCap = 'round';
  for (let i = 0; i < 280; i++) {
    const y0 = rnd() * size;
    const amp = 1 + rnd() * 6;
    const freq = (0.4 + rnd() * 2.2) * ((Math.PI * 2) / size);
    const phase = rnd() * Math.PI * 2;
    ctx.beginPath();
    for (let x = 0; x <= size; x += 16) {
      const y = y0 + Math.sin(x * freq + phase) * amp;
      if (x === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.strokeStyle = grain + (0.04 + rnd() * 0.16) + ')';
    ctx.lineWidth = 0.5 + rnd() * 2.2;
    ctx.stroke();
  }
  return canvas;
}
