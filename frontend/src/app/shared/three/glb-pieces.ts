// src/app/shared/three/glb-pieces.ts
// Chess pieces from a glTF / GLB file, in place of the lathe-built ones.
//
// The file is found by NAME, so it can come from any modelling tool:
//   - a node (group or mesh) whose name holds king | queen | rook | bishop | knight | pawn
//     ("King", "white_king", "Bishop_Black.003", "WhiteKnight" ...);
//   - the colour comes from the same name, or from an ancestor's (a "White" group holding "King", "Queen" ...):
//     white | light | ivory  /  black | dark | ebony;
//   - a set without colours, or with one colour only, is used for both sides: the other side is derived
//     from it by recolouring a copy.
// Every piece is then put on the same footing: upright, base centred on the origin and resting on y = 0,
// all scaled by ONE factor (so the set keeps its own proportions) that makes the king KING_HEIGHT tall.
import * as THREE from 'three';
import type { PieceLetter } from './luxe-pieces';

/** Where the game looks for the set. Missing or unreadable: the built-in pieces are used. */
export const PIECE_MODELS_URL = 'assets/pieces/pieces.glb';

/** Height of the king once normalised, in board squares (the built-in king stands 1.21) */
export const KING_HEIGHT = 1.4;
/** A base never reaches further than this from the centre of its square (a square is 0.5 to each side) */
export const MAX_BASE_RADIUS = 0.38;
/** The lowest part of a piece taken as its base, as a share of its height */
const BASE_SLICE = 0.07;

export interface PieceModels {
  white: Record<PieceLetter, THREE.Object3D>;
  black: Record<PieceLetter, THREE.Object3D>;
  /** One side came from the other by recolouring, because the file did not carry both */
  derived: boolean;
}

const LETTERS: PieceLetter[] = ['K', 'Q', 'R', 'B', 'N', 'P'];
const KIND: Record<string, PieceLetter> = {
  king: 'K', queen: 'Q', rook: 'R', castle: 'R', bishop: 'B', knight: 'N', horse: 'N', pawn: 'P',
};
const WHITE_WORDS = ['white', 'light', 'ivory', 'w'];
const BLACK_WORDS = ['black', 'dark', 'ebony', 'b'];

type Side = 'white' | 'black';

/** "Bishop_Black.003" -> ["bishop", "black", "003"]; "WhiteKing" -> ["white", "king"] */
function tokens(name: string): string[] {
  return name.replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
}

function sideOf(words: string[]): Side | null {
  if (words.some((w) => WHITE_WORDS.includes(w))) return 'white';
  if (words.some((w) => BLACK_WORDS.includes(w))) return 'black';
  return null;
}

/** World-space vertices of every mesh under `root` (the root's own matrix included) */
function worldVertices(root: THREE.Object3D): THREE.Vector3[] {
  root.updateWorldMatrix(true, true);
  const out: THREE.Vector3[] = [];
  root.traverse((o) => {
    const mesh = o as THREE.Mesh;
    const pos = mesh.geometry?.getAttribute('position');
    if (!mesh.isMesh || !pos) return;
    for (let i = 0; i < pos.count; i++) out.push(new THREE.Vector3().fromBufferAttribute(pos, i).applyMatrix4(mesh.matrixWorld));
  });
  return out;
}

interface Measured {
  /** The piece with its world transform baked into one group, so it no longer depends on its place in the file */
  holder: THREE.Group;
  height: number;
  /** Centre of the base, and how far the base reaches from it */
  cx: number;
  cz: number;
  minY: number;
  radius: number;
}

function measure(node: THREE.Object3D): Measured | null {
  const verts = worldVertices(node);
  if (!verts.length) return null;

  let minY = Infinity;
  let maxY = -Infinity;
  for (const v of verts) {
    minY = Math.min(minY, v.y);
    maxY = Math.max(maxY, v.y);
  }
  const height = maxY - minY;
  if (!(height > 0)) return null;

  // the base alone gives the centre: a knight's head must not drag the piece off its square
  const lim = minY + height * BASE_SLICE;
  let x0 = Infinity, x1 = -Infinity, z0 = Infinity, z1 = -Infinity;
  for (const v of verts) {
    if (v.y > lim) continue;
    x0 = Math.min(x0, v.x); x1 = Math.max(x1, v.x);
    z0 = Math.min(z0, v.z); z1 = Math.max(z1, v.z);
  }
  const cx = (x0 + x1) / 2;
  const cz = (z0 + z1) / 2;
  const radius = Math.max(x1 - x0, z1 - z0) / 2;

  const holder = new THREE.Group();
  const copy = node.clone(true);
  node.updateWorldMatrix(true, false);
  copy.matrix.copy(node.matrixWorld);
  copy.matrix.decompose(copy.position, copy.quaternion, copy.scale);
  holder.add(copy);
  return { holder, height, cx, cz, minY, radius };
}

/** Every material of an object, cloned, so that recolouring or highlighting one piece leaves the others alone */
function ownMaterials(root: THREE.Object3D): void {
  root.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    const own = (m: THREE.Material): THREE.Material => {
      const c = m.clone();
      c.userData['body'] = true;
      return c;
    };
    mesh.material = Array.isArray(mesh.material) ? mesh.material.map(own) : own(mesh.material);
  });
}

/** The other side of a set: the same models, recoloured */
function recolour(root: THREE.Object3D, to: Side): THREE.Object3D {
  const copy = root.clone(true);
  ownMaterials(copy);
  const ink = new THREE.Color(to === 'black' ? 0x1a120d : 0xf1e6cf);
  copy.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    for (const m of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
      const std = m as THREE.MeshStandardMaterial;
      if (!std.color) continue;
      std.color.lerp(ink, 0.88);
      // a white piece made from a dark one must not stay dark through its texture
      if (to === 'white') std.map = null;
      std.needsUpdate = true;
    }
  });
  return copy;
}

/**
 * Find the six pieces of each side in a loaded file and put them on the same footing.
 * Null when the file does not hold a whole set (the reason goes to the console).
 */
export function extractPieceModels(root: THREE.Object3D): PieceModels | null {
  const found: Record<'white' | 'black' | 'any', Partial<Record<PieceLetter, THREE.Object3D>>> = { white: {}, black: {}, any: {} };

  const walk = (node: THREE.Object3D, inherited: Side | null): void => {
    const words = tokens(node.name);
    const side = sideOf(words) ?? inherited;
    const kind = words.map((w) => KIND[w]).find(Boolean);
    if (kind) {
      const bucket = found[side ?? 'any'];
      bucket[kind] ??= node;       // several pawns: the first one stands for them all
      return;                      // what is inside a piece is part of it
    }
    node.children.forEach((c) => walk(c, side));
  };
  walk(root, null);

  const has = (b: Partial<Record<PieceLetter, THREE.Object3D>>): boolean => LETTERS.every((l) => !!b[l]);
  // pieces without a colour in their name fill what the coloured ones leave out
  const merged = (side: Side): Partial<Record<PieceLetter, THREE.Object3D>> => ({ ...found.any, ...found[side] });

  let derived = false;
  let white = merged('white');
  let black = merged('black');
  if (has(white) && !has(black) && !LETTERS.some((l) => found.black[l])) derived = true;
  else if (has(black) && !has(white) && !LETTERS.some((l) => found.white[l])) derived = true;
  else if (!has(white) || !has(black)) {
    const missing = (b: Partial<Record<PieceLetter, THREE.Object3D>>): string => LETTERS.filter((l) => !b[l]).join('');
    console.warn(`[pieces] the file does not hold a whole set: missing white ${missing(white) || '-'}, black ${missing(black) || '-'}. Names found: ` +
      (JSON.stringify(Object.fromEntries(Object.entries(found).map(([k, v]) => [k, Object.keys(v).join('')]))) ));
    return null;
  }

  // measure one side, then derive the other if the file had no second set
  const measureSet = (set: Partial<Record<PieceLetter, THREE.Object3D>>): Record<PieceLetter, Measured> | null => {
    const out: Partial<Record<PieceLetter, Measured>> = {};
    for (const l of LETTERS) {
      const m = measure(set[l]!);
      if (!m) {
        console.warn(`[pieces] "${set[l]!.name}" has no geometry`);
        return null;
      }
      out[l] = m;
    }
    return out as Record<PieceLetter, Measured>;
  };

  const baseSet = has(white) ? white : black;
  const measured = measureSet(baseSet);
  if (!measured) return null;

  // one factor for the whole set, so that its proportions stay its own
  let scale = KING_HEIGHT / measured.K.height;
  const widest = Math.max(...LETTERS.map((l) => measured[l].radius));
  if (widest * scale > MAX_BASE_RADIUS) scale = MAX_BASE_RADIUS / widest;

  const settle = (m: Measured): THREE.Object3D => {
    const copy = m.holder.children[0];
    copy.position.x -= m.cx;
    copy.position.z -= m.cz;
    copy.position.y -= m.minY;
    // scaling the holder, not the copy, keeps the offsets above in the file's own units
    const piece = new THREE.Group();
    piece.add(m.holder);
    piece.scale.setScalar(scale);
    return piece;
  };

  const from = {} as Record<PieceLetter, THREE.Object3D>;
  LETTERS.forEach((l) => (from[l] = settle(measured[l])));
  const baseSide: Side = has(white) ? 'white' : 'black';
  const otherSide: Side = baseSide === 'white' ? 'black' : 'white';

  const other = {} as Record<PieceLetter, THREE.Object3D>;
  if (derived) LETTERS.forEach((l) => (other[l] = recolour(from[l], otherSide)));
  else {
    const set = otherSide === 'white' ? white : black;
    const m = measureSet(set);
    if (!m) return null;
    LETTERS.forEach((l) => (other[l] = settle(m[l])));
  }

  return baseSide === 'white' ? { white: from, black: other, derived } : { white: other, black: from, derived };
}

/** One piece of the set, ready to stand on a square: its own materials, shadows on, `size` times as big (phones) */
export function buildModelPiece(models: PieceModels, letter: PieceLetter, white: boolean, size = 1): THREE.Group {
  const source = (white ? models.white : models.black)[letter];
  const body = source.clone(true);
  ownMaterials(body);

  const group = new THREE.Group();
  const shape = new THREE.Group();   // sized inside its own group: the caller scales `group` to make a piece appear or vanish
  shape.scale.setScalar(size);
  shape.add(body);
  group.add(shape);
  // a glTF model looks along +Z; knights should face the opponent (white looks to -Z)
  group.rotation.y = letter === 'N' ? (white ? Math.PI : 0) : 0;
  return group;
}

let loading: Promise<PieceModels | null> | null = null;

/** Fetch and read the set once for the whole page. Resolves to null when there is none. */
export function loadPieceModels(url: string = PIECE_MODELS_URL): Promise<PieceModels | null> {
  return (loading ??= (async () => {
    try {
      const res = await fetch(url);
      if (!res.ok) return null;
      const data = await res.arrayBuffer();
      // a dev server may answer a missing file with its home page: only a real glTF binary goes on
      if (data.byteLength < 12 || new TextDecoder().decode(data.slice(0, 4)) !== 'glTF') return null;

      const [{ GLTFLoader }, { DRACOLoader }, { MeshoptDecoder }] = await Promise.all([
        import('three/examples/jsm/loaders/GLTFLoader.js'),
        import('three/examples/jsm/loaders/DRACOLoader.js'),
        import('three/examples/jsm/libs/meshopt_decoder.module.js'),
      ]);
      const loader = new GLTFLoader();
      loader.setDRACOLoader(new DRACOLoader().setDecoderPath('assets/draco/'));
      loader.setMeshoptDecoder(MeshoptDecoder);
      const gltf = await loader.parseAsync(data, url.slice(0, url.lastIndexOf('/') + 1));
      const models = extractPieceModels(gltf.scene);
      if (models) console.info(`[pieces] using ${url}${models.derived ? ' (one side derived from the other)' : ''}`);
      return models;
    } catch (err) {
      console.warn('[pieces] could not read the piece models, using the built-in ones', err);
      return null;
    }
  })());
}
