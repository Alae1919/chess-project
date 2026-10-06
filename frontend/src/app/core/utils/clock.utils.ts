// How the game clocks read.

/** Milliseconds as MM:SS */
export function formatClock(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/** Under 30 s on the clock */
export function isLowTime(ms?: number): boolean {
  return ms !== undefined && ms > 0 && ms < 30_000;
}
