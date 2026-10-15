// src/app/shared/three/board-looks.ts
// Materials and textures for each 3D board style.
import * as THREE from 'three';
import { Style3D } from '../../core/models';
import { makeMarble } from './marble';
import { PieceMarble } from './piece-marble';
import { makeWood } from './wood';
import { assetUrl } from '../../core/assets/asset-url';

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
  /** Pieces carved from veined stone instead of a plain colour; their `color` above must then be white */
  stone?: { white: PieceMarble; black: PieceMarble };
  /** the rings around the pieces, and the grooves in them */
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
    // polished marble pieces: a white stone veined with gold, and a black one (Portoro) the same way
    white: { color: 0xffffff, metalness: 0, roughness: 0.16, clearcoat: 1, clearcoatRoughness: 0.04, envMapIntensity: 1.1 },
    black: { color: 0xffffff, metalness: 0, roughness: 0.14, clearcoat: 1, clearcoatRoughness: 0.03, envMapIntensity: 1.5 },
    stone: {
      white: { base: 0xf3efe7, cloud: 0xddd4c5, vein: 0xb08d52, vein2: 0x8f8a82, weight: 1.6 },
      black: { base: 0x0f0e0d, cloud: 0x1f1c19, vein: 0xc8a45a, vein2: 0xe9e3d7 },
    },
    trim: 0xd6a94e,
    groove: 0x3a2610,
  },
  // maple and copper-red squares in a dark walnut frame with a thin gold inlay; ivory against rosewood
  'classic-wood': {
    frame: { kind: 'wood', base: '#3a1c0e', grain: 'rgba(12,5,2,', seed: 4, repeat: 0.3 },
    light: { kind: 'wood', base: '#e8cb97', grain: 'rgba(150,100,50,', seed: 9 },
    dark: { kind: 'wood', base: '#7c4a2c', grain: 'rgba(44,20,8,', seed: 6 },
    inlay: 0xc9a05a,
    label: 'rgba(232,188,104,.95)',
    white: { color: 0xf3ead6, metalness: 0, roughness: 0.34, clearcoat: 0.6, clearcoatRoughness: 0.2, envMapIntensity: 1.0 },
    black: { color: 0x3a1a0e, metalness: 0, roughness: 0.3, clearcoat: 0.8, clearcoatRoughness: 0.15, envMapIntensity: 1.1 },
    trim: 0xc9a05a,
    groove: 0x5a3418,
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
  frame: THREE.Texture;
  light: THREE.Texture;
  dark: THREE.Texture;
  /** Photographs of real wood rather than painted ones: each square shows a plain, unturned piece of them (see `fitSquare`) */
  photo?: boolean;
}

/** Looks that have photographs of their woods to wear on the desk, and where they are (made by tools/desk-assets) */
const PHOTO_WOODS: Partial<Record<Style3D, { light: string; dark: string; frame: string }>> = {
  'classic-wood': { light: assetUrl('assets/desk/wood-maple.webp'), dark: assetUrl('assets/desk/wood-walnut.webp'), frame: assetUrl('assets/desk/wood-frame.webp') },
};
/** How much of a photograph one square shows, in px of it: a board square is a hand's width of the wood */
const PHOTO_SQUARE_PX = 512;
const photos: Partial<Record<Style3D, LookTextures>> = {};

/** Textures are expensive to paint, so each style's set is built once and shared by every board on the page. */
const cache: Partial<Record<Style3D, LookTextures>> = {};

/** Does this look have photographs of its woods? */
export function hasPhotoWoods(style: Style3D): boolean {
  return !!PHOTO_WOODS[style];
}

/** Are they in, ready for `lookTextures(style, true)`? */
export function photoWoodsReady(style: Style3D): boolean {
  return !!photos[style];
}

/** Bring in a look's photographs (once; at once if they are in already). Resolves false if they cannot be had. */
export function loadPhotoWoods(style: Style3D): Promise<boolean> {
  const urls = PHOTO_WOODS[style];
  if (!urls) return Promise.resolve(false);
  if (photos[style]) return Promise.resolve(true);
  const load = (url: string): Promise<THREE.Texture> =>
    new Promise((resolve, reject) => {
      new THREE.TextureLoader().load(url, (t) => {
        t.colorSpace = THREE.SRGBColorSpace;
        resolve(t);
      }, undefined, reject);
    });
  return Promise.all([load(urls.frame), load(urls.light), load(urls.dark)]).then(
    ([frame, light, dark]) => {
      frame.wrapS = frame.wrapT = THREE.RepeatWrapping;
      frame.repeat.set(LOOKS_3D[style].frame.repeat, LOOKS_3D[style].frame.repeat);
      photos[style] = { frame, light, dark, photo: true };
      return true;
    },
    (err) => {
      console.warn('[board-looks] could not load the woods of', style, err);
      return false;
    },
  );
}

/**
 * Point a square's own copy of its wood at a piece of it. Painted woods are small and endless: a random quarter of
 * them, turned at random. Photographs are one plank: a square of PHOTO_SQUARE_PX px of it, anywhere, the grain
 * always the way the photograph has it (turning it would also stretch it, as the photographs are not square).
 */
export function fitSquare(t: THREE.Texture, photo: boolean): void {
  if (!photo) {
    t.repeat.set(0.25, 0.25);
    t.offset.set(Math.random() * 0.75, Math.random() * 0.75);
    // a quarter turn keeps wood grain from running the same way on every square
    t.center.set(0.5, 0.5);
    t.rotation = Math.floor(Math.random() * 4) * (Math.PI / 2);
    return;
  }
  const img = t.image as { width: number; height: number };
  const rx = Math.min(PHOTO_SQUARE_PX / img.width, 1);
  const ry = Math.min(PHOTO_SQUARE_PX / img.height, 1);
  t.repeat.set(rx, ry);
  t.offset.set(Math.random() * (1 - rx), Math.random() * (1 - ry));
}

export function lookTextures(style: Style3D, photo = false): LookTextures {
  if (photo && photos[style]) return photos[style]!;
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
