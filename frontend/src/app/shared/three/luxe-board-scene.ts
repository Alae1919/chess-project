// src/app/shared/three/luxe-board-scene.ts
// Framework-agnostic Three.js scene for the REXCHESS Luxe board: marble squares in a
// gilded frame, gold & lacquer Staunton pieces. The Angular wrapper feeds it the
// position / selection from the store and listens for square clicks.
import * as THREE from 'three';
import { Piece, PieceType, Square, Style3D } from '../../core/models';
import { buildFrame, buildPiece, disposeLuxeMaterials, glowPiece, hasPieceModel, loadPieceModels, LuxeMaterials, makeLuxeMaterials, PieceLetter, pieceModelsSettled, reshapePiece } from './luxe-pieces';
import { fitSquare, hasPhotoWoods, loadPhotoWoods, LOOKS_3D, lookTextures, photoWoodsReady } from './board-looks';
import { DESK_HOME, DESK_PRESETS, DeskPreset, DeskView, deskOffset, easeView, nudge, orbitDrag, panDrag, sameView, zoomBy } from './desk-camera';
import { disposeProp, loadDeskProps, LoadedProp } from './desk-props';
import { buildDesk, DESK_PANO_URL, DESK_WOOD_URL, DeskParts, loadPicture, roomEnvironment, standInEnvironment } from './desk-environment';

const LETTER: Record<PieceType, PieceLetter> = {
  king: 'K', queen: 'Q', rook: 'R', bishop: 'B', knight: 'N', pawn: 'P',
};

/** Phones: pieces drawn this much bigger than on a desktop, to be seen and tapped easily (a king's base stays inside its square). */
const PHONE_PIECE_SIZE = 1.25;
const REST_Y = 0.1;
const LIFT_Y = 0.38;
/**
 * The pieces stay off the board until their model files have loaded. If that takes longer than this
 * (a stalled connection), the stand-in lathe pieces are shown instead and swapped when the files arrive.
 */
const MODEL_WAIT_MS = 6000;
/** Longest the board waits for its shaders to compile before drawing anyway (a driver that never reports them done) */
const COMPILE_WAIT_MS = 8000;
/** The desk view waits this long for its pictures (the room, the wood) before it is drawn without them */
const DESK_WAIT_MS = 8000;
/** How much the room behind the desk is blurred, as the background of a photograph is (0..1) */
const DESK_BLUR = 0.025;
const BASE_PITCH = -0.24;
/** What the camera looks at in the tilted 3D view. */
const PERSP_TARGET = new THREE.Vector3(0, -0.3, 0.3);
/** The study: the board lies flat on a table, seen from this far above the table (rad), looking at this point */
const STUDY_ELEVATION = 0.64;
/** Pieces drawn a little bigger in the study: the squares are seen from further away */
const STUDY_PIECE_SIZE = 1.1;
const STUDY_TARGET = new THREE.Vector3(0, 0.25, 0.1);
/** Underside of the board's frame: the height of the table it stands on */
const TABLE_Y = -0.47;
/** The study's key light: up and to the left in the tilted view, swung out and lower in the top view */
const KEY_INTENSITY = 700;
const KEY_TILTED = new THREE.Vector3(-6, 13, 8);
const KEY_TOP = new THREE.Vector3(-10, 8.5, 10.8);

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
  /** Phones: the board may barely turn sideways, so the camera can come closer and fill the screen */
  tight?: boolean;
  /** Dragging a piece: may the piece on this square be picked up? */
  canDrag?: (sq: Square) => boolean;
  /** A piece was picked up and dragged off its square: select it, so its legal moves load */
  onDragStart?: (sq: Square) => void;
  /**
   * A piece was let go over a square. The answer tells the piece where to go: `accept` (the move is
   * played) and `wait` (decided later) leave it there, `reject` sends it back where it came from.
   */
  onDrop?: (from: Square, to: Square) => 'accept' | 'reject' | 'wait';
  /** How long an animated move takes, in ms (default 380) */
  moveMs?: number;
  /** How high a moving piece hops (default 0.95) */
  hop?: number;
  /** A coloured light that travels with a moving piece and fades once it has landed (off by default) */
  moveLight?: number;
  /**
   * `studio` (default): the board is tilted towards the viewer in a dark studio of its own.
   * `study`: the board lies flat on a table that the page paints behind the (transparent) canvas;
   * the scene adds what the photo cannot: the board's shadow on the table, and the light of that room.
   * `desk`: the same table and room, but drawn by the scene itself (a desk surface and a 360° room), so the camera
   * can move around the board: turn, rise, come closer, shift (see desk-camera.ts).
   */
  environment?: 'studio' | 'study' | 'desk';
  /**
   * The first frame was drawn, `ms` after the scene was created. `cold` for the first scene since the page loaded:
   * later ones find the piece models, and the browser's caches, already warm.
   */
  onFirstFrame?: (ms: number, cold: boolean) => void;
  /** The desk's room, wood and props are all in, `ms` after the scene asked for them (desk scenes only) */
  onDeskReady?: (ms: number, cold: boolean) => void;
}

/** Runs `work` when the main thread is idle (or soon, where that cannot be asked about) */
function whenIdle(work: () => void): void {
  const ric = (window as { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number }).requestIdleCallback;
  if (ric) ric(work, { timeout: 3000 });
  else setTimeout(work, 500);
}

/** Records the time since `start` as a performance measure named `name`; returns the end, for the next step */
function measure(name: string, start: number): number {
  const end = performance.now();
  try { performance.measure(name, { start, end }); } catch { /* an old browser: nothing to record */ }
  return end;
}

/** Scenes and desks built since the page loaded: the first of each is the one that loads everything */
let scenesBuilt = 0;
let desksLoaded = 0;

/** Room the page leaves free for the board, in px from each side of the canvas (panels, bars) */
export interface SafeArea {
  left: number;
  right: number;
  top: number;
  bottom: number;
  /** Panels floating over the canvas, in canvas px: the board is framed as large as it can be without running under them */
  avoid?: Array<{ x: number; y: number; w: number; h: number }>;
}

/** A line of the engine's calculation drawn over the board: from one square to another, at some strength (0..1). */
export interface Arc {
  from: Square;
  to: Square;
  strength: number;
}

/** Pixels a press must travel before it counts as a drag rather than a tap */
const DRAG_THRESHOLD_PX = 6;
/** Height a piece is carried at, above the board */
const HELD_Y = 0.75;

/** How far the board may turn sideways (rad) — the camera is framed so the whole frame stays visible within it. */
const YAW_LIMIT = { interactive: 0.3, still: 0.1, tight: 0.06, study: 0.08, desk: 0.1 };
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

/** Can this browser draw WebGL? (Kept for the scene's callers; pages check with canUseWebGL, which loads no three.js.) */
export { canUseWebGL as isWebGLAvailable } from './webgl-support';

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
  /** The desk's lettering, which turns against the board so as to stay readable (see frame()) */
  private labels: THREE.Object3D[] = [];
  private lastSquares: Squares | null = null;
  private ownedMaterials: THREE.Material[] = [];
  /** Marble materials (squares + frame) whose reflections are dimmed in the top view */
  private boardMaterials: THREE.MeshPhysicalMaterial[] = [];
  private ownedGeometries: THREE.BufferGeometry[] = [];
  private envTexture: THREE.Texture;
  /** The board lies flat on a table (see SceneOptions.environment): the page's photo, or the scene's own desk */
  private readonly study: boolean;
  /** The table and the room are the scene's own, and the camera moves around the board */
  private readonly desk: boolean;
  private deskParts: DeskParts | null = null;
  /** The room and the wood have arrived (or we gave up waiting): until then the desk view draws nothing */
  private deskReady = true;
  private deskTimer = 0;
  private panoTexture: THREE.Texture | null = null;
  /** What stands on the desk (the lamp, the books), freed with the scene */
  private deskProps: THREE.Object3D[] = [];
  /** The lamp's light: in the study it is a guess at where the photo's lamp is; on the desk it moves to the real lamp */
  private lampLight: THREE.PointLight | null = null;
  /** Where the camera is, where the player has sent it, and the distance it was framed at */
  private deskView: DeskView = { ...DESK_HOME };
  private deskGoal: DeskView = { ...DESK_HOME };
  private deskDist = 14;
  private readonly deskTarget = new THREE.Vector3();
  /** Fingers on the canvas, to tell a pinch from a drag */
  private touches = new Map<number, { x: number; y: number }>();
  private pinchDist = 0;
  /** What the table needs from the scene: shadows. Built once, freed with the scene. */
  private tableParts: Array<{ geo: THREE.BufferGeometry; mat: THREE.Material; tex?: THREE.Texture }> = [];
  private lights: THREE.Light[] = [];
  /** the study's main light; it moves out and lower when the camera looks straight down (see applyCamera) */
  private keyLight: THREE.SpotLight | null = null;
  private safe: SafeArea = { left: 0, right: 0, top: 0, bottom: 0 };
  private viewW = 1;
  private viewH = 1;

  private markers: THREE.Mesh[] = [];
  private dotGeo = new THREE.CylinderGeometry(0.16, 0.16, 0.035, 24);
  private ringGeo = new THREE.TorusGeometry(0.4, 0.04, 10, 40);
  private dotMat = new THREE.MeshStandardMaterial({ color: 0xd6a94e, emissive: 0x8a6420, emissiveIntensity: 0.8, metalness: 1, roughness: 0.25, transparent: true, opacity: 0.9 });
  private ringMat = new THREE.MeshStandardMaterial({ color: 0xe6c27a, emissive: 0x8a6420, emissiveIntensity: 0.8, metalness: 1, roughness: 0.25 });

  private highlights: Highlights = { selected: null, hints: [], lastMove: null, check: null };
  /** Calculation arcs, by "from-to" key, so changing a strength reuses the meshes */
  private arcs = new Map<string, { group: THREE.Group; mats: THREE.MeshBasicMaterial[]; geos: THREE.BufferGeometry[] }>();
  /** Travels with a moving piece when `moveLight` is set; made once, as adding a light later recompiles every material */
  private carryLight: THREE.PointLight | null = null;
  private tweens: Tween[] = [];
  private positioned = false;
  /** Waiting for the piece models: setPosition only remembers the position until they are in */
  private modelsPending = false;
  private modelsTimer = 0;
  /** The shaders are still compiling (see compile()): nothing is drawn yet, and the intro waits for it */
  private compiling = true;
  /** Run once the next frame has been drawn: frees the look that has just been replaced (see retireBoard) */
  private afterDraw: Array<() => void> = [];
  /** One pawn per look, never drawn: their materials keep every look's shader programs alive (see keepLooksWarm) */
  private warm: { group: THREE.Group; mats: LuxeMaterials[] } | null = null;

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
  private drag = { down: false, moved: false, x: 0, y: 0, pan: false };
  /** The piece in the player's hand: pressed, and carried once the pointer has moved far enough */
  private held: { entry: PieceEntry; from: Square; x: number; y: number; id: number; active: boolean } | null = null;
  /** The square under the carried piece */
  private hover: Square | null = null;
  private suppressClick = false;

  private readonly listeners: Array<() => void> = [];

  private readonly createdAt = performance.now();
  private readonly cold = scenesBuilt++ === 0;
  private firstFrameDrawn = false;

  constructor(private canvas: HTMLCanvasElement, private opts: SceneOptions) {
    const width = Math.max(canvas.clientWidth, 200);
    const height = Math.max(canvas.clientHeight, 200);
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    this.renderer.setSize(width, height, false);
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.desk = opts.environment === 'desk';
    this.study = opts.environment === 'study' || this.desk;
    this.renderer.toneMappingExposure = this.study ? 1.05 : 1.15;
    this.viewW = width;
    this.viewH = height;

    this.look = opts.look ?? 'marble-gold';
    this.mats = makeLuxeMaterials(LOOKS_3D[this.look]);
    this.camera = new THREE.PerspectiveCamera(this.study ? 56 : 46, width / height, 0.01, 100);
    this.envTexture = this.study ? this.buildStudyLighting() : this.buildLighting();
    this.scene.add(this.boardGroup);
    if (this.study) this.buildTable();
    this.buildBoard();
    this.boardGroup.add(this.pieceGroup);
    if (opts.moveLight !== undefined) {
      this.carryLight = new THREE.PointLight(opts.moveLight, 0, 2.6, 1.6);
      this.boardGroup.add(this.carryLight);
    }
    this.fitCamera();
    this.bindEvents();

    this.boardGroup.position.y = this.study ? 0.9 : -2;
    if (this.desk) {
      // nothing is drawn until the room and the wood are in: until then the page's own photo of the room shows
      this.deskReady = false;
      this.deskTimer = window.setTimeout(() => (this.deskReady = true), DESK_WAIT_MS);
      void this.loadDesk();
    }
    this.keepLooksWarm();
    this.frame(); // arms the loop; nothing is drawn until the shaders are compiled
    // a tick on, so that the pieces the caller is about to set are compiled with the rest
    queueMicrotask(() => void this.compile().then(() => {
      try { this.drawWarm(); } finally { this.compiling = false; }
    }));

    // the model files come in after the first frame; until they have, no piece is drawn
    if (!pieceModelsSettled()) {
      this.modelsPending = true;
      this.modelsTimer = window.setTimeout(() => this.revealPieces(), MODEL_WAIT_MS);
      void loadPieceModels().then((arrived) => {
        if (this.disposed) return;
        if (this.modelsPending) this.revealPieces();
        else if (arrived) this.reshapePieces(); // the wait ran out first: the stand-ins give way to the models
      });
    }
  }

  /* ── public API ─────────────────────────────────────────────────────────── */

  resize(width: number, height: number): void {
    if (width < 1 || height < 1) return;
    this.viewW = width;
    this.viewH = height;
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.fitCamera();
  }

  /** The part of the canvas the board may use: the rest is under panels. The camera frames the board inside it. */
  setSafeArea(area: SafeArea): void {
    const s = this.safe;
    if (area.left === s.left && area.right === s.right && area.top === s.top && area.bottom === s.bottom) return;
    this.safe = { ...area };
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

  /**
   * Desk view only: send the camera to a named view (the others keep their fixed camera). `overhead` is the
   * top-down slide, and the page is told about it as after a drag. Anything else comes back from it.
   */
  setView(preset: DeskPreset | 'reset'): void {
    if (!this.desk) return;
    if (preset === 'overhead') {
      this.topTarget = 1;
      this.opts.onTopViewChange?.(true);
      return;
    }
    this.deskGoal = { ...(DESK_PRESETS[preset === 'reset' ? 'player' : preset]) };
    if (this.topTarget !== 0) {
      this.topTarget = 0;
      this.opts.onTopViewChange?.(false);
    }
  }

  /** Re-dress the board and pieces in another style, keeping the position, selection and camera. */
  setLook(look: Style3D): void {
    if (look === this.look || this.disposed) return;
    this.dress(look);
  }

  /** Put the board and its pieces into `look`'s clothes, replacing whatever they wear (the same look too: its photographs may have come in) */
  private dress(look: Style3D): void {
    // The old look is freed only after the new one has been drawn: a shader program is deleted the moment the
    // last material using it goes, and the new materials (the same programs, other colours) would compile it again.
    const free = this.retireBoard();
    this.look = look;
    this.mats = makeLuxeMaterials(LOOKS_3D[look]);
    this.buildBoard();
    this.applyCamera(); // re-applies the top-view reflection dimming to the new materials
    if (this.lastSquares) this.setPosition(this.lastSquares, false);
    else this.applyHighlights();
    this.afterDraw.push(free);
  }

  setFlipped(flipped: boolean): void {
    this.flipYaw = flipped ? Math.PI : 0;
  }

  /** Sync the pieces with `squares`, animating small changes (a move) and snapping big ones (a new game). */
  setPosition(squares: Squares, animate = true): void {
    this.lastSquares = squares;
    if (this.modelsPending) return; // revealPieces() draws the latest position once the models are in
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

  /**
   * A move the player has just tapped: the piece goes across at once, before the game has confirmed the move.
   * The piece stays on its old square in the scene's books, so the confirmation finds it already there (no
   * second hop), and a refused move sends it back with `settle`.
   */
  slideTo(from: Square, to: Square, ms = 300): void {
    const entry = this.pieces.get(keyOf(from.row, from.col));
    if (!entry) return;
    const g = entry.group;
    const [tx, tz] = sqCenter(to.row, to.col);
    const [sx, sy, sz] = [g.position.x, g.position.y, g.position.z];
    const hop = (this.opts.hop ?? 0.95) * 0.6;
    this.cancelTween(entry);
    entry.moving = true;
    entry.tween = this.tween(
      ms,
      (t) => {
        const e = 1 - Math.pow(1 - t, 3);
        g.position.set(sx + (tx - sx) * e, sy + (REST_Y - sy) * e + Math.sin(t * Math.PI) * hop, sz + (tz - sz) * e);
      },
      () => {
        entry.moving = false;
        g.position.set(tx, REST_Y, tz);
      },
    );
  }

  /** Send every piece that is not on its square back to it (a move that was not played). */
  settle(): void {
    this.pieces.forEach((e) => {
      if (this.held?.active && this.held.entry === e) return;
      const [x, z] = sqCenter(e.row, e.col);
      if (Math.hypot(e.group.position.x - x, e.group.position.z - z) > 0.02) this.glide(e, x, z);
    });
  }

  setHighlights(h: Highlights): void {
    this.highlights = h;
    this.applyHighlights();
  }

  /** Put the camera part-way between the tilted view (0) and straight above (1); `snap` skips the easing. */
  setTopAmount(t: number, snap = false): void {
    this.topTarget = Math.max(0, Math.min(1, t));
    if (snap) {
      this.topT = this.topTarget;
      this.applyCamera();
    }
  }

  /** Draw these arcs over the board (and remove the others). A strength of 0 hides an arc without freeing it. */
  setArcs(arcs: Arc[], color = 0x4da6ff): void {
    const wanted = new Set<string>();
    for (const a of arcs) {
      const key = `${a.from.row},${a.from.col}-${a.to.row},${a.to.col}`;
      wanted.add(key);
      let arc = this.arcs.get(key);
      if (!arc) {
        arc = this.buildArc(a.from, a.to, color);
        this.arcs.set(key, arc);
        this.boardGroup.add(arc.group);
      }
      const k = Math.max(0, Math.min(1, a.strength));
      arc.group.visible = k > 0.001;
      // core, glow, landing square: each its own share of the strength
      [1, 0.28, 0.85].forEach((share, i) => (arc!.mats[i].opacity = k * share));
    }
    for (const [key, arc] of this.arcs) {
      if (wanted.has(key)) continue;
      this.boardGroup.remove(arc.group);
      arc.mats.forEach((m) => m.dispose());
      arc.geos.forEach((g) => g.dispose());
      this.arcs.delete(key);
    }
  }

  /**
   * Take everything that belongs to the current style (squares, frame, labels, pieces) off the board, and hand
   * back what frees it. Nothing is freed until that is called, so the old look can stay alive while the new one is made.
   */
  private retireBoard(): () => void {
    this.clearMarkers();
    this.tweens = [];
    this.boardParts.forEach((o) => this.boardGroup.remove(o));
    const textures = this.sqTextures;
    const materials = this.ownedMaterials;
    const geometries = this.ownedGeometries;
    const pieces = [...this.pieces.values()];
    const mats = this.mats;
    this.boardParts = [];
    this.labels = [];
    this.sqMeshes = [];
    this.boardMaterials = [];
    this.sqTextures = [];
    this.ownedMaterials = [];
    this.ownedGeometries = [];
    this.pieces.clear();
    this.pieceGroup.clear();
    return () => {
      textures.forEach((t) => t.dispose());
      materials.forEach((m) => m.dispose());
      geometries.forEach((g) => g.dispose());
      pieces.forEach((e) => this.disposeBodies(e.group));
      disposeLuxeMaterials(mats);
    };
  }

  /** Free everything that belongs to the current style at once. */
  private disposeBoard(): void {
    this.retireBoard()();
  }

  /** A piece's own materials (the geometry is shared by every piece of its kind) */
  private disposeBodies(piece: THREE.Object3D): void {
    piece.traverse((o) => {
      const mat = (o as THREE.Mesh).material as THREE.Material | THREE.Material[] | undefined;
      for (const m of Array.isArray(mat) ? mat : mat ? [mat] : []) if (m.userData['body']) m.dispose();
    });
  }

  dispose(): void {
    this.disposed = true;
    window.clearTimeout(this.modelsTimer);
    cancelAnimationFrame(this.raf);
    this.listeners.forEach((off) => off());
    this.setArcs([]);
    this.carryLight?.dispose();
    this.afterDraw.splice(0).forEach((free) => free());
    if (this.warm) {
      this.disposeBodies(this.warm.group);
      this.warm.mats.forEach(disposeLuxeMaterials);
      this.warm = null;
    }
    this.disposeBoard();
    [this.dotGeo, this.ringGeo].forEach((g) => g.dispose());
    [this.dotMat, this.ringMat].forEach((m) => m.dispose());
    window.clearTimeout(this.deskTimer);
    this.deskProps.forEach(disposeProp);
    this.deskProps = [];
    this.deskParts?.dispose();
    this.panoTexture?.dispose();
    this.scene.background = null;
    this.envTexture.dispose();
    this.tableParts.forEach((p) => { p.geo.dispose(); p.mat.dispose(); p.tex?.dispose(); });
    this.tableParts = [];
    this.lights.forEach((l) => l.dispose());
    this.lights = [];
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

  /** The light of the room in the photo: a warm lamp on the left, a soft ceiling light, dark wood all around. */
  private buildStudyLighting(): THREE.Texture {
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    const room = new THREE.Scene();
    room.background = new THREE.Color(0x070402);
    const cards: Array<[number, number, number, number, number, [number, number, number]]> = [
      [-9, 3.5, 3, 3, 5, [7, 4.4, 2]],          // the brass lamp
      [1, 10, 1, 9, 6, [2.8, 2.3, 1.7]],        // ceiling light
      [0, 4, -10, 14, 4, [0.9, 0.55, 0.28]],    // shelves behind the table
      [9, 3, 0, 4, 4, [0.5, 0.34, 0.2]],
      [0, -3, 0, 14, 10, [0.62, 0.32, 0.13]],   // bounce off the polished table
    ];
    const geos: THREE.BufferGeometry[] = [];
    const mats: THREE.Material[] = [];
    cards.forEach(([x, y, z, w, h, col]) => {
      const geo = new THREE.PlaneGeometry(w, h);
      const mat = new THREE.MeshBasicMaterial({ side: THREE.DoubleSide });
      mat.color.setRGB(col[0], col[1], col[2]);
      const m = new THREE.Mesh(geo, mat);
      m.position.set(x, y, z);
      m.lookAt(0, 0, 0);
      room.add(m);
      geos.push(geo);
      mats.push(mat);
    });
    // the desk's room is a picture that arrives later: a stand-in of the same kind keeps the shaders valid for it
    const env = this.desk ? standInEnvironment(this.renderer) : pmrem.fromScene(room, 0.04).texture;
    pmrem.dispose();
    geos.forEach((g) => g.dispose());
    mats.forEach((m) => m.dispose());
    this.scene.environment = env;

    const add = <T extends THREE.Light>(l: T): T => {
      this.scene.add(l);
      this.lights.push(l);
      return l;
    };
    add(new THREE.AmbientLight(0x3a2416, 0.55));
    // a pool of warm light from up and to the left, where the lamp is: shadows fall to the right and back
    const key = add(new THREE.SpotLight(0xffd9a6, KEY_INTENSITY, 0, 0.62, 0.75, 2));
    key.position.copy(KEY_TILTED);
    this.keyLight = key;
    key.target.position.set(0, 0, 0);
    this.scene.add(key.target);
    key.castShadow = true;
    key.shadow.mapSize.set(2048, 2048);
    key.shadow.camera.near = 6;
    key.shadow.camera.far = 34;
    key.shadow.bias = -0.0004;
    key.shadow.normalBias = 0.02;
    // the lamp itself, close to the left side of the board
    const lamp = add(new THREE.PointLight(0xffb866, 140, 0, 2));
    lamp.position.set(-9, 2.6, 3);
    this.lampLight = lamp;
    // an edge of warm light from behind, so dark pieces keep their outline
    // (low and far behind: its glint on the glossy squares must land beyond the frame, not in the middle of the board)
    const rim = add(new THREE.DirectionalLight(0xffb070, 1.2));
    rim.position.set(9, 3.5, -13);
    const fill = add(new THREE.DirectionalLight(0x9aa6c4, 0.16));
    fill.position.set(6, 5, 6);
    return env;
  }

  /** Bring in the room behind the desk and the wood of its top, then let the scene be drawn. */
  private async loadDesk(): Promise<void> {
    const start = performance.now();
    const cold = desksLoaded++ === 0;
    // only the woods the board wears now: the other looks' photographs come in afterwards, before anyone switches
    const [pano, wood, props] = await Promise.all([
      loadPicture(DESK_PANO_URL), loadPicture(DESK_WOOD_URL), loadDeskProps(),
      hasPhotoWoods(this.look) ? loadPhotoWoods(this.look) : Promise.resolve(false),
    ]);
    // each step shows in DevTools' Performance panel (Timings), and in performance.getEntriesByType('measure')
    const filesIn = measure('desk: files', start);
    if (this.disposed) {
      // the pictures and models are shared with the scenes to come (see loadPicture, loadDeskProps): only this
      // scene's copies on the graphics card go
      pano?.dispose();
      wood?.dispose();
      props.forEach((p) => disposeProp(p.object));
      return;
    }
    whenIdle(() => {
      for (const look of Object.keys(LOOKS_3D) as Style3D[]) {
        if (look === this.look || !hasPhotoWoods(look)) continue;
        // a look switched to before they are in wears painted wood, and its photographs as soon as they arrive
        void loadPhotoWoods(look).then((ok) => { if (ok && !this.disposed && this.look === look) this.dress(look); });
      }
    });
    props.forEach((p) => this.placeProp(p));
    // the board is wearing painted wood, and has photographs to wear instead
    if (photoWoodsReady(this.look)) this.dress(this.look);
    if (wood) this.deskParts?.setWood(wood, this.renderer.capabilities.getMaxAnisotropy());
    if (pano) {
      pano.mapping = THREE.EquirectangularReflectionMapping;
      pano.colorSpace = THREE.SRGBColorSpace;
      this.panoTexture = pano;
      this.scene.background = pano;
      this.scene.backgroundBlurriness = DESK_BLUR;
      const env = roomEnvironment(this.renderer, pano);
      this.envTexture.dispose();
      this.envTexture = env;
      this.scene.environment = env;
    }
    const lit = measure('desk: props placed, room lighting', filesIn);
    // the props came in after the shaders were compiled: compile theirs too, before anything is drawn
    await this.compile();
    measure('desk: shaders', lit);
    if (this.disposed) return;
    window.clearTimeout(this.deskTimer);
    this.deskReady = true;
    this.opts.onDeskReady?.(performance.now() - start, cold);
  }

  /** Stand a prop on the desk. The lamp also becomes the light of the room: a flame inside it, and the lamp light moved to it. */
  private placeProp(p: LoadedProp): void {
    p.object.position.set(p.spec.at[0], TABLE_Y, p.spec.at[1]);
    this.scene.add(p.object);
    this.deskProps.push(p.object);
    if (p.spec.id !== 'lamp') return;

    const flame = new THREE.Vector3(p.spec.at[0], TABLE_Y + p.height * (p.spec.flame ?? 0.72), p.spec.at[1]);
    this.lampLight?.position.copy(flame);
    if (this.lampLight) this.lampLight.intensity = 45; // less than the photo's guess: this one is near the board, and its glint on the varnish blows out

    const core = new THREE.SphereGeometry(p.height * 0.022, 12, 8);
    const coreMat = new THREE.MeshBasicMaterial({ color: new THREE.Color(1, 0.72, 0.36).multiplyScalar(3) });
    const coreMesh = new THREE.Mesh(core, coreMat);
    coreMesh.scale.y = 1.6;
    coreMesh.position.copy(flame);
    this.scene.add(coreMesh);
    // a soft halo around it, as the glow of a flame seen through glass
    const c = document.createElement('canvas');
    c.width = c.height = 128;
    const g = c.getContext('2d')!;
    const ramp = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    ramp.addColorStop(0, 'rgba(255,196,110,0.9)');
    ramp.addColorStop(0.25, 'rgba(255,160,70,0.35)');
    ramp.addColorStop(1, 'rgba(255,140,50,0)');
    g.fillStyle = ramp;
    g.fillRect(0, 0, 128, 128);
    const haloTex = new THREE.CanvasTexture(c);
    const haloMat = new THREE.SpriteMaterial({ map: haloTex, blending: THREE.AdditiveBlending, transparent: true, depthWrite: false, toneMapped: false, opacity: 0.8 });
    const halo = new THREE.Sprite(haloMat);
    halo.scale.setScalar(p.height * 0.7);
    halo.position.copy(flame);
    this.scene.add(halo);
    this.tableParts.push({ geo: core, mat: coreMat }, { geo: new THREE.BufferGeometry(), mat: haloMat, tex: haloTex });
    this.deskProps.push(coreMesh, halo);
  }

  /**
   * The table is the photo behind the canvas: all the scene adds is what the board does to it. A shadow
   * catcher for the shadows of the lights, and a soft dark halo under the frame, so the board is set on the wood.
   */
  private buildTable(): void {
    if (this.desk) {
      // the desk is a surface of its own: it takes the shadows itself
      this.deskParts = buildDesk(TABLE_Y);
      this.scene.add(this.deskParts.object);
    } else {
      const plane = new THREE.PlaneGeometry(60, 60);
      plane.rotateX(-Math.PI / 2);
      const shade = new THREE.ShadowMaterial({ color: 0x120600, opacity: 0.6 });
      const catcher = new THREE.Mesh(plane, shade);
      catcher.position.y = TABLE_Y;
      catcher.receiveShadow = true;
      this.scene.add(catcher);
      this.tableParts.push({ geo: plane, mat: shade });
    }

    // halo: a blurred dark square, painted once
    const size = 256;
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const ctx = c.getContext('2d')!;
    ctx.shadowColor = 'rgba(8,3,0,1)';
    ctx.shadowBlur = 46;
    ctx.fillStyle = 'rgba(8,3,0,1)';
    ctx.fillRect(58, 58, size - 116, size - 116);
    const tex = new THREE.CanvasTexture(c);
    const haloGeo = new THREE.PlaneGeometry(13.4, 13.4);
    haloGeo.rotateX(-Math.PI / 2);
    const haloMat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, opacity: 0.55, depthWrite: false, toneMapped: false });
    const halo = new THREE.Mesh(haloGeo, haloMat);
    halo.position.y = TABLE_Y + 0.004;
    halo.renderOrder = 1;
    this.boardGroup.add(halo);
    this.tableParts.push({ geo: haloGeo, mat: haloMat, tex });
  }

  private buildBoard(): void {
    const bg = this.boardGroup;
    const look = LOOKS_3D[this.look];
    const tex = lookTextures(this.look, this.desk);
    const maxAniso = Math.min(8, this.renderer.capabilities.getMaxAnisotropy());
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
    // (on the desk the lines are lit a little from within: a gold inlay is brighter than the room's light makes it)
    const goldMat = new THREE.MeshStandardMaterial({ color: look.inlay, metalness: 1, roughness: 0.22, emissive: this.desk ? look.inlay : 0, emissiveIntensity: 0.2 });
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
        fitSquare(tx, !!tex.photo);
        tx.anisotropy = maxAniso; // the squares are seen at a slant
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
    const desk = this.desk;
    const size = desk ? 128 : 64;
    const c = document.createElement('canvas');
    c.width = c.height = size;
    const ctx = c.getContext('2d')!;
    ctx.fillStyle = LOOKS_3D[this.look].label;
    ctx.font = desk ? `600 ${size * 0.7}px "Cormorant Garamond", serif` : 'italic 500 40px "Cormorant Garamond", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, size / 2, size / 2);
    const tex = new THREE.CanvasTexture(c);
    tex.anisotropy = 4;
    if (desk) tex.colorSpace = THREE.SRGBColorSpace; // the lettering is gold, not a washed-out cream
    this.sqTextures.push(tex);

    if (desk) {
      // on the desk the letters lie on the frame, like lettering cut into it (an upright card would stand half
      // inside the frame), and keep their reading direction when the board is turned for Black
      const geo = new THREE.PlaneGeometry(0.42, 0.42);
      geo.rotateX(-Math.PI / 2);
      this.ownedGeometries.push(geo);
      const mat = new THREE.MeshBasicMaterial({ map: tex, transparent: true, depthWrite: false, toneMapped: false, polygonOffset: true, polygonOffsetFactor: -2 });
      this.ownedMaterials.push(mat);
      const label = new THREE.Mesh(geo, mat);
      label.position.set(x, y + 0.01, z);
      label.renderOrder = 2;
      this.boardGroup.add(label);
      this.boardParts.push(label);
      this.labels.push(label);
      return;
    }

    const mat = new THREE.SpriteMaterial({ map: tex, transparent: true, depthWrite: false });
    this.ownedMaterials.push(mat);
    const sprite = new THREE.Sprite(mat);
    sprite.position.set(x, y, z);
    sprite.scale.set(0.28, 0.28, 1);
    this.boardGroup.add(sprite);
    this.boardParts.push(sprite);
  }

  /** How far the board may turn sideways (rad) */
  private get yawLimit(): number {
    if (this.opts.tight) return YAW_LIMIT.tight;
    if (this.desk) return YAW_LIMIT.desk;
    if (this.study) return YAW_LIMIT.study;
    return this.opts.interactive ? YAW_LIMIT.interactive : YAW_LIMIT.still;
  }

  /** What the camera looks at in the tilted view, before the desk view shifts it */
  private get baseTarget(): THREE.Vector3 {
    return this.study ? STUDY_TARGET : PERSP_TARGET;
  }

  /** What the camera looks at in the tilted view: the desk view may have shifted it over the desk */
  private get target(): THREE.Vector3 {
    if (!this.desk) return this.baseTarget;
    return this.deskTarget.set(STUDY_TARGET.x + this.deskView.panX, STUDY_TARGET.y, STUDY_TARGET.z + this.deskView.panZ);
  }

  /** How far the board is tilted towards the viewer at rest (the study's board lies flat on its table) */
  private get basePitch(): number {
    return this.study ? 0 : BASE_PITCH;
  }

  /**
   * Back the camera off until the whole frame stays inside the safe area (and clear of the panels that
   * float over it), for both the tilted and the top-down view. The frame's centre goes to the middle of
   * the safe area.
   */
  private fitCamera(): void {
    const W = Math.max(this.viewW, 1);
    const H = Math.max(this.viewH, 1);
    const s = this.safe;
    const avoid = s.avoid ?? [];
    const margin = 0.03;
    const xmin = Math.min(-1 + (2 * s.left) / W + margin, -0.2);
    const xmax = Math.max(1 - (2 * s.right) / W - margin, 0.2);
    const ymin = Math.min(-1 + (2 * s.bottom) / H + margin, -0.2);
    const ymax = Math.max(1 - (2 * s.top) / H - margin, 0.2);
    const cx = (xmin + xmax) / 2;
    const cy = (ymin + ymax) / 2;
    if (Math.abs(cx) < 1e-4 && Math.abs(cy) < 1e-4) this.camera.clearViewOffset();
    else this.camera.setViewOffset(W, H, (-cx * W) / 2, (cy * H) / 2, W, H);

    // phones look down more steeply: the far ranks get taller, so a finger can find them
    const dir = this.study
      ? new THREE.Vector3(0, Math.sin(this.studyElevation), Math.cos(this.studyElevation))
      : new THREE.Vector3(0, 9.6, this.opts.tight ? 3.8 : 9.4).normalize();
    const target = this.baseTarget;
    const E = 4.97; // frame half-width, bevel included
    const yawMax = this.yawLimit;
    const base = this.basePitch;
    const pitches = this.opts.interactive || this.study ? [base] : [base - 0.03, base + 0.03];

    /** What must stay on screen: the frame's four corners, high and low (the study: its whole outline and the far pieces) */
    const outline = (euler: THREE.Euler, ys: (sz: number) => number[]): THREE.Vector3[] => {
      const out: THREE.Vector3[] = [];
      if (!this.study) {
        for (const sx of [-1, 1]) for (const sz of [-1, 1]) for (const y of ys(sz)) out.push(new THREE.Vector3(sx * E, y, sz * E).applyEuler(euler));
        return out;
      }
      const N = 14;
      for (let i = 0; i <= N; i++) {
        const t = -E + (2 * E * i) / N;
        for (const [x, z] of [[t, -E], [t, E], [-E, t], [E, t]]) out.push(new THREE.Vector3(x, 0.1, z).applyEuler(euler));
        out.push(new THREE.Vector3(t, -0.3, E).applyEuler(euler));                       // the front face of the frame
        if (Math.abs(t) < 4) out.push(new THREE.Vector3(t, ys(-1)[ys(-1).length - 1], -3.5).applyEuler(euler)); // the far pieces
      }
      return out;
    };
    const fits = (pts: THREE.Vector3[]): boolean => {
      this.camera.updateMatrixWorld();
      this.camera.updateProjectionMatrix();
      const v = new THREE.Vector3();
      return pts.every((p) => {
        v.copy(p).project(this.camera);
        if (v.x < xmin || v.x > xmax || v.y < ymin || v.y > ymax) return false;
        const px = (v.x * 0.5 + 0.5) * W;
        const py = (0.5 - v.y * 0.5) * H;
        return !avoid.some((r) => px > r.x - 10 && px < r.x + r.w + 10 && py > r.y - 10 && py < r.y + r.h + 10);
      });
    };

    const tilted: THREE.Vector3[] = [];
    for (const pitch of pitches) {
      for (const yaw of [-yawMax, 0, yawMax]) {
        tilted.push(...outline(new THREE.Euler(pitch, yaw, 0, 'XYZ'), (sz) => (this.study ? [this.pieceTop] : sz < 0 ? [-0.3, 1.6] : [-0.3, 1.6])));
      }
    }
    this.camera.up.set(0, 1, 0);
    // phones start closer, so the board can fill the width
    let d = this.opts.tight ? 3 : 6;
    for (; d < 40; d += 0.1) {
      this.camera.position.copy(target).addScaledVector(dir, d);
      this.camera.lookAt(target);
      if (fits(tilted)) break;
    }
    this.perspPos.copy(target).addScaledVector(dir, d);
    this.deskDist = d;

    // top-down: camera straight above, rank 8 at the top of the screen
    const flat: THREE.Vector3[] = [];
    for (const yaw of [0, Math.PI]) flat.push(...outline(new THREE.Euler(0, yaw, 0, 'XYZ'), () => [-0.3, 1.2]));
    this.camera.up.set(0, 0, -1);
    let top = 6;
    for (; top < 40; top += 0.1) {
      this.camera.position.set(0, top, 0);
      this.camera.lookAt(0, 0, 0);
      if (fits(flat)) break;
    }
    this.topDist = top;

    this.applyCamera();
  }

  private deskOffsetVector(): THREE.Vector3 {
    const [x, y, z] = deskOffset(this.deskView, this.deskDist);
    return new THREE.Vector3(x, y, z);
  }

  /** Phones look down more steeply: the far ranks get taller, so a finger can find them */
  private get studyElevation(): number {
    return this.opts.tight ? 0.95 : STUDY_ELEVATION;
  }

  /** Height of the tallest piece's top, where the far ranks reach (the study fits the pieces as well as the frame) */
  private get pieceTop(): number {
    return this.pieceSize * 1.5;
  }

  /** Place the camera between the tilted pose (topT = 0) and the top-down pose (topT = 1). */
  private applyCamera(): void {
    const t = this.topT;
    if (this.desk) this.perspPos.copy(this.target).add(this.deskOffsetVector());
    this.camera.position.lerpVectors(this.perspPos, new THREE.Vector3(0, this.topDist, 0), t);
    this.camera.up.lerpVectors(new THREE.Vector3(0, 1, 0), new THREE.Vector3(0, 0, -1), t).normalize();
    const tg = this.target;
    this.camera.lookAt(tg.x * (1 - t), tg.y * (1 - t), tg.z * (1 - t));
    this.camera.updateMatrixWorld();

    // seen from straight above, the glossy marble mirrors the bright ceiling light — tone the reflections down
    // (in the study the lamp is a bright card in the environment: the glossy frame would glare with it at full strength)
    const reflect = (this.study ? 0.7 : 1) * (1 - 0.85 * t);
    this.boardMaterials.forEach((m) => (m.envMapIntensity = reflect));

    // the same goes for the key light: from straight above, its mirror image on the varnish lands on the a1–b1 squares.
    // Swung out and lower, the glint falls beyond the frame (it lights the flat board less, so it gets a little stronger)
    if (this.keyLight) {
      this.keyLight.position.lerpVectors(KEY_TILTED, KEY_TOP, t);
      this.keyLight.intensity = KEY_INTENSITY * (1 + 0.5 * t);
    }
  }

  /* ── pieces ─────────────────────────────────────────────────────────────── */

  private get pieceSize(): number {
    return (this.opts.tight ? PHONE_PIECE_SIZE : 1) * (this.study ? STUDY_PIECE_SIZE : 1);
  }

  /** The models are in (or we gave up waiting): put the pieces on the board, near rank first. */
  private revealPieces(): void {
    window.clearTimeout(this.modelsTimer);
    this.modelsPending = false;
    if (!this.lastSquares) return; // nothing to draw yet: the first setPosition will do it directly
    this.setPosition(this.lastSquares, false);
    // the pieces stay hidden while their shaders compile (if they are not already), then come in
    this.pieceGroup.visible = false;
    void this.compile().then(() => {
      if (this.disposed) return;
      this.pieceGroup.visible = true;
      this.pieces.forEach((e) => {
        const g = e.group;
        g.scale.setScalar(0.01);
        this.tween(280, (t) => g.scale.setScalar(0.01 + 0.99 * (1 - Math.pow(1 - t, 3))), undefined, (7 - e.row) * 40 + e.col * 6);
      });
    });
  }

  /**
   * Make a pawn in each look's materials and keep them, hidden, for as long as the scene lives. A shader program is
   * deleted when the last material that uses it goes, so without these the style that is not on show would have to
   * compile its programs again each time it is picked. Compiling them with the rest also leaves them in the
   * graphics driver's cache. Hidden objects are compiled but never drawn.
   */
  private keepLooksWarm(): void {
    const group = new THREE.Group();
    group.visible = false;
    const sets = (Object.keys(LOOKS_3D) as Style3D[]).map((look) => {
      const mats = makeLuxeMaterials(LOOKS_3D[look]);
      group.add(buildPiece('P', true, mats));
      return mats;
    });
    this.scene.add(group);
    this.warm = { group, mats: sets };
  }

  /** Draw the warm pawns once, too small to see: some drivers only finish a program when it is first drawn. */
  private drawWarm(): void {
    const warm = this.warm?.group;
    if (!warm || this.disposed) return;
    warm.visible = true;
    warm.scale.setScalar(1e-4);
    this.renderer.render(this.scene, this.camera);
    warm.visible = false;
  }

  /**
   * Have every shader in the scene compiled, all at once and without holding up the page. Left alone, three
   * compiles each shader the first time something using it is drawn, one after the other, and the page stands
   * still for all of it (seconds, on some graphics drivers). Resolves when they are ready; at once if the
   * driver cannot compile in parallel (then the first draw compiles, as before), and after a while regardless.
   */
  private compile(): Promise<void> {
    try {
      this.renderer.compile(this.scene, this.camera);
    } catch (err) {
      console.warn('[luxe-board-scene] shader pre-compile failed, compiling on first draw', err);
      return Promise.resolve();
    }
    const giveUp = performance.now() + COMPILE_WAIT_MS;
    return new Promise((resolve) => {
      const poll = (): void => {
        // `isReady` is three's own check of the driver's "compile finished" flag
        const programs = (this.renderer.info.programs ?? []) as Array<{ isReady?: () => boolean }>;
        if (this.disposed || programs.every((p) => p.isReady?.() ?? true) || performance.now() > giveUp) resolve();
        else window.setTimeout(poll, 16);
      };
      poll();
    });
  }

  /** Stand every piece that has a model file in its model's shape (they were drawn before the file arrived). */
  private reshapePieces(): void {
    this.pieces.forEach((e) => {
      if (hasPieceModel(e.letter)) reshapePiece(e.group, e.letter, e.white, this.mats, this.pieceSize);
    });
    this.applyHighlights(); // the new bodies start without the selection glow
  }

  private addPiece(letter: PieceLetter, white: boolean, row: number, col: number, animated: boolean): PieceEntry {
    const group = buildPiece(letter, white, this.mats, letter === 'N' ? (white ? 2 : 0) : white ? 1 : -1, this.pieceSize);
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
    // a piece the player dropped on this square is already there: no hop
    if (!animated || Math.hypot(g.position.x - tx, g.position.z - tz) < 0.02) {
      g.position.set(tx, REST_Y, tz);
      return;
    }
    const sx = g.position.x;
    const sz = g.position.z;
    const hop = this.opts.hop ?? 0.95;
    const light = this.carryLight;
    entry.moving = true;
    entry.tween = this.tween(
      this.opts.moveMs ?? 380,
      (t) => {
        const e = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        g.position.x = sx + (tx - sx) * e;
        g.position.z = sz + (tz - sz) * e;
        g.position.y = REST_Y + Math.sin(t * Math.PI) * hop;
        if (light) {
          light.position.set(g.position.x, g.position.y + 0.35, g.position.z);
          light.intensity = 2.6 * Math.min(1, t * 4);
        }
      },
      () => {
        entry.moving = false;
        g.position.set(tx, REST_Y, tz);
        if (light) {
          // the colour stays on the square a moment after the piece lands, then goes
          const from = light.intensity;
          this.tween(700, (t) => (light.intensity = from * (1 - t) * (1 - t)));
        }
      },
    );
  }

  /** A glowing curve from one square to another, and a thin square outline where it lands. */
  private buildArc(from: Square, to: Square, color: number): { group: THREE.Group; mats: THREE.MeshBasicMaterial[]; geos: THREE.BufferGeometry[] } {
    const [fx, fz] = sqCenter(from.row, from.col);
    const [tx, tz] = sqCenter(to.row, to.col);
    const dist = Math.hypot(tx - fx, tz - fz);
    const y = 0.13;
    const curve = new THREE.QuadraticBezierCurve3(
      new THREE.Vector3(fx, y, fz),
      new THREE.Vector3((fx + tx) / 2, y + 0.18 + dist * 0.16, (fz + tz) / 2),
      new THREE.Vector3(tx, y, tz),
    );
    const mat = (): THREE.MeshBasicMaterial => new THREE.MeshBasicMaterial({
      color, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, toneMapped: false,
    });
    const core = new THREE.TubeGeometry(curve, 48, 0.018, 8, false);
    const glow = new THREE.TubeGeometry(curve, 48, 0.07, 10, false);
    const ring = new THREE.RingGeometry(0.4, 0.44, 4, 1, Math.PI / 4);
    ring.rotateX(-Math.PI / 2);
    ring.translate(tx, 0.115, tz);
    const mats = [mat(), mat(), mat()];
    const group = new THREE.Group();
    group.add(new THREE.Mesh(core, mats[0]), new THREE.Mesh(glow, mats[1]), new THREE.Mesh(ring, mats[2]));
    group.renderOrder = 2;
    return { group, mats, geos: [core, glow, ring] };
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

  /** Slide a piece (carried or not) to a point of the board and set it down. */
  private glide(entry: PieceEntry, tx: number, tz: number, ms = 190): void {
    const g = entry.group;
    const [sx, sy, sz] = [g.position.x, g.position.y, g.position.z];
    this.cancelTween(entry);
    entry.moving = true;
    entry.tween = this.tween(
      ms,
      (t) => {
        const e = 1 - Math.pow(1 - t, 3);
        g.position.set(sx + (tx - sx) * e, sy + (REST_Y - sy) * e, sz + (tz - sz) * e);
      },
      () => {
        entry.moving = false;
        g.position.set(tx, REST_Y, tz);
      },
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
      const isLastFrom = !!lastMove && sameSq(lastMove[0], row, col);
      const isLastTo = !!lastMove && sameSq(lastMove[1], row, col);
      const [color, emissive, intensity] = sameSq(check, row, col) ? [0xffb0a0, 0x8a1c14, 0.6]
        : sameSq(selected, row, col) ? [0xffd890, 0x6a4510, 0.5]
        : isHint && sameSq(this.hover, row, col) ? [0xfff2c8, 0xb07a14, 0.8]
        : isHint ? [0xf2dfb4, 0x3a2808, 0.3]
        : isLastTo ? [0xf4d77c, 0x9a6a10, 0.75]
        : isLastFrom ? [0xe9cf8e, 0x6a4a0c, 0.45]
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

    if (this.desk) this.bindDeskCamera(on);

    on(this.canvas, 'pointerdown', (e: PointerEvent) => {
      // a second finger turns the drag into a pinch, unless a piece is in the hand
      if (this.desk && e.pointerType === 'touch') {
        this.touches.set(e.pointerId, { x: e.clientX, y: e.clientY });
        if (this.touches.size === 2 && !this.held) {
          this.drag.down = false;
          this.drag.moved = true; // the lift of the fingers is not a tap
          this.pinchDist = this.touchSpan();
          return;
        }
      }
      const sq = e.button === 0 ? this.pick(e) : null;
      const entry = sq && this.opts.canDrag?.(sq) ? this.pieces.get(keyOf(sq.row, sq.col)) : undefined;
      if (sq && entry) {
        // a press on a piece of the player's is for moving it, not for turning the board
        this.held = { entry, from: sq, x: e.clientX, y: e.clientY, id: e.pointerId, active: false };
        return;
      }
      // on a phone the board stays put under the finger
      if (e.pointerType === 'touch' && this.opts.tight) return;
      this.drag = { down: true, moved: false, x: e.clientX, y: e.clientY, pan: this.desk && (e.button === 2 || e.shiftKey) };
    });
    on(window, 'pointermove', (e: PointerEvent) => {
      if (this.touches.has(e.pointerId)) this.touches.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (this.pinchDist > 0 && this.touches.size >= 2) {
        const span = this.touchSpan();
        this.deskGoal = zoomBy(this.deskGoal, span / this.pinchDist);
        this.pinchDist = span;
        return;
      }
      if (this.held) {
        this.moveHeld(e);
        return;
      }
      if (this.drag.down) {
        const dx = e.clientX - this.drag.x;
        const dy = e.clientY - this.drag.y;
        if (!this.drag.moved && Math.hypot(dx, dy) < 5) return;
        this.drag.moved = true;
        this.drag.x = e.clientX;
        this.drag.y = e.clientY;
        if (this.desk) {
          // the camera goes round the board; the board stays on its desk
          if (this.drag.pan) this.deskGoal = panDrag(this.deskGoal, dx, dy, this.panUnitsPerPx());
          else {
            const r = orbitDrag(this.deskGoal, this.topTarget, dx, dy);
            this.deskGoal = r.view;
            this.topTarget = r.top;
          }
        } else {
          this.dragYaw = Math.max(-this.yawLimit, Math.min(this.yawLimit, this.dragYaw + dx * 0.008));
          // dragging down raises the camera; all the way down looks straight at the board
          this.topTarget = Math.max(0, Math.min(1, this.topTarget + dy / TOP_DRAG_PX));
        }
        this.canvas.style.cursor = 'grabbing';
      } else if (e.target === this.canvas) {
        this.canvas.style.cursor = this.pick(e) ? 'pointer' : 'default';
      }
    });
    on(window, 'pointercancel', (e: PointerEvent) => {
      this.liftFinger(e);
      this.dropHeld(null);
    });
    on(window, 'pointerup', (e: PointerEvent) => {
      this.liftFinger(e);
      if (this.held) {
        if (e.pointerId === this.held.id) this.dropHeld(e);
        return;
      }
      // the board stays at whatever angle it was dragged to; just tell the page which side of halfway it is
      if (this.drag.down && this.drag.moved) this.opts.onTopViewChange?.(this.topTarget > 0.5);
      this.drag.down = false;
      this.canvas.style.cursor = 'default';
    });
    on(this.canvas, 'click', (e: MouseEvent) => {
      // the click that ends a drag is not a tap
      if (this.suppressClick) return;
      if (this.drag.moved) {
        this.drag.moved = false;
        return;
      }
      const sq = this.pick(e);
      if (sq && this.opts.onSquareClick) this.opts.onSquareClick(sq);
    });
  }

  /** The desk camera's inputs besides the drag: the wheel, Ctrl + the arrow keys, a double click on the bare desk. */
  private bindDeskCamera(on: (target: EventTarget, type: string, fn: (e: any) => void) => void): void {
    const canvas = this.canvas;
    on(canvas, 'contextmenu', (e: Event) => e.preventDefault()); // the right button pans
    const wheel = (e: WheelEvent): void => {
      e.preventDefault();
      // a wheel line is about 100 units, a trackpad's pinch a few: both feel the same
      this.deskGoal = zoomBy(this.deskGoal, Math.exp(-e.deltaY * 0.0012));
    };
    canvas.addEventListener('wheel', wheel, { passive: false });
    this.listeners.push(() => canvas.removeEventListener('wheel', wheel));
    // Ctrl + arrows (plus Shift to shift the view over the desk): the plain arrows step through the game's moves
    on(window, 'keydown', (e: KeyboardEvent) => {
      if (!e.ctrlKey || e.altKey || e.metaKey || (e.target as HTMLElement | null)?.closest?.('input, textarea, select, [contenteditable]')) return;
      const key = ({ ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' } as const)[e.key as 'ArrowLeft'];
      if (!key) return;
      e.preventDefault();
      this.deskGoal = nudge(this.deskGoal, key, e.shiftKey);
    });
    on(canvas, 'dblclick', (e: MouseEvent) => {
      if (!this.pick(e)) this.setView('reset');
    });
  }

  /** Distance between the first two fingers on the canvas */
  private touchSpan(): number {
    const [a, b] = [...this.touches.values()];
    return a && b ? Math.max(Math.hypot(a.x - b.x, a.y - b.y), 1) : 0;
  }

  private liftFinger(e: PointerEvent): void {
    if (this.touches.delete(e.pointerId) && this.touches.size < 2) this.pinchDist = 0;
  }

  /** How far the desk is from the camera for one pixel of the screen, at the board: what a pan drag moves by */
  private panUnitsPerPx(): number {
    return (2 * this.deskDist * this.deskGoal.zoom * Math.tan(THREE.MathUtils.degToRad(this.camera.fov / 2))) / Math.max(this.viewH, 1);
  }

  /** Carry the pressed piece after the pointer, once it has moved far enough to be a drag. */
  private moveHeld(e: PointerEvent): void {
    const h = this.held;
    if (!h || e.pointerId !== h.id) return;
    // the button was released outside the window: nobody holds the piece any more
    if (e.pointerType === 'mouse' && e.buttons === 0) {
      this.dropHeld(e);
      return;
    }
    if (!h.active) {
      if (Math.hypot(e.clientX - h.x, e.clientY - h.y) < DRAG_THRESHOLD_PX) return;
      h.active = true;
      this.cancelTween(h.entry);
      h.entry.moving = true;
      this.canvas.style.cursor = 'grabbing';
      this.opts.onDragStart?.(h.from);
    }
    const p = this.boardPoint(e, HELD_Y);
    if (p) h.entry.group.position.set(Math.max(-4.5, Math.min(4.5, p.x)), HELD_Y, Math.max(-4.5, Math.min(4.5, p.z)));
    const over = this.squareUnder(e);
    const moved = !over !== !this.hover || (!!over && !!this.hover && !sameSq(this.hover, over.row, over.col));
    if (moved) {
      this.hover = over;
      this.applyHighlights();
    }
  }

  /** The carried piece is let go (`e` null: the gesture was interrupted). */
  private dropHeld(e: PointerEvent | null): void {
    const h = this.held;
    this.held = null;
    if (!h || !h.active) return;   // never moved: a tap, and the click that follows selects the piece

    this.suppressClick = true;
    setTimeout(() => (this.suppressClick = false), 0);
    this.canvas.style.cursor = 'default';
    this.hover = null;
    this.applyHighlights();

    // the position moved on under the player's hand: nothing to put down
    if (this.pieces.get(keyOf(h.from.row, h.from.col)) !== h.entry) return;

    const to = e ? this.squareUnder(e) : null;
    const [fx, fz] = sqCenter(h.from.row, h.from.col);
    if (!to || sameSq(to, h.from.row, h.from.col)) {
      this.glide(h.entry, fx, fz);
      return;
    }
    const verdict = this.opts.onDrop?.(h.from, to) ?? 'reject';
    const [tx, tz] = verdict === 'reject' ? [fx, fz] : sqCenter(to.row, to.col);
    this.glide(h.entry, tx, tz, verdict === 'reject' ? 230 : 120);
  }

  /** Where the pointer's ray meets the horizontal plane at height `y`, in board coordinates. */
  private boardPoint(e: { clientX: number; clientY: number }, y: number): THREE.Vector3 | null {
    const rect = this.canvas.getBoundingClientRect();
    this.pointer.set(((e.clientX - rect.left) / rect.width) * 2 - 1, -((e.clientY - rect.top) / rect.height) * 2 + 1);
    this.raycaster.setFromCamera(this.pointer, this.camera);
    this.boardGroup.updateMatrixWorld();
    const ray = this.raycaster.ray.clone().applyMatrix4(this.boardGroup.matrixWorld.clone().invert());
    return ray.intersectPlane(new THREE.Plane(new THREE.Vector3(0, 1, 0), -y), new THREE.Vector3());
  }

  /** The board square under the pointer, whatever stands on it; null off the board. */
  private squareUnder(e: { clientX: number; clientY: number }): Square | null {
    const p = this.boardPoint(e, REST_Y);
    if (!p) return null;
    const col = Math.floor(p.x + 4);
    const row = Math.floor(p.z + 4);
    return col < 0 || col > 7 || row < 0 || row > 7 ? null : { row, col };
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
    if (!this.visible || document.hidden || this.compiling || !this.deskReady) return;

    const now = performance.now();
    const t = now * 0.001;

    if (this.intro) {
      this.introT = Math.min(this.introT + 0.014, 1);
      const e = 1 - Math.pow(1 - this.introT, 3);
      this.boardGroup.rotation.x = this.basePitch * e * (1 - this.topT);
      // the study's board is set down on its table; the studio's rises into view
      this.boardGroup.position.y = (this.study ? 0.9 : -2) * (1 - e);
      if (this.introT >= 1) this.intro = false;
    }

    const k = this.opts.interactive ? 0.15 : 0.07;
    this.pitch += (this.targetPitch - this.pitch) * k;
    // the desk's camera goes round the board; only the board's own turn (playing Black) turns the board on the desk
    const turn = this.desk ? 0 : (this.dragYaw + this.parallaxYaw) * (1 - this.topT);
    this.yaw += (this.flipYaw + turn - this.yaw) * k;
    if (!this.intro) {
      this.boardGroup.rotation.x = (this.basePitch + this.pitch) * (1 - this.topT);
    }

    let moved = false;
    if (this.desk && !sameView(this.deskView, this.deskGoal, 1e-4)) {
      this.deskView = easeView(this.deskView, this.deskGoal, 0.18);
      if (sameView(this.deskView, this.deskGoal, 1e-3)) this.deskView = { ...this.deskGoal };
      moved = true;
    }
    if (this.topT !== this.topTarget) {
      this.topT += (this.topTarget - this.topT) * 0.12;
      if (Math.abs(this.topTarget - this.topT) < 0.002) this.topT = this.topTarget;
      moved = true;
    }
    if (moved) this.applyCamera();
    this.boardGroup.rotation.y = this.yaw;
    if (this.desk) for (const l of this.labels) l.rotation.y = -this.yaw;

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
    this.afterDraw.splice(0).forEach((free) => free());
    if (!this.firstFrameDrawn) {
      this.firstFrameDrawn = true;
      this.opts.onFirstFrame?.(performance.now() - this.createdAt, this.cold);
    }
  };
}
