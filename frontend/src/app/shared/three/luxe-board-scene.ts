// src/app/shared/three/luxe-board-scene.ts
// Framework-agnostic Three.js scene for the REXCHESS Luxe board: marble squares in a
// gilded frame, gold & lacquer Staunton pieces. The Angular wrapper feeds it the
// position / selection from the store and listens for square clicks.
import * as THREE from 'three';
import { Piece, PieceType, Square, Style3D } from '../../core/models';
import { buildFrame, buildPiece, glowPiece, LuxeMaterials, makeLuxeMaterials, PieceLetter } from './luxe-pieces';
import { LOOKS_3D, lookTextures } from './board-looks';

const LETTER: Record<PieceType, PieceLetter> = {
  king: 'K', queen: 'Q', rook: 'R', bishop: 'B', knight: 'N', pawn: 'P',
};

const REST_Y = 0.1;
const LIFT_Y = 0.38;
const BASE_PITCH = -0.24;
/** What the camera looks at in the tilted 3D view. */
const PERSP_TARGET = new THREE.Vector3(0, -0.3, 0.3);

export type Squares = (Piece | null)[][];

export interface Highlights {
  selected: Square | null;
  hints: Square[];
  lastMove: [Square, Square] | null;
  check: Square | null;
}

export interface SceneOptions {
  /** Interactive boards react to clicks and can be orbited by dragging; the others follow the mouse. */
  interactive: boolean;
  /** Board style; defaults to marble & gold */
  look?: Style3D;
  onSquareClick?: (sq: Square) => void;
  /** Fired when a drag ends: true when the board was left more than halfway towards the top-down view. */
  onTopViewChange?: (top: boolean) => void;
}

/** How far the board may turn sideways (rad) — the camera is framed so the whole frame stays visible within it. */
const YAW_LIMIT = { interactive: 0.3, still: 0.1 };
/** Vertical drag distance (px) that takes the view from the 3D angle all the way to straight down. */
const TOP_DRAG_PX = 180;

interface PieceEntry {
  group: THREE.Group;
  row: number;
  col: number;
  letter: PieceLetter;
  white: boolean;
  targetY: number;
  moving: boolean;
  tween?: Tween;
}

interface Tween {
  start: number;
  delay: number;
  duration: number;
  step: (t: number) => void;
  done?: () => void;
}

/** Standard starting position, for decorative boards. */
export function initialSquares(): Squares {
  const back: PieceType[] = ['rook', 'knight', 'bishop', 'queen', 'king', 'bishop', 'knight', 'rook'];
  const empty = () => Array.from({ length: 8 }, () => null as Piece | null);
  return [
    back.map((type) => ({ type, color: 'black' as const })),
    Array.from({ length: 8 }, () => ({ type: 'pawn' as const, color: 'black' as const })),
    empty(), empty(), empty(), empty(),
    Array.from({ length: 8 }, () => ({ type: 'pawn' as const, color: 'white' as const })),
    back.map((type) => ({ type, color: 'white' as const })),
  ];
}

export function isWebGLAvailable(): boolean {
  try {
    const c = document.createElement('canvas');
    return !!(window.WebGLRenderingContext && (c.getContext('webgl2') || c.getContext('webgl')));
  } catch {
    return false;
  }
}

const sqCenter = (row: number, col: number): [number, number] => [-3.5 + col, -3.5 + row];
const keyOf = (row: number, col: number): string => `${row},${col}`;
const sameSq = (a: Square | null, row: number, col: number): boolean => !!a && a.row === row && a.col === col;

export class LuxeBoardScene {
  private renderer: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera: THREE.PerspectiveCamera;
  private boardGroup = new THREE.Group();
  private pieceGroup = new THREE.Group();
  private sqMeshes: THREE.Mesh[] = [];
  private sqTextures: THREE.Texture[] = [];
  private pieces = new Map<string, PieceEntry>();
  private look: Style3D = 'marble-gold';
  private mats!: LuxeMaterials;
  /** Everything buildBoard() adds to the board group, so a style change can swap it out */
  private boardParts: THREE.Object3D[] = [];
  private lastSquares: Squares | null = null;
  private ownedMaterials: THREE.Material[] = [];
  /** Marble materials (squares + frame) whose reflections are dimmed in the top view */
  private boardMaterials: THREE.MeshPhysicalMaterial[] = [];
  private ownedGeometries: THREE.BufferGeometry[] = [];
  private envTexture: THREE.Texture;

  private markers: THREE.Mesh[] = [];
  private dotGeo = new THREE.CylinderGeometry(0.16, 0.16, 0.035, 24);
  private ringGeo = new THREE.TorusGeometry(0.4, 0.04, 10, 40);
  private dotMat = new THREE.MeshStandardMaterial({ color: 0xd6a94e, emissive: 0x8a6420, emissiveIntensity: 0.8, metalness: 1, roughness: 0.25, transparent: true, opacity: 0.9 });
  private ringMat = new THREE.MeshStandardMaterial({ color: 0xe6c27a, emissive: 0x8a6420, emissiveIntensity: 0.8, metalness: 1, roughness: 0.25 });

  private highlights: Highlights = { selected: null, hints: [], lastMove: null, check: null };
  private tweens: Tween[] = [];
  private positioned = false;

  private raycaster = new THREE.Raycaster();
  private pointer = new THREE.Vector2();
  private raf = 0;
  private disposed = false;
  private visible = true;

  private intro = true;
  private introT = 0;
  private perspPos = new THREE.Vector3(0, 9.6, 9.4);
  private topDist = 12;
  /** 0 = tilted 3D view, 1 = straight down; eased towards `topTarget` */
  private topT = 0;
  private topTarget = 0;
  private pitch = 0;
  private targetPitch = 0;
  private yaw = 0;
  private dragYaw = 0;
  private parallaxYaw = 0;
  private flipYaw = 0;
  private drag = { down: false, moved: false, x: 0, y: 0 };

  private readonly listeners: Array<() => void> = [];

  constructor(private canvas: HTMLCanvasElement, private opts: SceneOptions) {
    const width = Math.max(canvas.clientWidth, 200);
    const height = Math.max(canvas.clientHeight, 200);
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(width, height, false);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.15;

    this.look = opts.look ?? 'marble-gold';
    this.mats = makeLuxeMaterials(LOOKS_3D[this.look]);
    this.camera = new THREE.PerspectiveCamera(46, width / height, 0.01, 100);
    this.envTexture = this.buildLighting();
    this.scene.add(this.boardGroup);
    this.buildBoard();
    this.boardGroup.add(this.pieceGroup);
    this.fitCamera();
    this.bindEvents();

    this.boardGroup.position.y = -2;
    this.frame();
  }

  /* ── public API ─────────────────────────────────────────────────────────── */

  resize(width: number, height: number): void {
    if (width < 1 || height < 1) return;
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.fitCamera();
  }

  setVisible(visible: boolean): void {
    this.visible = visible;
  }

  /** Switch between the tilted 3D view and a straight top-down view of the board. */
  setTopView(top: boolean, snap = false): void {
    this.topTarget = top ? 1 : 0;
    if (snap) {
      this.topT = this.topTarget;
      this.applyCamera();
    }
    if (top) this.dragYaw = 0; // look straight down the files, not at a sideways angle
  }

  /** Re-dress the board and pieces in another style, keeping the position, selection and camera. */
  setLook(look: Style3D): void {
    if (look === this.look || this.disposed) return;
    this.disposeBoard();
    this.look = look;
    this.mats = makeLuxeMaterials(LOOKS_3D[look]);
    this.buildBoard();
    this.applyCamera(); // re-applies the top-view reflection dimming to the new materials
    if (this.lastSquares) this.setPosition(this.lastSquares, false);
    else this.applyHighlights();
  }

  setFlipped(flipped: boolean): void {
    this.flipYaw = flipped ? Math.PI : 0;
  }

  /** Sync the pieces with `squares`, animating small changes (a move) and snapping big ones (a new game). */
  setPosition(squares: Squares, animate = true): void {
    this.lastSquares = squares;
    const desired = new Map<string, { letter: PieceLetter; white: boolean }>();
    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        const p = squares[r]?.[c];
        if (p) desired.set(keyOf(r, c), { letter: LETTER[p.type], white: p.color === 'white' });
      }
    }

    const orphans: PieceEntry[] = [];
    for (const [key, e] of [...this.pieces]) {
      const d = desired.get(key);
      if (!d || d.letter !== e.letter || d.white !== e.white) {
        orphans.push(e);
        this.pieces.delete(key);
      }
    }
    const missing = [...desired].filter(([key]) => !this.pieces.has(key));
    const animated = animate && this.positioned && orphans.length + missing.length <= 4;

    for (const [key, d] of missing) {
      const [row, col] = key.split(',').map(Number);
      // reuse the nearest identical piece that left its square (a move, castling rook, en passant…)
      let best = -1;
      let bestDist = Infinity;
      orphans.forEach((o, i) => {
        if (o.letter !== d.letter || o.white !== d.white) return;
        const dist = (o.row - row) ** 2 + (o.col - col) ** 2;
        if (dist < bestDist) { bestDist = dist; best = i; }
      });
      if (best >= 0) {
        const entry = orphans.splice(best, 1)[0];
        this.relocate(entry, row, col, animated);
        this.pieces.set(key, entry);
      } else {
        this.pieces.set(key, this.addPiece(d.letter, d.white, row, col, animated));
      }
    }

    for (const o of orphans) {
      if (animated) this.fadeOut(o);
      else this.pieceGroup.remove(o.group);
    }

    this.positioned = true;
    this.applyHighlights();
  }

  setHighlights(h: Highlights): void {
    this.highlights = h;
    this.applyHighlights();
  }

  /** Free everything that belongs to the current style: squares, frame, labels, pieces. */
  private disposeBoard(): void {
    this.clearMarkers();
    this.tweens = [];
    this.boardParts.forEach((o) => this.boardGroup.remove(o));
    this.boardParts = [];
    this.sqMeshes = [];
    this.boardMaterials = [];
    this.sqTextures.forEach((t) => t.dispose());
    this.sqTextures = [];
    this.ownedMaterials.forEach((m) => m.dispose());
    this.ownedMaterials = [];
    this.ownedGeometries.forEach((g) => g.dispose());
    this.ownedGeometries = [];
    this.pieces.forEach((e) => e.group.traverse((o) => {
      const mat = (o as THREE.Mesh).material as THREE.Material | undefined;
      if (mat && mat.userData['body']) mat.dispose();
    }));
    this.pieces.clear();
    this.pieceGroup.clear();
    Object.values(this.mats).forEach((m: THREE.Material) => m.dispose());
  }

  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.raf);
    this.listeners.forEach((off) => off());
    this.disposeBoard();
    [this.dotGeo, this.ringGeo].forEach((g) => g.dispose());
    [this.dotMat, this.ringMat].forEach((m) => m.dispose());
    this.envTexture.dispose();
    this.renderer.dispose();
    this.renderer.forceContextLoss();
  }

  /* ── scene construction ─────────────────────────────────────────────────── */

  private buildLighting(): THREE.Texture {
    // a tiny studio of glowing cards, baked into an environment map for the metal & lacquer
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    const studio = new THREE.Scene();
    studio.background = new THREE.Color(0x050403);
    const cards: Array<[number, number, number, number, number, [number, number, number]]> = [
      [0, 9, 2, 10, 4, [5, 4.2, 3.2]],
      [-8, 4, 4, 4, 6, [2.4, 1.8, 1.1]],
      [8, 5, -4, 4, 6, [2.2, 1.5, 0.8]],
      [0, 3, 10, 8, 2, [0.8, 0.7, 0.6]],
      // warm bounce off the marble, so upright metal faces (knight flanks) don't mirror a black void
      [0, -3.5, 1, 12, 8, [0.85, 0.6, 0.32]],
    ];
    const cardGeos: THREE.BufferGeometry[] = [];
    const cardMats: THREE.Material[] = [];
    cards.forEach(([x, y, z, w, h, col]) => {
      const geo = new THREE.PlaneGeometry(w, h);
      const mat = new THREE.MeshBasicMaterial({ side: THREE.DoubleSide });
      mat.color.setRGB(col[0], col[1], col[2]);
      const m = new THREE.Mesh(geo, mat);
      m.position.set(x, y, z);
      m.lookAt(0, 0, 0);
      studio.add(m);
      cardGeos.push(geo);
      cardMats.push(mat);
    });
    const env = pmrem.fromScene(studio, 0.04).texture;
    pmrem.dispose();
    cardGeos.forEach((g) => g.dispose());
    cardMats.forEach((m) => m.dispose());
    this.scene.environment = env;

    this.scene.add(new THREE.AmbientLight(0x3a2c1e, 0.6));
    const key = new THREE.DirectionalLight(0xffe2b8, 3.0);
    key.position.set(-4, 12, 6);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    Object.assign(key.shadow.camera, { left: -10, right: 10, top: 10, bottom: -10, far: 50 });
    key.shadow.bias = -0.0005;
    key.shadow.radius = 4;
    this.scene.add(key);
    const rim = new THREE.DirectionalLight(0xffb36b, 2.2);
    rim.position.set(6, 5, -7);
    this.scene.add(rim);
    const fill = new THREE.DirectionalLight(0x8090b0, 0.25);
    fill.position.set(4, 6, 8);
    this.scene.add(fill);
    return env;
  }

  private buildBoard(): void {
    const bg = this.boardGroup;
    const look = LOOKS_3D[this.look];
    const tex = lookTextures(this.look);
    const add = <T extends THREE.Object3D>(o: T): T => {
      bg.add(o);
      this.boardParts.push(o);
      return o;
    };

    const frameMat = new THREE.MeshPhysicalMaterial({ map: tex.frame, roughness: 0.22, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.08 });
    this.ownedMaterials.push(frameMat);
    this.boardMaterials.push(frameMat);
    const frame = buildFrame(frameMat, 0.1, 4.02, 4.92);
    frame.traverse((o) => { if ((o as THREE.Mesh).geometry) this.ownedGeometries.push((o as THREE.Mesh).geometry); });
    add(frame);

    // inlay lines
    const goldMat = new THREE.MeshStandardMaterial({ color: look.inlay, metalness: 1, roughness: 0.22 });
    this.ownedMaterials.push(goldMat);
    ([[4.1, 0.035], [4.78, 0.025]] as Array<[number, number]>).forEach(([d, t]) => {
      ([[0, -d, 2 * d + t, t], [0, d, 2 * d + t, t], [-d, 0, t, 2 * d + t], [d, 0, t, 2 * d + t]] as Array<[number, number, number, number]>).forEach(([x, z, w, dd]) => {
        const geo = new THREE.BoxGeometry(w, 0.02, dd);
        this.ownedGeometries.push(geo);
        const line = new THREE.Mesh(geo, goldMat);
        line.position.set(x, 0.105, z);
        add(line);
      });
    });

    // squares — every one samples a different patch of the marble so they don't look tiled
    const squareGeo = new THREE.BoxGeometry(0.995, 0.12, 0.995);
    this.ownedGeometries.push(squareGeo);
    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        const light = (r + c) % 2 === 0;
        const tx = (light ? tex.light : tex.dark).clone();
        tx.needsUpdate = true;
        tx.repeat.set(0.25, 0.25);
        tx.offset.set(Math.random() * 0.75, Math.random() * 0.75);
        // a quarter turn keeps wood grain from running the same way on every square
        tx.center.set(0.5, 0.5);
        tx.rotation = Math.floor(Math.random() * 4) * (Math.PI / 2);
        this.sqTextures.push(tx);
        const mat = new THREE.MeshPhysicalMaterial({ map: tx, color: 0xffffff, roughness: 0.16, metalness: 0, clearcoat: 0.8, clearcoatRoughness: 0.06 });
        this.ownedMaterials.push(mat);
        this.boardMaterials.push(mat);
        const mesh = new THREE.Mesh(squareGeo, mat);
        mesh.position.set(-3.5 + c, 0.04, -3.5 + r);
        mesh.receiveShadow = true;
        mesh.userData = { row: r, col: c, light };
        add(mesh);
        this.sqMeshes.push(mesh);
      }
    }

    // coordinate labels
    const files = 'abcdefgh';
    const ranks = '87654321';
    for (let i = 0; i < 8; i++) {
      this.addLabel(files[i], -3.5 + i, 0.11, 4.44);
      this.addLabel(ranks[i], -4.44, 0.11, -3.5 + i);
    }
  }

  private addLabel(text: string, x: number, y: number, z: number): void {
    const c = document.createElement('canvas');
    c.width = 64;
    c.height = 64;
    const ctx = c.getContext('2d')!;
    ctx.fillStyle = LOOKS_3D[this.look].label;
    ctx.font = 'italic 500 40px "Cormorant Garamond", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 32, 32);
    const tex = new THREE.CanvasTexture(c);
    this.sqTextures.push(tex);
    const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false });
    this.ownedMaterials.push(mat);
    const sprite = new THREE.Sprite(mat);
    sprite.position.set(x, y, z);
    sprite.scale.set(0.28, 0.28, 1);
    this.boardGroup.add(sprite);
    this.boardParts.push(sprite);
  }

  /** Back the camera off until the whole frame stays on screen, for both the tilted and the top-down view. */
  private fitCamera(): void {
    const dir = new THREE.Vector3(0, 9.6, 9.4).normalize();
    const target = PERSP_TARGET;
    const E = 4.97; // frame half-width, bevel included
    const yawMax = this.opts.interactive ? YAW_LIMIT.interactive : YAW_LIMIT.still;
    const pitches = this.opts.interactive
      ? [BASE_PITCH]
      : [BASE_PITCH - 0.03, BASE_PITCH + 0.03];

    const pts: THREE.Vector3[] = [];
    for (const pitch of pitches) {
      for (const yaw of [-yawMax, 0, yawMax]) {
        const euler = new THREE.Euler(pitch, yaw, 0, 'XYZ');
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) for (const y of [-0.3, 1.6]) {
          pts.push(new THREE.Vector3(sx * E, y, sz * E).applyEuler(euler));
        }
      }
    }
    const allInside = (): boolean => {
      this.camera.updateMatrixWorld();
      this.camera.updateProjectionMatrix();
      return pts.every((p) => {
        const v = p.clone().project(this.camera);
        return Math.abs(v.x) <= 0.97 && Math.abs(v.y) <= 0.97;
      });
    };

    this.camera.up.set(0, 1, 0);
    let d = 6;
    for (; d < 40; d += 0.1) {
      this.camera.position.copy(target).addScaledVector(dir, d);
      this.camera.lookAt(target);
      if (allInside()) break;
    }
    this.perspPos.copy(target).addScaledVector(dir, d);

    // top-down: camera straight above, rank 8 at the top of the screen
    pts.length = 0;
    for (const yaw of [0, Math.PI]) {
      const euler = new THREE.Euler(0, yaw, 0, 'XYZ');
      for (const sx of [-1, 1]) for (const sz of [-1, 1]) for (const y of [-0.3, 1.2]) {
        pts.push(new THREE.Vector3(sx * E, y, sz * E).applyEuler(euler));
      }
    }
    this.camera.up.set(0, 0, -1);
    let top = 6;
    for (; top < 40; top += 0.1) {
      this.camera.position.set(0, top, 0);
      this.camera.lookAt(0, 0, 0);
      if (allInside()) break;
    }
    this.topDist = top;

    this.applyCamera();
  }

  /** Place the camera between the tilted pose (topT = 0) and the top-down pose (topT = 1). */
  private applyCamera(): void {
    const t = this.topT;
    this.camera.position.lerpVectors(this.perspPos, new THREE.Vector3(0, this.topDist, 0), t);
    this.camera.up.lerpVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, 0, -1), t).normalize();
    this.camera.lookAt(PERSP_TARGET.x * (1 - t), PERSP_TARGET.y * (1 - t), PERSP_TARGET.z * (1 - t));
    this.camera.updateMatrixWorld();

    // seen from straight above, the glossy marble mirrors the bright ceiling light — tone the reflections down
    const reflect = 1 - 0.85 * t;
    this.boardMaterials.forEach((m) => (m.envMapIntensity = reflect));
  }

  /* ── pieces ─────────────────────────────────────────────────────────────── */

  private addPiece(letter: PieceLetter, white: boolean, row: number, col: number, animated: boolean): PieceEntry {
    const group = buildPiece(letter, white, this.mats, letter === 'N' ? (white ? 2 : 0) : white ? 1 : -1);
    const [x, z] = sqCenter(row, col);
    group.position.set(x, REST_Y, z);
    group.userData = { row, col };
    this.pieceGroup.add(group);
    const entry: PieceEntry = { group, row, col, letter, white, targetY: REST_Y, moving: false };
    if (animated) {
      group.scale.setScalar(0.01);
      this.tween(260, (t) => group.scale.setScalar(0.01 + 0.99 * (1 - Math.pow(1 - t, 3))));
    }
    return entry;
  }

  private relocate(entry: PieceEntry, row: number, col: number, animated: boolean): void {
    const [tx, tz] = sqCenter(row, col);
    entry.row = row;
    entry.col = col;
    entry.group.userData = { row, col };
    entry.targetY = REST_Y;
    const g = entry.group;
    this.cancelTween(entry);
    if (!animated) {
      g.position.set(tx, REST_Y, tz);
      return;
    }
    const sx = g.position.x;
    const sz = g.position.z;
    entry.moving = true;
    entry.tween = this.tween(
      380,
      (t) => {
        const e = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        g.position.x = sx + (tx - sx) * e;
        g.position.z = sz + (tz - sz) * e;
        g.position.y = REST_Y + Math.sin(t * Math.PI) * 0.95;
      },
      () => {
        entry.moving = false;
        g.position.set(tx, REST_Y, tz);
      },
    );
  }

  private fadeOut(entry: PieceEntry): void {
    const g = entry.group;
    this.cancelTween(entry);
    entry.moving = true;
    this.tween(
      300,
      (t) => {
        g.scale.setScalar(Math.max(0.01, 1 - t));
        g.position.y = REST_Y - t * 0.2;
      },
      () => this.pieceGroup.remove(g),
      160,
    );
  }

  private tween(duration: number, step: (t: number) => void, done?: () => void, delay = 0): Tween {
    const tw: Tween = { start: performance.now(), delay, duration, step, done };
    this.tweens.push(tw);
    return tw;
  }

  /** Drop a piece's in-flight move so a newer one never fights it for the position. */
  private cancelTween(entry: PieceEntry): void {
    if (!entry.tween) return;
    const i = this.tweens.indexOf(entry.tween);
    if (i >= 0) this.tweens.splice(i, 1);
    entry.tween = undefined;
    entry.moving = false;
  }

  /* ── selection, hints, last move ────────────────────────────────────────── */

  private applyHighlights(): void {
    const { selected, hints, lastMove, check } = this.highlights;

    this.pieces.forEach((e) => {
      const isSel = sameSq(selected, e.row, e.col);
      e.targetY = isSel ? LIFT_Y : REST_Y;
      glowPiece(e.group, isSel ? (e.white ? 0x4a2c00 : 0x5a3c12) : 0x000000, isSel ? 0.6 : 0);
    });

    this.sqMeshes.forEach((m) => {
      const { row, col } = m.userData as { row: number; col: number };
      const mat = m.material as THREE.MeshPhysicalMaterial;
      const isHint = hints.some((h) => h.row === row && h.col === col);
      const isLast = !!lastMove && (sameSq(lastMove[0], row, col) || sameSq(lastMove[1], row, col));
      const [color, emissive, intensity] = sameSq(check, row, col) ? [0xffb0a0, 0x8a1c14, 0.6]
        : sameSq(selected, row, col) ? [0xffd890, 0x6a4510, 0.5]
        : isHint ? [0xf2dfb4, 0x3a2808, 0.3]
        : isLast ? [0xead6aa, 0x2a1c06, 0.18]
        : [0xffffff, 0x000000, 0];
      mat.color.setHex(color);
      mat.emissive.setHex(emissive);
      mat.emissiveIntensity = intensity;
    });

    this.clearMarkers();
    hints.forEach((h) => {
      const occupied = this.pieces.has(keyOf(h.row, h.col));
      const mesh = new THREE.Mesh(occupied ? this.ringGeo : this.dotGeo, occupied ? this.ringMat : this.dotMat);
      const [x, z] = sqCenter(h.row, h.col);
      if (occupied) mesh.rotation.x = Math.PI / 2;
      mesh.position.set(x, 0.12, z);
      this.boardGroup.add(mesh);
      this.markers.push(mesh);
    });
  }

  private clearMarkers(): void {
    this.markers.forEach((m) => this.boardGroup.remove(m));
    this.markers = [];
  }

  /* ── input ──────────────────────────────────────────────────────────────── */

  private bindEvents(): void {
    const on = (target: EventTarget, type: string, fn: (e: any) => void): void => {
      target.addEventListener(type, fn);
      this.listeners.push(() => target.removeEventListener(type, fn));
    };

    if (!this.opts.interactive) {
      // decorative board: gentle parallax following the mouse
      on(window, 'mousemove', (e: MouseEvent) => {
        this.targetPitch = (e.clientY / window.innerHeight - 0.5) * -0.06;
        this.parallaxYaw = (e.clientX / window.innerWidth - 0.5) * 0.15;
      });
      return;
    }

    on(this.canvas, 'pointerdown', (e: PointerEvent) => {
      this.drag = { down: true, moved: false, x: e.clientX, y: e.clientY };
    });
    on(window, 'pointermove', (e: PointerEvent) => {
      if (this.drag.down) {
        const dx = e.clientX - this.drag.x;
        const dy = e.clientY - this.drag.y;
        if (!this.drag.moved && Math.hypot(dx, dy) < 5) return;
        this.drag.moved = true;
        this.drag.x = e.clientX;
        this.drag.y = e.clientY;
        this.dragYaw = Math.max(-YAW_LIMIT.interactive, Math.min(YAW_LIMIT.interactive, this.dragYaw + dx * 0.008));
        // dragging down raises the camera; all the way down looks straight at the board
        this.topTarget = Math.max(0, Math.min(1, this.topTarget + dy / TOP_DRAG_PX));
        this.canvas.style.cursor = 'grabbing';
      } else if (e.target === this.canvas) {
        this.canvas.style.cursor = this.pick(e) ? 'pointer' : 'default';
      }
    });
    on(window, 'pointerup', () => {
      // the board stays at whatever angle it was dragged to; just tell the page which side of halfway it is
      if (this.drag.down && this.drag.moved) this.opts.onTopViewChange?.(this.topTarget > 0.5);
      this.drag.down = false;
      this.canvas.style.cursor = 'default';
    });
    on(this.canvas, 'click', (e: MouseEvent) => {
      if (this.drag.moved) {
        this.drag.moved = false;
        return;
      }
      const sq = this.pick(e);
      if (sq && this.opts.onSquareClick) this.opts.onSquareClick(sq);
    });
  }

  /** Board square under the pointer, or null. */
  private pick(e: { clientX: number; clientY: number }): Square | null {
    const rect = this.canvas.getBoundingClientRect();
    this.pointer.set(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const targets: THREE.Object3D[] = [...this.sqMeshes, ...[...this.pieces.values()].map((p) => p.group)];
    const hit = this.raycaster.intersectObjects(targets, true)[0];
    if (!hit) return null;
    let obj: THREE.Object3D | null = hit.object;
    while (obj && obj.userData['row'] === undefined) obj = obj.parent;
    return obj ? { row: obj.userData['row'] as number, col: obj.userData['col'] as number } : null;
  }

  /* ── render loop ────────────────────────────────────────────────────────── */

  private frame = (): void => {
    if (this.disposed) return;
    this.raf = requestAnimationFrame(this.frame);
    if (!this.visible || document.hidden) return;

    const now = performance.now();
    const t = now * 0.001;

    if (this.intro) {
      this.introT = Math.min(this.introT + 0.014, 1);
      const e = 1 - Math.pow(1 - this.introT, 3);
      this.boardGroup.rotation.x = BASE_PITCH * e * (1 - this.topT);
      this.boardGroup.position.y = -2 * (1 - e);
      if (this.introT >= 1) this.intro = false;
    }

    const k = this.opts.interactive ? 0.15 : 0.07;
    this.pitch += (this.targetPitch - this.pitch) * k;
    this.yaw += (this.flipYaw + (this.dragYaw + this.parallaxYaw) * (1 - this.topT) - this.yaw) * k;
    if (!this.intro) {
      this.boardGroup.rotation.x = (BASE_PITCH + this.pitch) * (1 - this.topT);
    }

    if (this.topT !== this.topTarget) {
      this.topT += (this.topTarget - this.topT) * 0.12;
      if (Math.abs(this.topTarget - this.topT) < 0.002) this.topT = this.topTarget;
      this.applyCamera();
    }
    this.boardGroup.rotation.y = this.yaw;

    for (let i = this.tweens.length - 1; i >= 0; i--) {
      const tw = this.tweens[i];
      const elapsed = now - tw.start - tw.delay;
      if (elapsed < 0) continue;
      const p = Math.min(elapsed / tw.duration, 1);
      tw.step(p);
      if (p >= 1) {
        this.tweens.splice(i, 1);
        tw.done?.();
      }
    }

    this.pieces.forEach((e) => {
      if (!e.moving) e.group.position.y += (e.targetY - e.group.position.y) * 0.3;
    });

    this.dotMat.emissiveIntensity = this.ringMat.emissiveIntensity = 0.5 + Math.sin(t * 2.5) * 0.2;
    this.markers.forEach((m, i) => m.scale.setScalar(0.85 + Math.sin(t * 2 + i * 0.9) * 0.1));

    this.renderer.render(this.scene, this.camera);
  };
}
