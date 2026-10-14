import * as THREE from 'three';
import { buildModelPiece, extractPieceModels, KING_HEIGHT, MAX_BASE_RADIUS } from './glb-pieces';
import type { PieceLetter } from './luxe-pieces';

const NAMES: Record<PieceLetter, string> = { K: 'King', Q: 'Queen', R: 'Rook', B: 'Bishop', N: 'Knight', P: 'Pawn' };
const HEIGHT: Record<PieceLetter, number> = { K: 10, Q: 9, R: 6, B: 8, N: 7, P: 5 };
const LETTERS = Object.keys(NAMES) as PieceLetter[];

/** A stand-in piece: a cylinder resting on y = 0, its base centred on (x, z) */
function stand(name: string, letter: PieceLetter, opts: { radius?: number; x?: number; z?: number; colour?: number; lift?: number } = {}): THREE.Mesh {
  const h = HEIGHT[letter];
  const geo = new THREE.CylinderGeometry(opts.radius ?? 2, opts.radius ?? 2, h, 12);
  geo.translate(0, h / 2, 0);
  const mesh = new THREE.Mesh(geo, new THREE.MeshStandardMaterial({ color: opts.colour ?? 0x808080 }));
  mesh.name = name;
  mesh.position.set(opts.x ?? 0, opts.lift ?? 0, opts.z ?? 0);
  return mesh;
}

function set(prefix: (l: PieceLetter) => string, opts: Parameters<typeof stand>[2] = {}, skip: PieceLetter[] = []): THREE.Object3D[] {
  return LETTERS.filter((l) => !skip.includes(l)).map((l, i) => stand(prefix(l), l, { ...opts, x: i * 5 }));
}

function sceneOf(...nodes: THREE.Object3D[]): THREE.Group {
  const root = new THREE.Group();
  nodes.forEach((n) => root.add(n));
  return root;
}

function box(o: THREE.Object3D): THREE.Box3 {
  return new THREE.Box3().setFromObject(o);
}

describe('glb-pieces', () => {
  describe('extractPieceModels', () => {
    it('reads a file that holds both colours, named on the piece itself', () => {
      const root = sceneOf(...set((l) => `White_${NAMES[l]}`), ...set((l) => `Black_${NAMES[l]}`));
      const models = extractPieceModels(root);
      expect(models).not.toBeNull();
      expect(models!.derived).toBeFalse();
      LETTERS.forEach((l) => {
        expect(models!.white[l]).toBeDefined();
        expect(models!.black[l]).toBeDefined();
      });
    });

    it('takes the colour from a parent group, and survives the loader dropping the dot of "King.001"', () => {
      const white = new THREE.Group();
      white.name = 'White';
      set((l) => `${NAMES[l]}001`).forEach((n) => white.add(n));
      const black = new THREE.Group();
      black.name = 'BlackPieces';
      set((l) => `${NAMES[l]}002`).forEach((n) => black.add(n));
      const models = extractPieceModels(sceneOf(white, black));
      expect(models).not.toBeNull();
      expect(models!.derived).toBeFalse();
    });

    it('derives black from a single set that carries no colour, and keeps white as it is', () => {
      const models = extractPieceModels(sceneOf(...set((l) => NAMES[l].toLowerCase(), { colour: 0xeeeeee })));
      expect(models).not.toBeNull();
      expect(models!.derived).toBeTrue();

      const colour = (o: THREE.Object3D): THREE.Color => {
        let c: THREE.Color | null = null;
        o.traverse((n) => {
          const m = (n as THREE.Mesh).material as THREE.MeshStandardMaterial | undefined;
          if (m?.color && !c) c = m.color;
        });
        return c!;
      };
      const white = colour(buildModelPiece(models!, 'K', true));
      const black = colour(buildModelPiece(models!, 'K', false));
      expect(black.r + black.g + black.b).toBeLessThan((white.r + white.g + white.b) * 0.25);
    });

    it('derives the other side when only one colour is named', () => {
      const models = extractPieceModels(sceneOf(...set((l) => `ebony_${NAMES[l]}`)));
      expect(models).not.toBeNull();
      expect(models!.derived).toBeTrue();
    });

    it('gives up, and says why, when a piece is missing', () => {
      const warn = spyOn(console, 'warn');
      const root = sceneOf(...set((l) => `White_${NAMES[l]}`, {}, ['Q']), ...set((l) => `Black_${NAMES[l]}`));
      expect(extractPieceModels(root)).toBeNull();
      expect(warn).toHaveBeenCalled();
      expect(String(warn.calls.mostRecent().args[0])).toContain('missing white Q');
    });

    it('ignores a file with nothing that looks like a piece', () => {
      spyOn(console, 'warn');
      expect(extractPieceModels(sceneOf(stand('Cube', 'K'), stand('Plane', 'P')))).toBeNull();
    });

    it('puts every piece upright on y = 0 with its base on the origin, whatever the file did with it', () => {
      // pieces far from the origin and floating, as modelling tools leave them
      const nodes = LETTERS.map((l, i) => stand(`White_${NAMES[l]}`, l, { x: 40 + i * 7, z: -25, lift: 3 }));
      const models = extractPieceModels(sceneOf(...nodes))!;
      LETTERS.forEach((l) => {
        const b = box(models.white[l]);
        expect(b.min.y).toBeCloseTo(0, 4);
        expect((b.min.x + b.max.x) / 2).toBeCloseTo(0, 4);
        expect((b.min.z + b.max.z) / 2).toBeCloseTo(0, 4);
      });
    });

    it('scales the whole set by one factor so that the king is KING_HEIGHT tall and the proportions stay the file\'s own', () => {
      const models = extractPieceModels(sceneOf(...set((l) => `White_${NAMES[l]}`, { radius: 0.5 })))!;
      const king = box(models.white.K).getSize(new THREE.Vector3()).y;
      const pawn = box(models.white.P).getSize(new THREE.Vector3()).y;
      expect(king).toBeCloseTo(KING_HEIGHT, 4);
      expect(pawn / king).toBeCloseTo(HEIGHT.P / HEIGHT.K, 4);
    });

    it('never lets a base spill over its square', () => {
      // a set of very squat pieces: scaling for the king alone would make them wider than a square
      const models = extractPieceModels(sceneOf(...set((l) => `White_${NAMES[l]}`, { radius: 9 })))!;
      LETTERS.forEach((l) => {
        const s = box(models.white[l]).getSize(new THREE.Vector3());
        expect(Math.max(s.x, s.z) / 2).toBeLessThanOrEqual(MAX_BASE_RADIUS + 1e-6);
      });
    });
  });

  describe('buildModelPiece', () => {
    const models = () => extractPieceModels(sceneOf(...set((l) => `White_${NAMES[l]}`), ...set((l) => `Black_${NAMES[l]}`)))!;

    it('turns the knights to face the opponent, and leaves the others alone', () => {
      const m = models();
      expect(buildModelPiece(m, 'N', true).rotation.y).toBeCloseTo(Math.PI);
      expect(buildModelPiece(m, 'N', false).rotation.y).toBe(0);
      expect(buildModelPiece(m, 'Q', true).rotation.y).toBe(0);
    });

    it('sizes the piece inside its own group, so the caller stays free to scale the group', () => {
      const m = models();
      const full = buildModelPiece(m, 'K', true);
      const small = buildModelPiece(m, 'K', true, 0.5);
      expect(small.scale.x).toBe(1);
      expect(box(small).getSize(new THREE.Vector3()).y).toBeCloseTo(box(full).getSize(new THREE.Vector3()).y * 0.5, 4);
    });

    it('gives each piece its own materials: lighting one up leaves the others as they were', () => {
      const m = models();
      const a = buildModelPiece(m, 'P', true);
      const b = buildModelPiece(m, 'P', true);
      const mat = (g: THREE.Object3D): THREE.MeshStandardMaterial => {
        let found: THREE.MeshStandardMaterial | null = null;
        g.traverse((o) => {
          if ((o as THREE.Mesh).isMesh && !found) found = (o as THREE.Mesh).material as THREE.MeshStandardMaterial;
        });
        return found!;
      };
      expect(mat(a)).not.toBe(mat(b));
      mat(a).emissive.set(0xff0000);
      expect(mat(b).emissive.getHex()).toBe(0);
      expect(mat(a).userData['body']).toBeTrue();
    });

    it('casts and receives shadows', () => {
      const piece = buildModelPiece(models(), 'R', false);
      const flags: boolean[] = [];
      piece.traverse((o) => {
        if ((o as THREE.Mesh).isMesh) flags.push(o.castShadow && o.receiveShadow);
      });
      expect(flags.length).toBeGreaterThan(0);
      expect(flags.every(Boolean)).toBeTrue();
    });
  });
});
