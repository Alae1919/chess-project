// src/app/shared/three/desk-environment.ts
// What the desk view adds to the board: the desk it stands on (a real surface, so it moves right when the camera does)
// and the room behind it (a 360° picture, far enough away not to need parallax). Both are pictures that arrive after
// the scene is built, so the scene starts with stand-ins and swaps them in.
import * as THREE from 'three';

export const DESK_PANO_URL = 'assets/desk/room-pano.webp';
export const DESK_WOOD_URL = 'assets/desk/desk-wood.webp';

/** The desk top, in board units (1 unit is about 4.5 cm): sides, the edge nearest the player, and the far edge behind the board */
const DESK = { halfWidth: 38, near: 42, far: -10 };
/** How deep the dark falloff along the far edge goes: the desk sinks into the shadow of whoever sits behind it */
const FALLOFF_DEPTH = 6;
/** What one copy of the wood picture covers: the picture is a plank 1.1 m wide */
const WOOD_TILE = { w: 24, h: 13.5 };

/** A rectangle on the desk's plane, in scene units */
export interface DeskArea {
  x0: number;
  x1: number;
  z0: number;
  z1: number;
}

/** Everything the desk view allocated, so the scene can free it */
export interface DeskParts {
  /** The desk top and the shadow along its far edge */
  object: THREE.Object3D;
  /** Swap in the wood picture once it has arrived */
  setWood(tex: THREE.Texture, anisotropy: number): void;
  /** Cut the wood to the flat top of the desk model, once that has arrived (until then it covers the plain desk's size) */
  fit(area: DeskArea): void;
  dispose(): void;
}

const flat = (w: number, d: number): THREE.PlaneGeometry => new THREE.PlaneGeometry(w, d).rotateX(-Math.PI / 2);

/** The desk top, lying at height `y`, in a plain dark stand-in until the wood arrives. */
export function buildDesk(y: number): DeskParts {
  let w = DESK.halfWidth * 2;
  let d = DESK.near - DESK.far;

  const stand = new THREE.DataTexture(new Uint8Array([70, 42, 28, 255]), 1, 1);
  stand.colorSpace = THREE.SRGBColorSpace;
  stand.needsUpdate = true;
  // polished walnut: a soft broad sheen from the lamp, and the room mirrored in the varnish
  const mat = new THREE.MeshPhysicalMaterial({
    map: stand, roughness: 0.5, metalness: 0, clearcoat: 0.3, clearcoatRoughness: 0.4, envMapIntensity: 0.8,
  });
  const mesh = new THREE.Mesh(flat(w, d), mat);
  mesh.position.set(0, y, (DESK.near + DESK.far) / 2);
  mesh.receiveShadow = true;

  // the far end of the desk falls into shadow: the line where it meets the room behind is not a hard one
  const c = document.createElement('canvas');
  c.width = 4;
  c.height = 128;
  const g = c.getContext('2d')!;
  const ramp = g.createLinearGradient(0, 0, 0, 128);
  ramp.addColorStop(0, 'rgba(12,6,3,0.92)'); // the far edge (the top of the picture is the far side of the plane)
  ramp.addColorStop(1, 'rgba(12,6,3,0)');
  g.fillStyle = ramp;
  g.fillRect(0, 0, 4, 128);
  const fadeTex = new THREE.CanvasTexture(c);
  const fadeMat = new THREE.MeshBasicMaterial({ map: fadeTex, transparent: true, depthWrite: false, toneMapped: false, polygonOffset: true, polygonOffsetFactor: -2 });
  const fade = new THREE.Mesh(flat(w, FALLOFF_DEPTH), fadeMat);
  fade.position.set(0, y + 0.01, DESK.far + FALLOFF_DEPTH / 2);
  fade.renderOrder = 1;

  const object = new THREE.Group();
  object.add(mesh, fade);

  let wood: THREE.Texture | null = null;
  const repeat = (): void => {
    wood?.repeat.set(w / WOOD_TILE.w, d / WOOD_TILE.h);
  };
  return {
    object,
    setWood(tex, anisotropy) {
      wood = tex;
      tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
      repeat();
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.anisotropy = anisotropy;
      tex.needsUpdate = true;
      mat.map = tex; // the stand-in had a map too: no new shader
    },
    fit(area) {
      w = area.x1 - area.x0;
      d = area.z1 - area.z0;
      mesh.geometry.dispose();
      mesh.geometry = flat(w, d);
      mesh.position.set((area.x0 + area.x1) / 2, y, (area.z0 + area.z1) / 2);
      fade.geometry.dispose();
      fade.geometry = flat(w, FALLOFF_DEPTH);
      fade.position.set(mesh.position.x, y + 0.01, area.z0 + FALLOFF_DEPTH / 2);
      repeat();
    },
    dispose() {
      mesh.geometry.dispose();
      mat.dispose();
      fade.geometry.dispose();
      fadeMat.dispose();
      fadeTex.dispose();
      stand.dispose();
      wood?.dispose();
    },
  };
}

/** One picture, or null if it cannot be had (the scene then keeps its stand-in) */
export function loadPicture(url: string): Promise<THREE.Texture | null> {
  return new Promise((resolve) => {
    new THREE.TextureLoader().load(url, resolve, undefined, (err) => {
      console.warn('[desk] could not load', url, err);
      resolve(null);
    });
  });
}

/** The room as light and reflection for everything on the desk. */
export function roomEnvironment(renderer: THREE.WebGLRenderer, pano: THREE.Texture): THREE.Texture {
  const pmrem = new THREE.PMREMGenerator(renderer);
  const env = pmrem.fromEquirectangular(pano).texture;
  pmrem.dispose();
  return env;
}

/**
 * A warm dark room as plain colour, made the same way (and the same size) as `roomEnvironment`. The materials are
 * compiled against it before the room has arrived, and a shader built for one reflection map is reused for another
 * of the same size, so the real room swaps in without compiling everything again.
 */
export function standInEnvironment(renderer: THREE.WebGLRenderer): THREE.Texture {
  // as wide as the panorama (2432 px → 512 px cube faces), as high as it takes to be a valid picture
  const px = new Uint8Array(2048 * 4);
  for (let i = 0; i < 2048; i++) px.set([54, 34, 22, 255], i * 4);
  const tex = new THREE.DataTexture(px, 2048, 1);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.mapping = THREE.EquirectangularReflectionMapping;
  tex.needsUpdate = true;
  const env = roomEnvironment(renderer, tex);
  tex.dispose();
  return env;
}
