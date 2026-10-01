// src/app/shared/three/luxe-pieces.ts
// Staunton pieces in gold & black lacquer, built from lathe profiles. Port of the
// REXCHESS Luxe design's LuxePieces.js.
import * as THREE from 'three';
import { Look3D } from './board-looks';

export type PieceLetter = 'P' | 'R' | 'N' | 'B' | 'Q' | 'K';

type Pt = [number, number];
/** A profile is a list of points; `{ c }` marks a quadratic-bezier control point for the next segment. */
type Spec = Array<Pt | { c: Pt }>;

interface PieceDef {
  R: number;
  body: Spec;
  tail?: () => Pt[];
  trims: Pt[];
}

export interface LuxeMaterials {
  gold: THREE.MeshPhysicalMaterial;
  lacquer: THREE.MeshPhysicalMaterial;
  trim: THREE.MeshStandardMaterial;
  groove: THREE.MeshStandardMaterial;
}

interface PieceParts {
  body: THREE.BufferGeometry[];
  accent: THREE.BufferGeometry[];
  trims: THREE.BufferGeometry[];
}

const PI = Math.PI;

function isControl(s: Pt | { c: Pt }): s is { c: Pt } {
  return !Array.isArray(s);
}

/** Expand a spec (with bezier control points) into a flat polyline. */
function prof(spec: Spec, n = 10): Pt[] {
  const out: Pt[] = [];
  let i = 0;
  while (i < spec.length) {
    const s = spec[i];
    if (isControl(s)) {
      const a = out[out.length - 1];
      const c = s.c;
      const b = spec[i + 1] as Pt;
      for (let k = 1; k <= n; k++) {
        const t = k / n;
        const u = 1 - t;
        out.push([u * u * a[0] + 2 * u * t * c[0] + t * t * b[0], u * u * a[1] + 2 * u * t * c[1] + t * t * b[1]]);
      }
      i += 2;
    } else {
      out.push(s);
      i++;
    }
  }
  return out;
}

function ball(cy: number, r: number, a0: number): Pt[] {
  const p: Pt[] = [];
  for (let k = 0; k <= 14; k++) {
    const a = a0 + ((PI / 2 - a0) * k) / 14;
    p.push([Math.max(0, Math.cos(a) * r * 1.38), cy + Math.sin(a) * r]);
  }
  return p;
}

function base(R: number): Spec {
  return [
    [0, 0], [R - 0.015, 0], { c: [R, 0] }, [R, 0.015], [R, 0.035], { c: [R, 0.048] },
    [R - 0.016, 0.048], [R - 0.03, 0.05], { c: [R - 0.008, 0.064] }, [R - 0.03, 0.078], [R - 0.05, 0.082],
  ];
}

function lathe(spec: Spec): THREE.LatheGeometry {
  const g = new THREE.LatheGeometry(prof(spec).map((p) => new THREE.Vector2(p[0], p[1])), 72);
  g.computeVertexNormals();
  return g;
}

const DEF: Record<PieceLetter, PieceDef> = {
  P: {
    R: 0.29,
    body: [{ c: [0.16, 0.095] }, [0.155, 0.15], { c: [0.13, 0.26] }, [0.098, 0.355], [0.15, 0.365], { c: [0.172, 0.375] }, [0.15, 0.39], [0.095, 0.395], [0.07, 0.41]],
    tail: () => ball(0.495, 0.09, -0.9),
    trims: [[0.161, 0.3775]],
  },
  R: {
    R: 0.33,
    body: [{ c: [0.19, 0.1] }, [0.185, 0.17], [0.165, 0.42], [0.2, 0.435], { c: [0.224, 0.447] }, [0.2, 0.46], [0.17, 0.465], { c: [0.165, 0.52] }, [0.215, 0.56], [0.228, 0.575], [0.228, 0.66], [0.168, 0.66], [0.168, 0.62], [0, 0.62]],
    trims: [[0.212, 0.447], [0.229, 0.578]],
  },
  N: {
    R: 0.33,
    body: [{ c: [0.2, 0.1] }, [0.2, 0.16], [0.23, 0.172], { c: [0.252, 0.183] }, [0.23, 0.196], [0.19, 0.2], [0.17, 0.21], [0, 0.21]],
    trims: [[0.241, 0.183]],
  },
  B: {
    R: 0.31,
    body: [{ c: [0.18, 0.1] }, [0.17, 0.17], { c: [0.13, 0.37] }, [0.11, 0.5], [0.165, 0.512], { c: [0.19, 0.523] }, [0.165, 0.537], [0.125, 0.542], [0.145, 0.55], { c: [0.163, 0.558] }, [0.145, 0.567], [0.09, 0.572], { c: [0.18, 0.65] }, [0.15, 0.75], { c: [0.12, 0.81] }, [0.045, 0.85], [0.03, 0.856]],
    tail: () => ball(0.888, 0.034, -1.1),
    trims: [[0.178, 0.523], [0.154, 0.558]],
  },
  Q: {
    R: 0.35,
    body: [{ c: [0.2, 0.105] }, [0.19, 0.18], { c: [0.14, 0.42] }, [0.122, 0.6], [0.19, 0.612], { c: [0.216, 0.623] }, [0.19, 0.638], [0.15, 0.643], [0.172, 0.652], { c: [0.19, 0.66] }, [0.17, 0.67], [0.115, 0.675], { c: [0.12, 0.8] }, [0.205, 0.885], [0.21, 0.9], [0.175, 0.905], { c: [0.14, 0.93] }, [0.1, 0.955], { c: [0.085, 0.99] }, [0.035, 1.0], [0.025, 1.002]],
    tail: () => ball(1.03, 0.03, -1.2),
    trims: [[0.203, 0.623], [0.181, 0.66], [0.211, 0.893]],
  },
  K: {
    R: 0.36,
    body: [{ c: [0.21, 0.105] }, [0.2, 0.19], { c: [0.145, 0.44] }, [0.128, 0.64], [0.195, 0.652], { c: [0.222, 0.663] }, [0.195, 0.678], [0.155, 0.683], [0.178, 0.692], { c: [0.196, 0.7] }, [0.176, 0.71], [0.12, 0.715], { c: [0.125, 0.83] }, [0.2, 0.915], [0.205, 0.93], [0.17, 0.935], { c: [0.15, 0.97] }, [0.1, 0.99], { c: [0.07, 1.02] }, [0.035, 1.025], [0.035, 1.04], [0, 1.04]],
    trims: [[0.209, 0.663], [0.187, 0.7], [0.206, 0.922]],
  },
};

const cache: Partial<Record<PieceLetter, PieceParts>> = {};

const smoothstep = (a: number, b: number, x: number): number => {
  const t = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return t * t * (3 - 2 * t);
};

function geoFor(type: PieceLetter): PieceParts {
  const hit = cache[type];
  if (hit) return hit;

  const d = DEF[type];
  const spec = base(d.R).concat(d.body, d.tail ? d.tail() : []);
  const parts: PieceParts = { body: [lathe(spec)], accent: [], trims: [] };

  if (type === 'R') {
    // crenellations
    for (let i = 0; i < 6; i++) {
      const a0 = (i * PI) / 3 + 0.12;
      const a1 = a0 + PI / 3 - 0.24;
      const sh = new THREE.Shape();
      sh.absarc(0, 0, 0.228, a0, a1, false);
      sh.absarc(0, 0, 0.168, a1, a0, true);
      sh.closePath();
      const g = new THREE.ExtrudeGeometry(sh, { depth: 0.07, bevelEnabled: true, bevelThickness: 0.006, bevelSize: 0.006, bevelSegments: 2, curveSegments: 10 });
      g.rotateX(-PI / 2);
      g.translate(0, 0.66, 0);
      parts.body.push(g);
    }
  }

  if (type === 'N') {
    const v = (x: number, y: number) => new THREE.Vector2(x, y);
    const sh = new THREE.Shape();
    sh.moveTo(-0.17, 0);
    sh.lineTo(0.14, 0);
    sh.splineThru([v(0.165, 0.08), v(0.135, 0.17), v(0.155, 0.24), v(0.22, 0.305), v(0.285, 0.36), v(0.305, 0.415), v(0.29, 0.47), v(0.22, 0.505), v(0.13, 0.56), v(0.075, 0.63), v(0.045, 0.69)]);
    sh.splineThru([v(0.015, 0.635), v(-0.04, 0.6), v(-0.11, 0.54), v(-0.165, 0.44), v(-0.205, 0.3), v(-0.22, 0.16), v(-0.2, 0.06), v(-0.17, 0)]);
    const g = new THREE.ExtrudeGeometry(sh, { depth: 0.13, bevelEnabled: true, bevelThickness: 0.045, bevelSize: 0.03, bevelSegments: 6, curveSegments: 48 });
    g.translate(0, 0, -0.065);
    // sculpt the head: taper the muzzle and neck, bulge the cheek
    const pos = g.attributes['position'];
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i);
      const y = pos.getY(i);
      const z = pos.getZ(i);
      const f = 1 + 0.25 * (1 - smoothstep(0.0, 0.25, y)) - 0.42 * smoothstep(0.12, 0.3, x) * smoothstep(0.25, 0.4, y) - 0.3 * smoothstep(0.55, 0.68, y) - 0.15 * smoothstep(-0.05, -0.2, x);
      const bulge = 1 + 0.12 * Math.exp(-((x - 0.05) ** 2 + (y - 0.45) ** 2) / 0.01);
      pos.setZ(i, z * f * bulge);
    }
    g.computeVertexNormals();
    g.translate(0, 0.2, 0);
    parts.body.push(g);

    // mane ridge
    const mane = new THREE.Shape();
    mane.moveTo(-0.02, 0.6);
    mane.splineThru([v(-0.09, 0.55), v(-0.15, 0.46), v(-0.19, 0.33), v(-0.205, 0.2), v(-0.19, 0.08)]);
    mane.splineThru([v(-0.15, 0.1), v(-0.165, 0.25), v(-0.13, 0.4), v(-0.07, 0.52), v(-0.02, 0.6)]);
    const mg = new THREE.ExtrudeGeometry(mane, { depth: 0.03, bevelEnabled: true, bevelThickness: 0.012, bevelSize: 0.012, bevelSegments: 3, curveSegments: 30 });
    mg.translate(-0.01, 0.2, -0.015);
    parts.accent.push(mg);

    // eyes & nostrils
    [-1, 1].forEach((s) => {
      const e = new THREE.SphereGeometry(0.018, 16, 12);
      e.scale(1, 0.7, 0.6);
      e.translate(0.17, 0.71, s * 0.105);
      parts.accent.push(e);
      const n = new THREE.SphereGeometry(0.012, 12, 8);
      n.translate(0.285, 0.61, s * 0.07);
      parts.accent.push(n);
    });
  }

  if (type === 'B') {
    const t = new THREE.TorusGeometry(0.152, 0.009, 8, 48, 2.3);
    t.rotateZ(-1.15);
    t.rotateX(PI / 2);
    t.rotateX(0.6);
    t.translate(0, 0.715, 0);
    parts.accent.push(t);
  }

  if (type === 'Q') {
    for (let i = 0; i < 10; i++) {
      const a = (i * PI) / 5;
      const g = new THREE.SphereGeometry(0.024, 16, 12);
      g.translate(Math.cos(a) * 0.19, 0.915, Math.sin(a) * 0.19);
      parts.body.push(g);
    }
  }

  if (type === 'K') {
    const w = 0.028;
    const cw = 0.08;
    const ch = 0.026;
    const h = 0.2;
    const cy = 0.125;
    const sh = new THREE.Shape();
    sh.moveTo(-w, 0);
    sh.lineTo(w, 0);
    sh.lineTo(w, cy - ch);
    sh.lineTo(cw, cy - ch);
    sh.lineTo(cw, cy + ch);
    sh.lineTo(w, cy + ch);
    sh.lineTo(w, h);
    sh.lineTo(-w, h);
    sh.lineTo(-w, cy + ch);
    sh.lineTo(-cw, cy + ch);
    sh.lineTo(-cw, cy - ch);
    sh.lineTo(-w, cy - ch);
    sh.closePath();
    const g = new THREE.ExtrudeGeometry(sh, { depth: 0.036, bevelEnabled: true, bevelThickness: 0.01, bevelSize: 0.01, bevelSegments: 3 });
    g.translate(0, 1.03, -0.018);
    g.rotateY(PI / 2);
    parts.body.push(g);
  }

  // decorative gold rings
  parts.trims = d.trims.concat([[d.R - 0.017, 0.064]]).map(([r, y]) => {
    const t = new THREE.TorusGeometry(r + 0.002, 0.0075, 8, 72);
    t.rotateX(PI / 2);
    t.translate(0, y, 0);
    return t;
  });

  [...parts.body, ...parts.accent, ...parts.trims].forEach((g) => g.scale(0.84, 1.16, 0.84));
  cache[type] = parts;
  return parts;
}

export function makeLuxeMaterials(look: Pick<Look3D, 'white' | 'black' | 'trim' | 'groove'>): LuxeMaterials {
  return {
    gold: new THREE.MeshPhysicalMaterial(look.white),
    lacquer: new THREE.MeshPhysicalMaterial(look.black),
    trim: new THREE.MeshStandardMaterial({ color: look.trim, metalness: 1, roughness: 0.22 }),
    groove: new THREE.MeshStandardMaterial({ color: look.groove, metalness: 0.7, roughness: 0.45 }),
  };
}

/**
 * Build one piece. `facing` is a quarter-turn count about Y: +1 faces the gold (white)
 * side, -1 the opposite side; knights use 2 / 0 so they look sideways along the board.
 */
export function buildPiece(type: PieceLetter, white: boolean, mats: LuxeMaterials, facing?: number): THREE.Group {
  const p = geoFor(type);
  const group = new THREE.Group();
  const body = (white ? mats.gold : mats.lacquer).clone();
  body.userData['body'] = true;

  const add = (geo: THREE.BufferGeometry, mat: THREE.Material): void => {
    const m = new THREE.Mesh(geo, mat);
    m.castShadow = true;
    m.receiveShadow = true;
    group.add(m);
  };
  p.body.forEach((geo) => add(geo, body));
  p.accent.forEach((geo) => add(geo, white ? mats.groove : mats.trim));
  if (!white) p.trims.forEach((geo) => add(geo, mats.trim));

  const f = facing === undefined ? (white ? 1 : -1) : facing;
  group.rotation.y = (f * PI) / 2;
  return group;
}

/** Tint a piece's body material (used for selection / hover highlight). */
export function glowPiece(group: THREE.Object3D, hex: number, intensity: number): void {
  group.traverse((o) => {
    const mat = (o as THREE.Mesh).material as THREE.MeshPhysicalMaterial | undefined;
    if (mat && mat.userData['body']) {
      mat.emissive.setHex(hex);
      mat.emissiveIntensity = intensity;
    }
  });
}

/** Bevelled frame: top surface at y=top, inner opening half-size `inner`. */
export function buildFrame(mat: THREE.Material, top: number, inner: number, outer: number): THREE.Group {
  const sh = new THREE.Shape();
  sh.moveTo(-outer, -outer);
  sh.lineTo(outer, -outer);
  sh.lineTo(outer, outer);
  sh.lineTo(-outer, outer);
  sh.closePath();
  const hole = new THREE.Path();
  hole.moveTo(-inner, -inner);
  hole.lineTo(-inner, inner);
  hole.lineTo(inner, inner);
  hole.lineTo(inner, -inner);
  hole.closePath();
  sh.holes.push(hole);

  const depth = 0.38;
  const bt = 0.05;
  const g = new THREE.ExtrudeGeometry(sh, { depth, bevelEnabled: true, bevelThickness: bt, bevelSize: bt, bevelSegments: 4 });
  g.rotateX(-PI / 2);
  const m = new THREE.Mesh(g, mat);
  m.position.y = top - depth - bt;
  m.castShadow = true;
  m.receiveShadow = true;

  const grp = new THREE.Group();
  grp.add(m);
  const plate = new THREE.Mesh(new THREE.BoxGeometry(inner * 2 + 0.1, 0.3, inner * 2 + 0.1), mat);
  plate.position.y = top - 0.42;
  plate.receiveShadow = true;
  grp.add(plate);
  return grp;
}
