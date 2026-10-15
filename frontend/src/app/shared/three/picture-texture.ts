// src/app/shared/three/picture-texture.ts
// A picture file as a texture, decoded once. three's TextureLoader keeps an <img>, which the browser decodes again
// each time the picture goes to a new graphics context: for the board's 2048 px woods, 0.3–0.7 s of every game after
// the first. A bitmap stays decoded, and goes up as it is.
import * as THREE from 'three';

/**
 * Safari, and Firefox before 98, do not turn a bitmap over when asked to (`imageOrientation`): there the picture
 * stays an <img>. The same test as three's GLTFLoader, which makes the same choice for the models' pictures.
 */
function bitmapsTurnOver(): boolean {
  if (typeof createImageBitmap === 'undefined') return false;
  const ua = navigator.userAgent;
  if (/^((?!chrome|android).)*safari/i.test(ua)) return false;
  const firefox = ua.match(/Firefox\/([0-9]+)\./);
  return !firefox || Number(firefox[1]) >= 98;
}

/** The picture at `url`, the way TextureLoader would give it (the right way up, nothing converted) */
export function loadPictureTexture(url: string): Promise<THREE.Texture> {
  if (!bitmapsTurnOver()) return new THREE.TextureLoader().loadAsync(url);
  return new Promise((resolve, reject) => {
    // turned over here, as WebGL ignores flipY for a bitmap; and as TextureLoader does: no premultiplied alpha, no
    // colour conversion (three's ImageBitmapLoader asks for none)
    new THREE.ImageBitmapLoader().setOptions({ imageOrientation: 'flipY', premultiplyAlpha: 'none' }).load(url, (bitmap) => {
      const tex = new THREE.Texture(bitmap);
      tex.flipY = false;
      tex.needsUpdate = true;
      resolve(tex);
    }, undefined, reject);
  });
}
