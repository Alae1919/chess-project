// src/app/features/landing/scenes/king-stage.ts
// A solitary ivory king on a polished marble board, under one warm spotlight: the first and last
// shots of the landing page. Built from the same pieces and marble as the game's 3D board
// (luxe-pieces, board-looks), lit and framed like a film still. Framework-agnostic.
import * as THREE from 'three';
import { Reflector } from 'three/examples/jsm/objects/Reflector.js';
import { buildPiece, makeLuxeMaterials } from '../../../shared/three/luxe-pieces';
import { LOOKS_3D, lookTextures } from '../../../shared/three/board-looks';
import { makeMarble } from '../../../shared/three/marble';

/** A camera position and what it looks at */
interface Shot { pos: THREE.Vector3; target: THREE.Vector3; fov: number; }

/** Act I: low and close, a long lens on the king */
const CLOSE: Shot = { pos: new THREE.Vector3(2.3, 1.05, 4.6), target: new THREE.Vector3(0, 0.82, 0), fov: 26 };
/** Act V: high and back, the king small in its pool of light */
const WIDE: Shot = { pos: new THREE.Vector3(0.4, 6.6, 8.2), target: new THREE.Vector3(0, -0.15, 0.1), fov: 24 };

const SPOT_MAX = 85;
/** Where the lamp hangs, and where its beam meets the board */
const LAMP = new THREE.Vector3(1.6, 6.4, 3.4);
const POOL = new THREE.Vector3(0, 0, 0);
const BEAM_RADIUS = 1.2;
const BG = 0x030201;
const DUST = 260;

const ease = (t: number): number => t * t * (3 - 2 * t);

export class KingStage {
  private renderer: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(CLOSE.fov, 1, 0.1, 60);
  private spot = new THREE.SpotLight(0xffd3a0, 0, 0, 0.21, 0.75, 1);
  // narrow beams aimed at the king, so they shape it without lighting the rest of the board
  private rim = new THREE.SpotLight(0xffb070, 0, 0, 0.16, 0.6, 1);
  private fill = new THREE.SpotLight(0xffe4c4, 0, 0, 0.1, 0.7, 1);
  private fog = new THREE.Fog(BG, 4, 12);
  private reflector: Reflector;
  private cone: THREE.Mesh<THREE.ConeGeometry, THREE.ShaderMaterial>;
  private dust: THREE.Points<THREE.BufferGeometry, THREE.PointsMaterial>;
  private dustSeeds: Float32Array;
  private squareMats: THREE.MeshPhysicalMaterial[] = [];
  private kingMats: THREE.MeshPhysicalMaterial[] = [];
  private disposables: Array<{ dispose(): void }> = [];

  private light = 0;
  private shot = 0;
  private focusX = 0;
  private width = 1;
  private height = 1;
  private active = false;
  private raf = 0;
  private disposed = false;

  constructor(private canvas: HTMLCanvasElement, maxPixelRatio = 1.5) {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, maxPixelRatio));
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.scene.background = new THREE.Color(BG);
    this.scene.fog = this.fog;

    this.scene.environment = this.buildEnvironment();
    this.buildBoard();
    this.buildKing();
    this.reflector = this.buildReflector();
    this.cone = this.buildCone();
    [this.dust, this.dustSeeds] = this.buildDust();
    this.buildLights();

    this.resize(Math.max(canvas.clientWidth, 2), Math.max(canvas.clientHeight, 2));
    this.apply();
  }

  /* ── public API ─────────────────────────────────────────────────────── */

  /** 0 = dark, 1 = the spotlight full and the board out of the darkness */
  setLight(v: number): void {
    this.light = Math.max(0, Math.min(1, v));
    this.apply();
  }

  /** 0 = the close shot (act I), 1 = the high wide shot (act V) */
  setShot(v: number): void {
    this.shot = Math.max(0, Math.min(1, v));
    this.apply();
  }

  /** Move the king sideways on screen, as a fraction of the width (0.2 = a fifth of the way right) */
  setFocus(x: number): void {
    this.focusX = x;
    this.applyViewOffset();
  }

  /** Only an active stage renders */
  setActive(active: boolean): void {
    if (active === this.active || this.disposed) return;
    this.active = active;
    if (active) this.raf = requestAnimationFrame(this.frame);
    else cancelAnimationFrame(this.raf);
  }

  resize(width: number, height: number): void {
    if (width < 2 || height < 2) return;
    this.width = width;
    this.height = height;
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    // the reflection needs less detail than the picture: it is seen through the marble
    const pr = this.renderer.getPixelRatio() * 0.5;
    this.reflector.getRenderTarget().setSize(Math.round(width * pr), Math.round(height * pr));
    this.applyViewOffset();
    this.render();
  }

  /** Draw one frame now (for a still: reduced motion, or a poster capture) */
  render(): void {
    if (!this.disposed) this.renderer.render(this.scene, this.camera);
  }

  dispose(): void {
    this.disposed = true;
    cancelAnimationFrame(this.raf);
    this.scene.traverse((o) => {
      const mesh = o as THREE.Mesh;
      mesh.geometry?.dispose();
      const mat = mesh.material as THREE.Material | THREE.Material[] | undefined;
      (Array.isArray(mat) ? mat : mat ? [mat] : []).forEach((m) => m.dispose());
    });
    this.disposables.forEach((d) => d.dispose());
    this.reflector.dispose();
    this.renderer.dispose();
    this.renderer.forceContextLoss();
  }

  /* ── construction ───────────────────────────────────────────────────── */

  /** A dark studio with one warm card overhead, baked into an environment map for the gloss */
  private buildEnvironment(): THREE.Texture {
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    const studio = new THREE.Scene();
    studio.background = new THREE.Color(0x020101);
    const cards: Array<[number, number, number, number, number, number]> = [
      [0, 8, 1, 6, 3, 0xffd9b0],
      [-6, 2, 3, 2, 5, 0x3a2a1c],
      [5, 3, -4, 3, 5, 0x6a4a2c],
    ];
    for (const [x, y, z, w, h, c] of cards) {
      const m = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ color: c, side: THREE.DoubleSide }));
      m.position.set(x, y, z);
      m.lookAt(0, 0, 0);
      studio.add(m);
    }
    const env = pmrem.fromScene(studio, 0.04).texture;
    studio.traverse((o) => {
      const m = o as THREE.Mesh;
      m.geometry?.dispose();
      (m.material as THREE.Material | undefined)?.dispose();
    });
    pmrem.dispose();
    this.disposables.push(env);
    return env;
  }

  /** Ivory and charcoal marble squares, slightly see-through so the mirror under them reads as polish */
  private buildBoard(): void {
    const tex = lookTextures('ebony-ivory');
    const geo = new THREE.PlaneGeometry(0.995, 0.995);
    geo.rotateX(-Math.PI / 2);
    let seed = 7;
    const rnd = (): number => ((seed = (seed * 16807) % 2147483647) / 2147483647);
    for (let r = 0; r < 8; r++) {
      for (let c = 0; c < 8; c++) {
        const light = (r + c) % 2 === 0;
        const map = (light ? tex.light : tex.dark).clone();
        map.needsUpdate = true;
        map.repeat.set(0.25, 0.25);
        map.offset.set(rnd() * 0.75, rnd() * 0.75);
        map.center.set(0.5, 0.5);
        map.rotation = Math.floor(rnd() * 4) * (Math.PI / 2);
        this.disposables.push(map);
        const mat = new THREE.MeshPhysicalMaterial({
          map, roughness: 0.18, metalness: 0, clearcoat: 1, clearcoatRoughness: 0.05,
          transparent: true, opacity: light ? 0.93 : 0.86,
        });
        this.squareMats.push(mat);
        const sq = new THREE.Mesh(geo, mat);
        // the king stands on e4's centre, at the origin
        sq.position.set(c - 4, 0, r - 4);
        sq.receiveShadow = true;
        this.scene.add(sq);
      }
    }
  }

  private buildKing(): void {
    const look = LOOKS_3D['ebony-ivory'];
    const mats = makeLuxeMaterials(look);
    // ivory marble with soft grey veins, instead of the game's plain ivory
    const veins = new THREE.CanvasTexture(
      makeMarble(512, '#efe7d7', 'rgba(70,66,62,', ['rgba(255,250,240,.55)', 'rgba(190,178,160,.3)'], 17, 1),
    );
    veins.colorSpace = THREE.SRGBColorSpace;
    veins.wrapS = veins.wrapT = THREE.RepeatWrapping;
    veins.repeat.set(1.3, 0.9);
    this.disposables.push(veins);
    mats.gold.map = veins;
    mats.gold.color.setHex(0xffffff);
    mats.gold.roughness = 0.28;
    mats.gold.clearcoat = 1;
    mats.gold.clearcoatRoughness = 0.12;
    mats.gold.sheen = 0.4;
    mats.gold.sheenColor = new THREE.Color(0xfff1dc);

    const king = buildPiece('K', true, mats, 1, 1.15);
    king.traverse((o) => {
      const mat = (o as THREE.Mesh).material as THREE.MeshPhysicalMaterial | undefined;
      if (mat?.isMeshPhysicalMaterial) this.kingMats.push(mat);
    });
    Object.values(mats).forEach((m) => this.disposables.push(m));
    this.scene.add(king);
  }

  private buildReflector(): Reflector {
    const r = new Reflector(new THREE.PlaneGeometry(9, 9), {
      textureWidth: 512, textureHeight: 512, color: 0x6a645c, clipBias: 0.002,
    });
    r.rotateX(-Math.PI / 2);
    r.position.set(-0.5, -0.004, -0.5);
    this.scene.add(r);
    return r;
  }

  /** The beam itself, faintly visible in the dark air: brightest down its middle, fading at its edges and top */
  private buildCone(): THREE.Mesh<THREE.ConeGeometry, THREE.ShaderMaterial> {
    const length = LAMP.distanceTo(POOL);
    const geo = new THREE.ConeGeometry(BEAM_RADIUS, length, 64, 1, true);
    const mat = new THREE.ShaderMaterial({
      uniforms: { uColor: { value: new THREE.Color(0xffc98a) }, uOpacity: { value: 0 } },
      vertexShader: `
        varying float vY;
        varying vec3 vN;
        varying vec3 vV;
        void main() {
          vY = uv.y;
          vec4 mv = modelViewMatrix * vec4(position, 1.0);
          vN = normalize(normalMatrix * normal);
          vV = normalize(-mv.xyz);
          gl_Position = projectionMatrix * mv;
        }`,
      fragmentShader: `
        uniform vec3 uColor;
        uniform float uOpacity;
        varying float vY;
        varying vec3 vN;
        varying vec3 vV;
        void main() {
          float facing = pow(abs(dot(normalize(vN), normalize(vV))), 2.2);
          float along = smoothstep(1.0, 0.25, vY) * smoothstep(0.0, 0.08, vY);
          gl_FragColor = vec4(uColor, facing * along * uOpacity);
        }`,
      transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide,
    });
    const cone = new THREE.Mesh(geo, mat);
    // apex at the lamp, base on the board
    cone.position.lerpVectors(POOL, LAMP, 0.5);
    cone.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), LAMP.clone().sub(POOL).normalize());
    this.scene.add(cone);
    return cone;
  }

  /** Dust drifting through the beam */
  private buildDust(): [THREE.Points<THREE.BufferGeometry, THREE.PointsMaterial>, Float32Array] {
    const pos = new Float32Array(DUST * 3);
    const seeds = new Float32Array(DUST * 4);
    let s = 3;
    const rnd = (): number => ((s = (s * 16807) % 2147483647) / 2147483647);
    // scattered through the lower part of the beam
    const axis = LAMP.clone().sub(POOL);
    const side = new THREE.Vector3(1, 0, 0).cross(axis).normalize();
    const side2 = axis.clone().cross(side).normalize();
    for (let i = 0; i < DUST; i++) {
      const t = 0.04 + rnd() * 0.55;
      const r = BEAM_RADIUS * (1 - t) * Math.sqrt(rnd()) * 0.9;
      const a = rnd() * Math.PI * 2;
      const p = POOL.clone().addScaledVector(axis, t).addScaledVector(side, Math.cos(a) * r).addScaledVector(side2, Math.sin(a) * r);
      seeds.set([p.x, p.y, p.z, rnd() * 100], i * 4);
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
    const dot = document.createElement('canvas');
    dot.width = dot.height = 32;
    const ctx = dot.getContext('2d')!;
    const g = ctx.createRadialGradient(16, 16, 0, 16, 16, 16);
    g.addColorStop(0, 'rgba(255,255,255,1)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 32, 32);
    const map = new THREE.CanvasTexture(dot);
    this.disposables.push(map);
    const mat = new THREE.PointsMaterial({
      color: 0xffd9a8, map, size: 0.022, transparent: true, opacity: 0, depthWrite: false,
      blending: THREE.AdditiveBlending, fog: false,
    });
    const points = new THREE.Points(geo, mat);
    points.frustumCulled = false;
    this.scene.add(points);
    return [points, seeds];
  }

  private buildLights(): void {
    // high and a little in front, so the light falls on the king's face as well as its crown
    this.spot.position.copy(LAMP);
    this.spot.target.position.set(0, 0.45, 0);
    this.spot.castShadow = true;
    this.spot.shadow.mapSize.set(1024, 1024);
    this.spot.shadow.bias = -0.0004;
    this.spot.shadow.camera.near = 2;
    this.spot.shadow.camera.far = 12;
    this.scene.add(this.spot, this.spot.target);

    // a warm edge from behind, to lift the king's outline off the black
    this.rim.position.set(-2.5, 3, -5);
    this.rim.target.position.set(0, 0.8, 0);
    this.scene.add(this.rim, this.rim.target);
    // a breath of light from the camera's side, so the body under the collar is not a black band
    this.fill.position.set(2.2, 3.2, 5.2);
    this.fill.target.position.set(0, 0.8, 0);
    this.scene.add(this.fill, this.fill.target);
    this.scene.add(new THREE.HemisphereLight(0x2a1f16, 0x000000, 0.08));
  }

  /* ── state ──────────────────────────────────────────────────────────── */

  private apply(): void {
    const e = ease(this.light);
    this.spot.intensity = SPOT_MAX * e;
    this.rim.intensity = 30 * e;
    this.fill.intensity = 9 * e;
    // seen from above, the beam would wash the whole frame, and the board would mirror the studio:
    // the wide shot keeps only the pool of light
    this.cone.material.uniforms['uOpacity'].value = 0.04 * e * (1 - 0.9 * this.shot);
    this.dust.material.opacity = 0.75 * e * (1 - 0.5 * this.shot);
    this.squareMats.forEach((m) => (m.envMapIntensity = 0.35 * e * (1 - 0.9 * this.shot)));
    this.kingMats.forEach((m) => (m.envMapIntensity = 0.25 + 0.55 * e));

    const k = ease(this.shot);
    this.camera.position.lerpVectors(CLOSE.pos, WIDE.pos, k);
    this.camera.fov = CLOSE.fov + (WIDE.fov - CLOSE.fov) * k;
    const target = new THREE.Vector3().lerpVectors(CLOSE.target, WIDE.target, k);
    this.camera.lookAt(target);
    this.camera.updateProjectionMatrix();
    // the dark closes in just behind the king, and draws back as the light comes up
    const d = this.camera.position.distanceTo(target);
    this.fog.near = d - 1.2;
    this.fog.far = d + 1.5 + 6 * e;
    if (!this.active) this.render();
  }

  private applyViewOffset(): void {
    if (Math.abs(this.focusX) < 1e-4) this.camera.clearViewOffset();
    else this.camera.setViewOffset(this.width, this.height, -this.focusX * this.width, 0, this.width, this.height);
    this.camera.updateProjectionMatrix();
  }

  private frame = (now: number): void => {
    if (this.disposed || !this.active) return;
    this.raf = requestAnimationFrame(this.frame);
    if (document.hidden) return;
    const t = now * 0.001;
    const pos = this.dust.geometry.attributes['position'] as THREE.BufferAttribute;
    for (let i = 0; i < DUST; i++) {
      const [x, y, z, p] = this.dustSeeds.subarray(i * 4, i * 4 + 4);
      const drift = (t * 0.035 + p) % 1;
      pos.setXYZ(i,
        x + Math.sin(t * 0.21 + p) * 0.08,
        y + drift * 0.6 - 0.3,
        z + Math.cos(t * 0.17 + p * 1.3) * 0.08);
    }
    pos.needsUpdate = true;
    this.render();
  };
}
