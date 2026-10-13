// src/app/features/landing/webgl.ts
/** Can this browser draw WebGL? Checked without loading three.js, so the page decides before fetching it. */
export function canUseWebGL(): boolean {
  try {
    const c = document.createElement('canvas');
    return !!(window.WebGLRenderingContext && (c.getContext('webgl2') || c.getContext('webgl')));
  } catch {
    return false;
  }
}
