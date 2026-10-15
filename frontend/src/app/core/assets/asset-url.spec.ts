import { assetUrl, stillSources } from './asset-url';
import { ASSET_HASHES } from './asset-hashes.generated';

describe('assetUrl', () => {
  const lamp = 'assets/desk/props/lamp.glb';

  it('adds the content hash of a file under src/assets', () => {
    expect(ASSET_HASHES[lamp]).toMatch(/^[0-9a-f]{10}$/);
    expect(assetUrl(lamp)).toBe(`${lamp}?v=${ASSET_HASHES[lamp]}`);
  });

  it('keeps a leading slash, and finds the file all the same', () => {
    expect(assetUrl('/' + lamp)).toBe(`/${lamp}?v=${ASSET_HASHES[lamp]}`);
  });

  it('leaves a path it does not know unchanged', () => {
    expect(assetUrl('assets/no-such-file.webp')).toBe('assets/no-such-file.webp');
  });

  it('gives a still its two widths, each versioned', () => {
    const { src, srcset } = stillSources('assets/landing/king-start', [1280, 2560]);
    expect(src).toBe(assetUrl('assets/landing/king-start-2560.webp'));
    expect(srcset).toBe(`${assetUrl('assets/landing/king-start-1280.webp')} 1280w, ${src} 2560w`);
  });
});
