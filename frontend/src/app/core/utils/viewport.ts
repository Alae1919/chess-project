// The breakpoints of the mobile layout, for the few decisions made in code.
// Keep in step with shared/styles/_mq.scss.

/** A phone, upright or on its side: the compact game screen */
export const COMPACT_QUERY =
  '(max-width: 768px), (max-height: 500px) and (max-width: 1000px) and (orientation: landscape)';

/** A phone held upright: the bottom tab bar and bottom sheets */
export const PHONE_QUERY = '(max-width: 768px)';

export function isPhoneViewport(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.(PHONE_QUERY).matches;
}

export function isCompactViewport(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.(COMPACT_QUERY).matches;
}

/** A short buzz on phones that have one; silently nothing elsewhere. */
export function vibrate(pattern: number | number[]): void {
  try { navigator.vibrate?.(pattern); } catch { /* not allowed here */ }
}
