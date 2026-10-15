// src/app/shared/three/scene-assets.ts
// Where the 3D scenes' files are, in a module that imports no three.js: the home page uses it to fetch them ahead of
// a game (prefetchSceneAssets), without loading the scene's code.
import { assetUrl } from '../../core/assets/asset-url';

/** The piece models, by letter (see luxe-pieces.ts) */
export const PIECE_MODEL_URLS = {
  P: assetUrl('assets/models/pawn.glb'),
  R: assetUrl('assets/models/rook.glb'),
  N: assetUrl('assets/models/knight.glb'),
  B: assetUrl('assets/models/bishop.glb'),
  Q: assetUrl('assets/models/queen.glb'),
  K: assetUrl('assets/models/king.glb'),
} as const;

/**
 * The room behind the desk, as the light it gives (its 360° picture, made into three.js's blurred reflections by
 * tools/desk-assets/bake-room-light.mjs), and the wood of the desk top (see desk-environment.ts)
 */
export const DESK_ROOM_LIGHT_URL = assetUrl('assets/desk/room-light.webp');
export const DESK_WOOD_URL = assetUrl('assets/desk/desk-wood.webp');

/** What stands on the desk (see desk-props.ts) */
export const DESK_PROP_URLS = {
  lamp: assetUrl('assets/desk/props/lamp.glb'),
  books: assetUrl('assets/desk/props/books.glb'),
  mug: assetUrl('assets/desk/props/mug.glb'),
  desk: assetUrl('assets/desk/props/desk.glb'),
  bookcase: assetUrl('assets/desk/props/bookcase.glb'),
  person: assetUrl('assets/desk/props/person.glb'),
  plant: assetUrl('assets/desk/props/plant.glb'),
} as const;

/** The photographs the classic-wood board wears on the desk (see board-looks.ts) */
export const CLASSIC_WOOD_PHOTOS = {
  light: assetUrl('assets/desk/wood-maple.webp'),
  dark: assetUrl('assets/desk/wood-walnut.webp'),
  frame: assetUrl('assets/desk/wood-frame.webp'),
} as const;

let prefetched = false;

/**
 * Fetch the files a 3D game needs into the browser's cache, quietly, so the board appears at once when the game
 * starts. Their URLs are versioned, so the cache keeps them (see nginx.conf). Once per page load; nothing on a
 * connection that asks to save data. `desk`: the desk view's room, wood and props too (wide screens).
 */
export function prefetchSceneAssets(desk: boolean): void {
  if (prefetched) return;
  const connection = (navigator as { connection?: { saveData?: boolean } }).connection;
  if (connection?.saveData) return;
  prefetched = true;
  const urls: string[] = [...Object.values(PIECE_MODEL_URLS)];
  if (desk) urls.push(DESK_ROOM_LIGHT_URL, DESK_WOOD_URL, ...Object.values(DESK_PROP_URLS), ...Object.values(CLASSIC_WOOD_PHOTOS));
  for (const url of urls) {
    // low priority: whatever the page itself asks for goes first
    fetch(url, { priority: 'low' } as RequestInit).catch(() => undefined);
  }
}
