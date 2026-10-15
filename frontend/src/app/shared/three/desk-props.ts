// src/app/shared/three/desk-props.ts
// The things that stand on the desk: models in .glb files, stood on their base and sized to the desk's units (1 unit is
// about 4.5 cm). A model that does not load is left out; the desk is complete without it.
import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DESK_PROP_URLS } from './scene-assets';

export interface DeskProp {
  id: 'lamp' | 'books' | 'mug';
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
}

/**
 * What is on the desk, as in the reference picture: the lamp on the left, the books on the right (both behind the board).
 * The lamp's cord leaves its base towards +x in the file: turned half way round it runs off to the left, as in the
 * picture, and not across the board. The mug (10 cm and its coaster, 12 cm across) stands near the player on the right,
 * its knight turned towards the board and its handle towards the player, as in the picture.
 */
export const DESK_PROPS: DeskProp[] = [
  { id: 'lamp', url: DESK_PROP_URLS.lamp, size: { height: 10 }, at: [-8.6, -4.6], turn: Math.PI, axis: [0.3, 1], flame: 0.74 },
  { id: 'books', url: DESK_PROP_URLS.books, size: { width: 7 }, at: [8.4, -8], turn: -0.5 },
  { id: 'mug', url: DESK_PROP_URLS.mug, size: { height: 2.3 }, at: [7.2, 4.1], turn: -0.35, axis: [0, 0.06] },
];

export interface LoadedProp {
  spec: DeskProp;
  /** The model, its base at the origin: the scene puts it at `spec.at`, on the desk */
  object: THREE.Group;
  /** Its height in units, once sized */
  height: number;
}

/** The model files already asked for, by URL: each is fetched and parsed once per page load (see loadDeskProps) */
const models = new Map<string, Promise<THREE.Group>>();

function loadModel(url: string): Promise<THREE.Group> {
  let model = models.get(url);
  if (!model) {
    model = new GLTFLoader().loadAsync(url).then((gltf) => gltf.scene);
    model.catch(() => models.delete(url)); // the next scene tries again
    models.set(url, model);
  }
  return model;
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
        return { spec, ...standOnBase(model.clone(true), spec) };
      } catch (err) {
        console.warn(`[desk-props] ${spec.url} did not load, leaving it off the desk`, err);
        return null;
      }
    }),
  );
  return loaded.filter((p): p is LoadedProp => !!p);
}

/** Scale the model to its size, stand it on its base (the middle of its footprint at the origin) and let it cast and take shadows. */
export function standOnBase(model: THREE.Object3D, spec: Pick<DeskProp, 'size' | 'turn' | 'axis'>): { object: THREE.Group; height: number } {
  const box = new THREE.Box3().setFromObject(model);
  const size = box.getSize(new THREE.Vector3());
  const part = spec.axis && boxBetween(model, box.min.y + size.y * spec.axis[0], box.min.y + size.y * spec.axis[1]);
  const centre = (part ?? box).getCenter(new THREE.Vector3());
  const k = 'height' in spec.size ? spec.size.height / size.y : spec.size.width / Math.max(size.x, size.z);
  model.scale.multiplyScalar(k);
  model.position.set(-centre.x * k, -box.min.y * k, -centre.z * k);
  model.traverse((o) => {
    const mesh = o as THREE.Mesh;
    if (!mesh.isMesh) return;
    mesh.castShadow = mesh.receiveShadow = true;
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
    }
  });
  const object = new THREE.Group();
  object.add(model);
  object.rotation.y = spec.turn;
  return { object, height: size.y * k };
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
