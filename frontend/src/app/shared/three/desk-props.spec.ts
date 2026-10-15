import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DESK_PROPS, disposeProp, loadDeskProps, standOnBase } from './desk-props';

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
    // versioned (see assetUrl): the hash is there only for a file that exists
    DESK_PROPS.forEach((p) => expect(p.url).toMatch(/^assets\/desk\/props\/.+\.glb\?v=[0-9a-f]{10}$/));
  });
});

describe('loading the props', () => {
  it('fetches each model once per page load, and gives every scene copies of its own that share its geometry', async () => {
    const load = spyOn(GLTFLoader.prototype, 'loadAsync').and.callFake(async () => ({ scene: model() } as any));
    const first = await loadDeskProps();
    const second = await loadDeskProps();

    expect(load).toHaveBeenCalledTimes(DESK_PROPS.length);
    expect(first.length).toBe(DESK_PROPS.length);
    expect(second[0].object).not.toBe(first[0].object);
    const mesh = (o: THREE.Object3D) => { let m: THREE.Mesh | undefined; o.traverse((c) => { if ((c as THREE.Mesh).isMesh) m ??= c as THREE.Mesh; }); return m!; };
    expect(mesh(second[0].object).geometry).toBe(mesh(first[0].object).geometry);
  });
});
