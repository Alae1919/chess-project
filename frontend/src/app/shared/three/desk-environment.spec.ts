import * as THREE from 'three';
import { loadRoomLight, standInRoomLight } from './desk-environment';

describe("the desk room's light", () => {
  it('is in the file at the size three gives the panorama, which the stand-in is made to match', async () => {
    const picture = await loadRoomLight();
    expect(picture).not.toBeNull();
    expect([picture!.width, picture!.height]).toEqual([1536, 2048]);
  });

  it('has a stand-in a shader built for it keeps working with: the same kind of map, of the same height', () => {
    const stand = standInRoomLight();
    expect(stand.mapping).toBe(THREE.CubeUVReflectionMapping);
    expect(stand.image.height).toBe(2048);
    // what PMREMGenerator makes, and what the real room is copied into: the background is tone mapped the same way
    expect(stand.type).toBe(THREE.HalfFloatType);
    expect(stand.colorSpace).toBe(THREE.LinearSRGBColorSpace);
    stand.dispose();
  });
});
