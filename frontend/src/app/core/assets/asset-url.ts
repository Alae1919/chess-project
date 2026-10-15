import { ASSET_HASHES } from './asset-hashes.generated';

/**
 * The URL of a file under src/assets, with its content hash: `assets/desk/props/lamp.glb` →
 * `assets/desk/props/lamp.glb?v=3f9c0a1b2e`. nginx lets browsers keep a versioned URL for a year
 * and asks them to check an unversioned one every time (nginx.conf), so every asset the app
 * loads goes through here. A path that is not a file under src/assets comes back unchanged.
 */
export function assetUrl(path: string): string {
  const v = ASSET_HASHES[path.startsWith('/') ? path.slice(1) : path];
  return v ? `${path}?v=${v}` : path;
}

/** A photo that comes in two widths, `<stem>-<width>.webp`: its `src` (the large one) and `srcset`. */
export function stillSources(stem: string, [small, large]: readonly [number, number]): { src: string; srcset: string } {
  const at = (w: number) => assetUrl(`${stem}-${w}.webp`);
  return { src: at(large), srcset: `${at(small)} ${small}w, ${at(large)} ${large}w` };
}
