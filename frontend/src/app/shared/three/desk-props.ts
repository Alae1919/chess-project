// src/app/shared/three/desk-props.ts
// The things that stand on the desk: models in .glb files, stood on their base and sized to the desk's units (1 unit is
// about 4.5 cm). A model that does not load is left out; the desk is complete without it.
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DESK_PROP_URLS } from './scene-assets';

export interface DeskProp {
  id: 'lamp' | 'books' | 'mug' | 'desk' | 'bookcase-left' | 'bookcase-right' | 'person' | 'plant';
  url: string;
  /** Size on the desk, in units: its height, or (for a low spread of things) the longer side of its footprint */
  size: { height: number } | { width: number };
  /** Where the middle of its base stands on the desk (x, z) */
  at: [number, number];
  /** Turn about the vertical (rad) */
  turn: number;
  /**
   * Centre it on the part between these two fractions of its height, instead of on everything. For a model with
   * something sticking out that would pull the middle away from its base: a lamp's cord lying along the desk (centre on
   * the part above the cord), a mug's handle (centre on its coaster).
   */
  axis?: [from: number, to: number];
  /** A lamp's flame, as a fraction of its height: where the light and the glow are */
  flame?: number;
  /** How far its base is above the desk top, in units (default 0, standing on the desk; negative for what stands on a floor far below it) */
  rise?: number;
  /** It is the desk itself: its flat top, not its base, is level with the desk top, and the desk's wood is laid on that top */
  hangs?: boolean;
  /** Left for right, so that two copies of a model do not look alike */
  mirror?: boolean;
  /** A piece of the room behind the desk: seen, but it neither casts nor takes the desk's shadows */
  set?: boolean;
  /** The lamps do not reach it, so it lights itself: this much of its own picture, in a warm tone, added to what it reflects */
  glow?: number;
}

/**
 * What is on the desk, as in the reference picture: the lamp on the left, the books on the right (both behind the board).
 * The lamp's cord leaves its base towards +x in the file: turned half way round it runs off to the left, as in the
 * picture, and not across the board. The mug (10 cm and its coaster, 12 cm across) stands near the player on the right,
 * its knight turned towards the board and its handle towards the player, as in the picture.
 *
 * The desk is a 2.4 m executive desk whose far edge stays where the flat desk's was (z = -10); its front, with the
 * knee hole, faces whoever sits behind it. Two bookcases stand against the wall behind that seat. The camera looks
 * down at the board, so over the desk's far edge it sees only a band of the room, from the desk's height to a little
 * above the board's: the bookcases are sunk (negative `rise`) until their shelves, not their cupboards, fill that band.
 *
 * The opponent sits behind the desk in his wing chair, chin on his hand (one model, life size: a seated man 1.2 m tall).
 * Sunk the same way, until the band frames him from the nose down, as in the reference picture: with the camera raised
 * or lowered the band moves over him, so the lower poses show his face.
 *
 * On the left of the board the plant is in the bottom corner of the picture, as in the reference, and the lamp stands
 * beside the board, behind it. Near the camera the picture is narrow, so the left edge of the desk is about 7 units
 * from the board's middle there: both lean out of the picture, the lamp's shade under the left-hand panel on a window
 * 1500 px wide, and in view on a wider one. The books are on the desk, a few units in from its far edge (at the edge
 * their back half hung in the air).
 */
export const DESK_PROPS: DeskProp[] = [
  { id: 'desk', url: DESK_PROP_URLS.desk, size: { width: 54 }, at: [0, 3.8], turn: Math.PI, hangs: true, set: true },
  { id: 'bookcase-left', url: DESK_PROP_URLS.bookcase, size: { width: 50 }, at: [-25, -36], turn: 0, rise: -27, set: true, glow: 0.45 },
  { id: 'bookcase-right', url: DESK_PROP_URLS.bookcase, size: { width: 50 }, at: [25, -36], turn: 0, rise: -27, mirror: true, set: true, glow: 0.45 },
  { id: 'lamp', url: DESK_PROP_URLS.lamp, size: { height: 6 }, at: [-7.6, -0.6], turn: Math.PI, axis: [0.3, 1], flame: 0.74 },
  { id: 'books', url: DESK_PROP_URLS.books, size: { width: 8 }, at: [8.8, -5.2], turn: -0.5 },
  { id: 'mug', url: DESK_PROP_URLS.mug, size: { height: 2.3 }, at: [7.2, 4.1], turn: -0.35, axis: [0, 0.06] },
  { id: 'person', url: DESK_PROP_URLS.person, size: { height: 26.6 }, at: [-1, -21.5], turn: 0, rise: -21, set: true, glow: 0.5 },
  { id: 'plant', url: DESK_PROP_URLS.plant, size: { height: 4.2 }, at: [-6.7, 4.4], turn: 0, glow: 0.35 },
];

/** The flat top of a desk, as a rectangle on the desk's own plane: x and z, in the frame of the prop's group */
export interface FlatTop {
  x0: number;
  x1: number;
  z0: number;
  z1: number;
}

export interface LoadedProp {
  spec: DeskProp;
  /** The model, its base at the origin: the scene puts it at `spec.at`, on the desk */
  object: THREE.Group;
  /** Its height in units, once sized */
  height: number;
  /** Only for the desk: where its flat top is (the rounded edge around it is left out) */
  top: FlatTop | null;
}

/** The model files already asked for, by URL: each is fetched and parsed once per page load (see loadDeskProps) */
const models = new Map<string, Promise<THREE.Group>>();

function loadModel(url: string): Promise<THREE.Group> {
  let model = models.get(url);
  if (!model) {
    model = new GLTFLoader().loadAsync(url).then((gltf) => gltf.scene);
    // the next scene tries again (unless the cache was emptied meanwhile and a newer load took this place)
    model.catch(() => { if (models.get(url) === model) models.delete(url); });
    models.set(url, model);
  }
  return model;
}

/** Forget the models already fetched, so that the next scene fetches them again. For specs: the cache outlives each one. */
export function forgetDeskModels(): void {
  models.clear();
}

/**
 * The props, ready to stand on the desk. Each scene gets its own copies, which share the models' geometry, materials
 * and pictures: a disposed scene frees those on the graphics card, and three uploads them again for the next one.
 */
export async function loadDeskProps(): Promise<LoadedProp[]> {
  const loaded = await Promise.all(
    DESK_PROPS.map(async (spec): Promise<LoadedProp | null> => {
      try {
        const model = await loadModel(spec.url);
        const stood = standOnBase(model.clone(true), spec);
        return { spec, ...stood, top: spec.hangs ? flatTop(stood.object, stood.height) : null };
      } catch (err) {
        console.warn(`[desk-props] ${spec.url} did not load, leaving it off the desk`, err);
        return null;
      }
    }),
  );
  return loaded.filter((p): p is LoadedProp => !!p);
}

/** Scale the model to its size, stand it on its base (the middle of its footprint at the origin) and let it cast and take shadows. */
export function standOnBase(
  model: THREE.Object3D,
  spec: Pick<DeskProp, 'size' | 'turn' | 'axis' | 'mirror' | 'set' | 'glow'>,
): { object: THREE.Group; height: number } {
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3());
  const part = spec.axis && boxBetween(model, box.min.y + size.y * spec.axis[0], box.min.y + size.y * spec.axis[1]);
  const centre = (part ?? box).getCenter(new THREE.Vector3());
  const k = 'height' in spec.size ? spec.size.height / size.y : spec.size.width / Math.max(size.x, size.z);
  const flip = spec.mirror ? -1 : 1;
  model.scale.multiplyScalar(k);
  model.scale.x *= flip;
  model.position.set(-centre.x * k * flip, -box.min.y * k, -centre.z * k);
  model.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.castShadow = mesh.receiveShadow = !spec.set;
    // Glass drawn by refraction makes three draw the whole scene a second time into a buffer, every frame: the first
    // lamp's glass alone cost two thirds of the frame rate. Its colour picture already carries the glass's transparency
    // (alphaMode BLEND), and it looks the same without the refraction.
    for (const m of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
      const glass = m as THREE.MeshPhysicalMaterial;
      if (glass.transmission > 0) {
        glass.transmission = 0;
        glass.transmissionMap = null;
        glass.needsUpdate = true;
      }
      const lit = m as THREE.MeshStandardMaterial;
      if (spec.glow && lit.map) {
        lit.emissiveMap = lit.map;
        lit.emissive.setRGB(1, 0.78, 0.55);
        lit.emissiveIntensity = spec.glow;
        lit.needsUpdate = true;
      }
    }
  });
  const object = new THREE.Group();
  object.add(model);
  object.rotation.y = spec.turn;
  return { object, height: size.y * k };
}

/**
 * Where a desk model's flat top is: the box (on the floor plane) around the upward-facing vertices at its highest level.
 * The rounded edge, a hair lower, is left out: that is the part of the model that stays in view around the desk's wood.
 * `height` is the model's height, from which the thickness of that top level is taken.
 */
export function flatTop(object: THREE.Object3D, height: number): FlatTop | null {
  object.updateWorldMatrix(true, true);
  const level = new THREE.Box3().setFromObject(object).max.y - height * 0.003;
  const top = { x0: Infinity, x1: -Infinity, z0: Infinity, z1: -Infinity };
  const p = new THREE.Vector3();
  const n = new THREE.Vector3();
  object.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    const at = mesh.geometry.getAttribute('position');
    const normals = mesh.geometry.getAttribute('normal');
    for (let i = 0; i < at.count; i++) {
      p.fromBufferAttribute(at, i).applyMatrix4(mesh.matrixWorld);
      if (p.y < level) continue;
      if (normals && n.fromBufferAttribute(normals, i).transformDirection(mesh.matrixWorld).y < 0.95) continue;
      top.x0 = Math.min(top.x0, p.x);
      top.x1 = Math.max(top.x1, p.x);
      top.z0 = Math.min(top.z0, p.z);
      top.z1 = Math.max(top.z1, p.z);
    }
  });
  return top.x1 > top.x0 && top.z1 > top.z0 ? top : null;
}

/** The box around the vertices of the model between heights `lo` and `hi` (in the model's own units, as placed in the file) */
function boxBetween(model: THREE.Object3D, lo: number, hi: number): THREE.Box3 {
  const box = new THREE.Box3();
  const p = new THREE.Vector3();
  model.updateWorldMatrix(true, true);
  model.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    const at = mesh.geometry.getAttribute('position');
    for (let i = 0; i < at.count; i++) {
      p.fromBufferAttribute(at, i).applyMatrix4(mesh.matrixWorld);
      if (p.y >= lo && p.y <= hi) box.expandByPoint(p);
    }
  });
  return box;
}

/** Free a prop's geometries, materials and the pictures they use. */
export function disposeProp(object: THREE.Object3D): void {
  object.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.geometry.dispose();
    for (const m of Array.isArray(mesh.material) ? mesh.material : [mesh.material]) {
      for (const v of Object.values(m)) if ((v as THREE.Texture | null)?.isTexture) (v as THREE.Texture).dispose();
      m.dispose();
    }
  });
}
