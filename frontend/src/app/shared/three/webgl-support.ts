// src/app/shared/three/webgl-support.ts
/**
 * Can this browser draw WebGL? Checked without loading three.js, so a page decides before fetching it: this file
 * imports nothing, and must stay that way (the account page and the 2D board use it).
 */
export function canUseWebGL(): boolean {
  try {
    const c = document.createElement('canvas');
    return !!(window.WebGLRenderingContext && (c.getContext('webgl2') || c.getContext('webgl')));
  } catch {
    return false;
  }
}
