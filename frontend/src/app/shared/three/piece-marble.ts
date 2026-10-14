// src/app/shared/three/piece-marble.ts
// Solid marble for the pieces. The veins are worked out in the fragment shader from the piece's own
// (object-space) position: the models have no UV coordinates to paint a texture on, and a piece carved
// from a block of stone has no seams to hide anyway. Each piece gets its own seed, so no two are alike.
import * as THREE from 'three';

export interface PieceMarble {
  /** the stone itself (hex colour) */
  base: number;
  /** soft cloudy patches over the base */
  cloud: number;
  /** the main, thin veins */
  vein: number;
  /** a second, rarer vein */
  vein2: number;
  /** how thick the veins are, 1 = normal (a pale stone needs more to show its veins) */
  weight?: number;
}

const VERTEX_DECL = /* glsl */ `
varying vec3 vMarblePos;
`;

const FRAGMENT_DECL = /* glsl */ `
varying vec3 vMarblePos;
uniform vec3 uMarbleBase;
uniform vec3 uMarbleCloud;
uniform vec3 uMarbleVein;
uniform vec3 uMarbleVein2;
uniform vec3 uMarbleSeed;
uniform float uMarbleWeight;

float marbleHash(vec3 p) {
  p = 17.0 * fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
  return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float marbleNoise(vec3 x) {
  vec3 i = floor(x);
  vec3 f = fract(x);
  f = f * f * (3.0 - 2.0 * f);
  return mix(
    mix(mix(marbleHash(i), marbleHash(i + vec3(1, 0, 0)), f.x),
        mix(marbleHash(i + vec3(0, 1, 0)), marbleHash(i + vec3(1, 1, 0)), f.x), f.y),
    mix(mix(marbleHash(i + vec3(0, 0, 1)), marbleHash(i + vec3(1, 0, 1)), f.x),
        mix(marbleHash(i + vec3(0, 1, 1)), marbleHash(i + vec3(1, 1, 1)), f.x), f.y),
    f.z);
}

float marbleFbm(vec3 p) {
  float sum = 0.0;
  float amp = 0.5;
  for (int i = 0; i < 5; i++) {
    sum += amp * marbleNoise(p);
    p = p * 2.03 + vec3(7.1, 3.7, 5.3);
    amp *= 0.5;
  }
  return sum;
}

vec3 marbleColor(vec3 object) {
  vec3 p = object * 2.4 + uMarbleSeed;

  // cloudy body of the stone
  vec3 col = mix(uMarbleBase, uMarbleCloud, smoothstep(0.38, 0.72, marbleFbm(p * 1.2)));

  // veins: bands of a warped sine, kept only where it is near zero, and only along stretches of the
  // stone where a second noise lets them through, so they thin out and reappear
  float band = abs(sin((p.x * 0.9 + p.y * 1.7 + p.z * 0.6) * 2.2 + marbleFbm(p * 1.7 + 3.1) * 6.0));
  float vein = (1.0 - smoothstep(0.0, 0.09 * uMarbleWeight, band)) * smoothstep(0.34, 0.62, marbleFbm(p * 0.8 + 9.0));

  float band2 = abs(sin((p.x * -1.3 + p.y * 0.8 + p.z * 1.4) * 3.0 + marbleFbm(p * 2.3 + 1.7) * 6.5));
  float vein2 = (1.0 - smoothstep(0.0, 0.05 * uMarbleWeight, band2)) * smoothstep(0.52, 0.74, marbleFbm(p * 1.1 + 4.0));

  col = mix(col, uMarbleVein, vein * 0.9);
  return mix(col, uMarbleVein2, vein2 * 0.85);
}
`;

/** Make `mat` solid marble. Its own colour must be white: the stone colours replace it. */
export function applyPieceMarble(mat: THREE.MeshPhysicalMaterial, stone: PieceMarble, seed: number): void {
  const uniforms = {
    uMarbleBase: { value: new THREE.Color(stone.base) },
    uMarbleCloud: { value: new THREE.Color(stone.cloud) },
    uMarbleVein: { value: new THREE.Color(stone.vein) },
    uMarbleVein2: { value: new THREE.Color(stone.vein2) },
    uMarbleWeight: { value: stone.weight ?? 1 },
    // a different window into the (endless) stone for each piece
    uMarbleSeed: { value: new THREE.Vector3(((seed * 7.31) % 13) + 1, ((seed * 3.17) % 11) + 1, ((seed * 5.53) % 9) + 1) },
  };
  mat.onBeforeCompile = (shader) => {
    Object.assign(shader.uniforms, uniforms);
    shader.vertexShader = shader.vertexShader
      .replace('#include <common>', `#include <common>\n${VERTEX_DECL}`)
      .replace('#include <begin_vertex>', '#include <begin_vertex>\nvMarblePos = position;');
    shader.fragmentShader = shader.fragmentShader
      .replace('#include <common>', `#include <common>\n${FRAGMENT_DECL}`)
      .replace('#include <color_fragment>', '#include <color_fragment>\ndiffuseColor.rgb *= marbleColor(vMarblePos);');
  };
  // every piece's material shares one compiled program (only the uniforms differ)
  mat.customProgramCacheKey = () => 'piece-marble';
}
