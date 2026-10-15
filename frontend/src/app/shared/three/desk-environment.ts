// src/app/shared/three/desk-environment.ts
// What the desk view adds to the board: the desk it stands on (a real surface, so it moves right when the camera does)
// and the room behind it (a 360° picture, far enough away not to need parallax, baked into the light it gives). Both
// are pictures that arrive after the scene is built, so the scene starts with stand-ins and swaps them in.
import * as THREE from 'three';
import { loadPictureTexture } from './picture-texture';
import { DESK_ROOM_LIGHT_URL, DESK_WOOD_URL } from './scene-assets';

export { DESK_ROOM_LIGHT_URL, DESK_WOOD_URL };

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

/** Pictures already asked for, by URL: kept for the life of the page (see loadPicture) */
const pictures = new Map<string, Promise<THREE.Texture | null>>();

/**
 * One picture, or null if it cannot be had (the scene then keeps its stand-in). Fetched and decoded once per page
 * load, then shared: a scene that is disposed frees the picture's copy on the graphics card, and three uploads it
 * again for the next scene, without going back to the network or decoding it again.
 */
export function loadPicture(url: string): Promise<THREE.Texture | null> {
  let picture = pictures.get(url);
  if (!picture) {
    picture = loadPictureTexture(url).catch((err) => {
      console.warn('[desk] could not load', url, err);
      pictures.delete(url); // the next scene tries again
      return null;
    });
    pictures.set(url, picture);
  }
  return picture;
}

/**
 * The size of three's blurred reflections ("PMREM", in its cubeUV layout) for the panorama, whose 2432 px make
 * cube faces of 512 px: 3 × 512 wide, 4 × 512 high. A shader is built for one height and works with any picture
 * of that height.
 */
const ROOM_LIGHT = { width: 1536, height: 2048 };

/** The room's light as a picture, decoded once per page load (see loadRoomLight) */
let roomPicture: Promise<RoomPicture | null> | null = null;

/** The baked room light, decoded (a bitmap where the browser can: it stays decoded, and goes up to each scene as it is) */
export type RoomPicture = ImageBitmap | HTMLImageElement;

/**
 * The room as light and reflection for everything on the desk, and as the blurred room behind it: the panorama made
 * into three's blurred reflections ahead of time (tools/desk-assets/bake-room-light.mjs), instead of on the
 * graphics card for every game (twice: once for the light, once for the background). Null if it cannot be had.
 * Fetched and decoded once per page load; `roomLight` makes each scene's texture from it.
 */
export function loadRoomLight(): Promise<RoomPicture | null> {
  roomPicture ??= loadPixels(DESK_ROOM_LIGHT_URL).then((img) => {
    if (img.width !== ROOM_LIGHT.width || img.height !== ROOM_LIGHT.height) throw new Error(`room light is ${img.width}×${img.height}`);
    return img;
  }).catch((err) => {
    console.warn('[desk] could not load', DESK_ROOM_LIGHT_URL, err);
    roomPicture = null; // the next scene tries again
    return null;
  });
  return roomPicture;
}

/** A picture, decoded off the page's thread where the browser can (a bitmap), or as an <img> */
function loadPixels(url: string): Promise<RoomPicture> {
  if (typeof createImageBitmap === 'undefined') return new THREE.ImageLoader().loadAsync(url);
  // as it is in the file: no colour conversion (three's ImageBitmapLoader asks for none), no premultiplied alpha
  return new THREE.ImageBitmapLoader().setOptions({ premultiplyAlpha: 'none' }).loadAsync(url);
}

/**
 * The room's light for one scene: what three's PMREMGenerator would have made from the panorama (linear half
 * floats, so the background is tone mapped as before). The file is 8-bit sRGB: the graphics card turns it into
 * linear light as it copies it over, in one draw. Its rows run bottom up, as a texture's do: no turning over.
 */
export function roomLight(renderer: THREE.WebGLRenderer, picture: RoomPicture): THREE.WebGLRenderTarget {
  const { width, height } = ROOM_LIGHT;
  const target = cubeUvTarget(width, height);
  const source = new THREE.Texture(picture);
  source.colorSpace = THREE.SRGBColorSpace;
  source.flipY = false;
  source.minFilter = source.magFilter = THREE.NearestFilter;
  source.generateMipmaps = false;
  source.needsUpdate = true;
  const copy = new THREE.Mesh(new THREE.PlaneGeometry(2, 2), new THREE.ShaderMaterial({
    uniforms: { map: { value: source } },
    vertexShader: 'varying vec2 vUv; void main() { vUv = uv; gl_Position = vec4(position.xy, 0.0, 1.0); }',
    fragmentShader: 'uniform sampler2D map; varying vec2 vUv; void main() { gl_FragColor = texture2D(map, vUv); }',
    depthTest: false,
    depthWrite: false,
  }));
  copy.frustumCulled = false;
  const scene = new THREE.Scene();
  scene.add(copy);
  const before = renderer.getRenderTarget();
  renderer.setRenderTarget(target);
  renderer.render(scene, new THREE.Camera());
  renderer.setRenderTarget(before);
  copy.geometry.dispose();
  copy.material.dispose();
  source.dispose(); // only its copy on the graphics card: the picture stays for the scenes to come
  return target;
}

/** A render target in three's cubeUV layout, set up as PMREMGenerator sets up its own */
function cubeUvTarget(width: number, height: number): THREE.WebGLRenderTarget {
  const target = new THREE.WebGLRenderTarget(width, height, {
    magFilter: THREE.LinearFilter,
    minFilter: THREE.LinearFilter,
    generateMipmaps: false,
    type: THREE.HalfFloatType,
    format: THREE.RGBAFormat,
    colorSpace: THREE.LinearSRGBColorSpace,
    depthBuffer: false,
  });
  target.texture.mapping = THREE.CubeUVReflectionMapping;
  return target;
}

/**
 * A warm dark room as plain colour, of the same height as `loadRoomLight`'s. The materials are compiled against it
 * before the room has arrived, and a shader built for one reflection map is reused for another of the same height,
 * so the real room swaps in without compiling everything again. One column is enough: the colour is the same
 * wherever the shader looks.
 */
export function standInRoomLight(): THREE.Texture {
  const { height } = ROOM_LIGHT;
  const rgb = new THREE.Color().setRGB(54 / 255, 34 / 255, 22 / 255, THREE.SRGBColorSpace);
  const px = [rgb.r, rgb.g, rgb.b, 1].map((c) => THREE.DataUtils.toHalfFloat(c));
  const data = new Uint16Array(height * 4);
  for (let i = 0; i < height; i++) data.set(px, i * 4);
  const tex = new THREE.DataTexture(data, 1, height, THREE.RGBAFormat, THREE.HalfFloatType);
  tex.mapping = THREE.CubeUVReflectionMapping;
  tex.colorSpace = THREE.LinearSRGBColorSpace;
  tex.minFilter = tex.magFilter = THREE.LinearFilter;
  tex.needsUpdate = true;
  return tex;
}
