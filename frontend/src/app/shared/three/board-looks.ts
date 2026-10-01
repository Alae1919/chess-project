// src/app/shared/three/board-looks.ts
// Materials and textures for each 3D board style.
import * as THREE from 'three';
import { Style3D } from '../../core/models';
import { makeMarble } from './marble';
import { makeWood } from './wood';

type TexSpec =
  | { kind: 'marble'; base: string; vein: string; mott: [string, string]; seed: number; k?: number }
  | { kind: 'wood'; base: string; grain: string; seed: number };

export interface Look3D {
  frame: TexSpec & { repeat: number };
  light: TexSpec;
  dark: TexSpec;
  /** inlay lines on the frame */
  inlay: number;
  /** file / rank letters around the board (CSS colour) */
  label: string;
  white: THREE.MeshPhysicalMaterialParameters;
  black: THREE.MeshPhysicalMaterialParameters;
  trim: number;
  groove: number;
}

export const LOOKS_3D: Record<Style3D, Look3D> = {
  'marble-gold': {
    frame: { kind: 'marble', base: '#1c1410', vein: 'rgba(150,104,62,', mott: ['rgba(60,38,24,.4)', 'rgba(0,0,0,.5)'], seed: 3, repeat: 0.11 },
    light: { kind: 'marble', base: '#d8c9b2', vein: 'rgba(140,108,80,', mott: ['rgba(240,228,210,.5)', 'rgba(170,145,115,.3)'], seed: 11 },
    dark: { kind: 'marble', base: '#121010', vein: 'rgba(198,160,96,', mott: ['rgba(40,34,28,.5)', 'rgba(0,0,0,.5)'], seed: 5 },
    inlay: 0xd6a94e,
    label: 'rgba(214,169,78,.85)',
    white: { color: 0xf3cf88, metalness: 1, roughness: 0.16, envMapIntensity: 1.35 },
    black: { color: 0x0c0b0a, metalness: 0.15, roughness: 0.22, clearcoat: 1, clearcoatRoughness: 0.03, envMapIntensity: 1.6 },
    trim: 0xd6a94e,
    groove: 0x3a2610,
  },
  'classic-wood': {
    frame: { kind: 'wood', base: '#3b2314', grain: 'rgba(14,7,3,', seed: 4, repeat: 0.3 },
    light: { kind: 'wood', base: '#d0a46e', grain: 'rgba(125,80,38,', seed: 9 },
    dark: { kind: 'wood', base: '#7d4d2a', grain: 'rgba(38,19,8,', seed: 6 },
    inlay: 0xb8863a,
    label: 'rgba(232,207,160,.9)',
    white: { color: 0xf7ecd2, metalness: 0, roughness: 0.38, clearcoat: 0.5, clearcoatRoughness: 0.25, envMapIntensity: 0.9 },
    black: { color: 0x2b170b, metalness: 0, roughness: 0.34, clearcoat: 0.6, clearcoatRoughness: 0.2, envMapIntensity: 0.9 },
    trim: 0xb8863a,
    groove: 0x4a2e14,
  },
  'ebony-ivory': {
    frame: { kind: 'marble', base: '#0f0c0a', vein: 'rgba(110,92,70,', mott: ['rgba(40,32,26,.4)', 'rgba(0,0,0,.5)'], seed: 13, repeat: 0.11, k: 0.5 },
    light: { kind: 'marble', base: '#c9bca3', vein: 'rgba(140,122,96,', mott: ['rgba(235,226,208,.5)', 'rgba(170,152,124,.25)'], seed: 21, k: 0.6 },
    dark: { kind: 'marble', base: '#16120f', vein: 'rgba(80,68,56,', mott: ['rgba(36,29,23,.5)', 'rgba(0,0,0,.5)'], seed: 8, k: 0.5 },
    inlay: 0xc9ced6,
    label: 'rgba(201,206,214,.9)',
    white: { color: 0xf8f3e4, metalness: 0, roughness: 0.25, clearcoat: 1, clearcoatRoughness: 0.1, envMapIntensity: 1.0 },
    black: { color: 0x0a0908, metalness: 0.05, roughness: 0.18, clearcoat: 1, clearcoatRoughness: 0.05, envMapIntensity: 1.5 },
    trim: 0xcfd4dc,
    groove: 0x555a60,
  },
};

export interface LookTextures {
  frame: THREE.CanvasTexture;
  light: THREE.CanvasTexture;
  dark: THREE.CanvasTexture;
}

/** Textures are expensive to paint, so each style's set is built once and shared by every board on the page. */
const cache: Partial<Record<Style3D, LookTextures>> = {};

export function lookTextures(style: Style3D): LookTextures {
  const hit = cache[style];
  if (hit) return hit;

  const look = LOOKS_3D[style];
  const paint = (spec: TexSpec): THREE.CanvasTexture => {
    const canvas = spec.kind === 'marble'
      ? makeMarble(1024, spec.base, spec.vein, spec.mott, spec.seed, spec.k)
      : makeWood(1024, spec.base, spec.grain, spec.seed);
    const t = new THREE.CanvasTexture(canvas);
    t.colorSpace = THREE.SRGBColorSpace;
    return t;
  };
  const frame = paint(look.frame);
  frame.wrapS = frame.wrapT = THREE.RepeatWrapping;
  frame.repeat.set(look.frame.repeat, look.frame.repeat);
  return (cache[style] = { frame, light: paint(look.light), dark: paint(look.dark) });
}
