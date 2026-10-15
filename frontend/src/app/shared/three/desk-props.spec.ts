import * as THREE from 'three';
import { DESK_PROPS, disposeProp, flatTop, standOnBase } from './desk-props';

/** A model as a file would give it: any size, any place, any units */
const model = (): THREE.Object3D => {
  const geo = new THREE.BoxGeometry(22, 54, 20);
  geo.translate(140, 112, -137); // far from the origin, as the lamp file is
  const mat = new THREE.MeshPhysicalMaterial({ transmission: 1 });
  const root = new THREE.Group();
  root.add(new THREE.Mesh(geo, mat));
  return root;
};

describe('standing a model on the desk', () => {
  it('sizes it by its height, stands it on its base and puts the middle of its footprint at the origin', () => {
    const { object, height } = standOnBase(model(), { size: { height: 10 }, turn: 0 });
    const box = new THREE.Box3().setFromObject(object);
    expect(height).toBeCloseTo(10, 6);
    expect(box.min.y).toBeCloseTo(0, 6);
    expect(box.max.y).toBeCloseTo(10, 6);
    const centre = box.getCenter(new THREE.Vector3());
    expect(centre.x).toBeCloseTo(0, 6);
    expect(centre.z).toBeCloseTo(0, 6);
  });

  it('sizes a low spread of things by the longer side of its footprint', () => {
    const { object } = standOnBase(model(), { size: { width: 7 }, turn: 0 });
    const size = new THREE.Box3().setFromObject(object).getSize(new THREE.Vector3());
    expect(Math.max(size.x, size.z)).toBeCloseTo(7, 6);
  });

  it('turns it about its own middle, which stays where it was', () => {
    const { object } = standOnBase(model(), { size: { height: 10 }, turn: 1.2 });
    expect(object.rotation.y).toBeCloseTo(1.2, 9);
    const centre = new THREE.Box3().setFromObject(object).getCenter(new THREE.Vector3());
    expect(Math.hypot(centre.x, centre.z)).toBeLessThan(1e-6);
  });

  describe('a model with a cord lying out along the desk', () => {
    /** A lamp: a column 2 wide and 10 tall around x = 5, and a cord 12 long and 0.3 high leaving its foot towards +x */
    const lamp = (): THREE.Object3D => {
      const body = new THREE.BoxGeometry(2, 10, 2);
      body.translate(5, 5, 0);
      const cord = new THREE.BoxGeometry(12, 0.3, 0.3);
      cord.translate(5 + 1 + 6, 0.15, 0);
      const root = new THREE.Group();
      root.add(new THREE.Mesh(body, new THREE.MeshStandardMaterial()), new THREE.Mesh(cord, new THREE.MeshStandardMaterial()));
      return root;
    };
    const bodyCentre = (object: THREE.Object3D): THREE.Vector3 => {
      object.updateMatrixWorld(true); // a box taken from the mesh alone uses its parents' matrices as they were last computed
      const body = object.children[0].children[0] as THREE.Mesh;
      return new THREE.Box3().setFromObject(body).getCenter(new THREE.Vector3());
    };

    it('is pulled off its base by the cord when it is centred on everything', () => {
      const { object } = standOnBase(lamp(), { size: { height: 10 }, turn: 0 });
      expect(Math.abs(bodyCentre(object).x)).toBeGreaterThan(2);
    });

    it('stands its body at the origin when it is centred on the part above the cord', () => {
      const { object, height } = standOnBase(lamp(), { size: { height: 10 }, turn: 0, axis: [0.3, 1] });
      const c = bodyCentre(object);
      expect(c.x).toBeCloseTo(0, 6);
      expect(c.z).toBeCloseTo(0, 6);
      expect(height).toBeCloseTo(10, 6);
      expect(new THREE.Box3().setFromObject(object).min.y).toBeCloseTo(0, 6);
    });

    it('turns the cord about the body, which stays where it is', () => {
      const { object } = standOnBase(lamp(), { size: { height: 10 }, turn: Math.PI, axis: [0.3, 1] });
      object.updateMatrixWorld(true);
      const cord = new THREE.Box3().setFromObject(object.children[0].children[1]).getCenter(new THREE.Vector3());
      expect(cord.x).toBeLessThan(-5); // it ran to +x in the file: it runs to -x now
      expect(Math.hypot(bodyCentre(object).x, bodyCentre(object).z)).toBeLessThan(1e-6);
    });
  });

  it('centres a mug on its coaster, not on its handle, when told which band of its height to use', () => {
    const coaster = new THREE.BoxGeometry(6, 0.5, 6);
    coaster.translate(30, 0.25, 40);
    const cup = new THREE.BoxGeometry(5, 10, 5);
    cup.translate(30, 5.5, 40);
    const handle = new THREE.BoxGeometry(3, 4, 0.8);
    handle.translate(30 + 2.5 + 1.5, 6, 40);
    const root = new THREE.Group();
    [coaster, cup, handle].forEach((g) => root.add(new THREE.Mesh(g, new THREE.MeshStandardMaterial())));

    const { object } = standOnBase(root, { size: { height: 2.3 }, turn: 0, axis: [0, 0.06] });
    object.updateMatrixWorld(true);
    const base = new THREE.Box3().setFromObject(object.children[0].children[0]).getCenter(new THREE.Vector3());
    expect(base.x).toBeCloseTo(0, 6);
    expect(base.z).toBeCloseTo(0, 6);
    expect(new THREE.Box3().setFromObject(object).getSize(new THREE.Vector3()).y).toBeCloseTo(2.3, 6);
  });

  it('takes the refraction out of glass: it redraws the whole scene every frame, and the lamp does not need it', () => {
    const root = model();
    standOnBase(root, { size: { height: 10 }, turn: 0 });
    root.traverse((o) => {
      const m = (o as THREE.Mesh).material as THREE.MeshPhysicalMaterial | undefined;
      if (m) expect(m.transmission).toBe(0);
    });
  });

  it('lets it cast and take shadows', () => {
    const root = model();
    standOnBase(root, { size: { height: 10 }, turn: 0 });
    root.traverse((o) => {
      if ((o as THREE.Mesh).isMesh) {
        expect(o.castShadow).toBeTrue();
        expect(o.receiveShadow).toBeTrue();
      }
    });
  });

  it('frees geometry, material and pictures, whatever the model has', () => {
    const root = model();
    const mesh = root.children[0] as THREE.Mesh;
    const map = new THREE.Texture();
    (mesh.material as THREE.MeshPhysicalMaterial).map = map;
    const spies = [spyOn(mesh.geometry, 'dispose'), spyOn(mesh.material as THREE.Material, 'dispose'), spyOn(map, 'dispose')];
    disposeProp(root);
    spies.forEach((spy) => expect(spy).toHaveBeenCalledTimes(1));
  });

  it('lists its models by ids that are all different, each with a file under the desk assets', () => {
    const ids = DESK_PROPS.map((p) => p.id);
    expect(new Set(ids).size).toBe(ids.length);
    DESK_PROPS.forEach((p) => expect(p.url).toMatch(/^assets\/desk\/props\/.+\.glb$/));
  });

  describe('a piece of the room behind the desk', () => {
    it('neither casts nor takes the desk\'s shadows', () => {
      const root = model();
      standOnBase(root, { size: { height: 10 }, turn: 0, set: true });
      root.traverse((o) => {
        if ((o as THREE.Mesh).isMesh) {
          expect(o.castShadow).toBeFalse();
          expect(o.receiveShadow).toBeFalse();
        }
      });
    });

    it('lights itself with its own picture, in a warm tone, when the lamps do not reach it', () => {
      const root = model();
      const mesh = root.children[0] as THREE.Mesh;
      const map = new THREE.Texture();
      (mesh.material as THREE.MeshPhysicalMaterial).map = map;
      standOnBase(root, { size: { height: 10 }, turn: 0, glow: 0.5 });
      const mat = mesh.material as THREE.MeshPhysicalMaterial;
      expect(mat.emissiveMap).toBe(map);
      expect(mat.emissiveIntensity).toBe(0.5);
      expect(mat.emissive.r).toBeGreaterThan(mat.emissive.b);
    });

    it('does not glow without being asked to, or without a picture to glow with', () => {
      const root = model();
      standOnBase(root, { size: { height: 10 }, turn: 0 });
      expect(((root.children[0] as THREE.Mesh).material as THREE.MeshPhysicalMaterial).emissiveMap).toBeNull();
      const bare = model();
      standOnBase(bare, { size: { height: 10 }, turn: 0, glow: 0.5 });
      expect(((bare.children[0] as THREE.Mesh).material as THREE.MeshPhysicalMaterial).emissiveMap).toBeNull();
    });

    it('can be turned left for right, still standing on its base with its footprint centred', () => {
      /** a block with a knob on its +x side */
      const lopsided = (): THREE.Object3D => {
        const block = new THREE.BoxGeometry(10, 4, 4);
        const knob = new THREE.BoxGeometry(1, 1, 1);
        knob.translate(5.5, 0, 0);
        const root = new THREE.Group();
        [block, knob].forEach((g) => root.add(new THREE.Mesh(g, new THREE.MeshStandardMaterial())));
        return root;
      };
      const plain = standOnBase(lopsided(), { size: { width: 10 }, turn: 0 }).object;
      const mirrored = standOnBase(lopsided(), { size: { width: 10 }, turn: 0, mirror: true }).object;
      plain.updateMatrixWorld(true);
      mirrored.updateMatrixWorld(true);
      const knobX = (o: THREE.Object3D): number => new THREE.Box3().setFromObject(o.children[0].children[1]).getCenter(new THREE.Vector3()).x;
      expect(knobX(plain)).toBeGreaterThan(4);
      expect(knobX(mirrored)).toBeCloseTo(-knobX(plain), 6);
      const box = new THREE.Box3().setFromObject(mirrored);
      expect(box.min.y).toBeCloseTo(0, 6);
      expect(box.getCenter(new THREE.Vector3()).x).toBeCloseTo(0, 6);
    });
  });
});

describe('finding the flat top of a desk model', () => {
  /** A desk: a top slab, a lip a hair lower around it, two legs, and a low footstool off to the -x side that pulls the middle of the model that way */
  const desk = (): THREE.Object3D => {
    const slab = new THREE.BoxGeometry(20, 1, 10);
    slab.translate(2, 49.5, 0); // x from -8 to 12, the highest level of the model: 50
    const lip = new THREE.BoxGeometry(21, 0.4, 11);
    lip.translate(2, 49.4, 0); // its top is at 49.6: 0.8 % of the height under the slab's
    const leg = new THREE.BoxGeometry(2, 49, 8);
    const stool = new THREE.BoxGeometry(13, 1, 4);
    stool.translate(-12.5, 0.5, 0); // x from -19 to -6
    const root = new THREE.Group();
    [slab, lip, leg.clone().translate(-7, 24.5, 0), leg.translate(11, 24.5, 0), stool].forEach((g) => root.add(new THREE.Mesh(g, new THREE.MeshStandardMaterial())));
    return root;
  };

  it('is the slab\'s top face: the rounded edge a little lower and the tops of the legs are left out', () => {
    const top = flatTop(desk(), 50)!;
    expect(top.x0).toBeCloseTo(-8, 6);
    expect(top.x1).toBeCloseTo(12, 6);
    expect(top.z0).toBeCloseTo(-5, 6);
    expect(top.z1).toBeCloseTo(5, 6);
  });

  it('follows the desk when it is turned about the vertical', () => {
    // the model is 31.5 wide (the stool to the lip), so this keeps its size; its slab sits to the +x side of its middle
    const stood = standOnBase(desk(), { size: { width: 31.5 }, turn: 0 });
    const plain = flatTop(stood.object, stood.height)!;
    expect((plain.x0 + plain.x1) / 2).toBeGreaterThan(4);
    const turned = standOnBase(desk(), { size: { width: 31.5 }, turn: Math.PI });
    const top = flatTop(turned.object, turned.height)!;
    expect((top.x0 + top.x1) / 2).toBeCloseTo(-(plain.x0 + plain.x1) / 2, 5);
    expect(top.x1 - top.x0).toBeCloseTo(20, 5);
  });

  it('is nowhere on a model with no upward-facing surface at its highest level', () => {
    const cone = new THREE.ConeGeometry(2, 10, 12);
    const root = new THREE.Group();
    root.add(new THREE.Mesh(cone, new THREE.MeshStandardMaterial()));
    expect(flatTop(root, 10)).toBeNull();
  });
});
