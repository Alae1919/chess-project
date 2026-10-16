import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { Style3D } from '../../core/models';
import { LOOKS_3D } from './board-looks';
import { forgetDeskModels } from './desk-props';
import { initialSquares, isWebGLAvailable, LuxeBoardScene } from './luxe-board-scene';
import { disposeLuxeMaterials, makeLuxeMaterials } from './luxe-pieces';
import { DESK_PROP_URLS } from './scene-assets';

const LOOKS = Object.keys(LOOKS_3D) as Style3D[];

/** Jasmine counts an async spec's own work against its 5 s: building a scene is several seconds on a CI runner with no GPU */
const SLOW_SPEC_MS = 60_000;

describe('disposeLuxeMaterials', () => {
  it('is exercised on a look with stone pieces, whose materials carry a plain description of the marble', () => {
    expect(LOOKS_3D['marble-gold'].stone).toBeDefined();
    expect(makeLuxeMaterials(LOOKS_3D['marble-gold']).stone).toBeDefined();
  });

  for (const look of LOOKS) {
    it(`frees the four materials of ${look}, and only those`, () => {
      const mats = makeLuxeMaterials(LOOKS_3D[look]);
      const freed = [mats.gold, mats.lacquer, mats.trim, mats.groove].map((m) => spyOn(m, 'dispose'));
      expect(() => disposeLuxeMaterials(mats)).not.toThrow();
      freed.forEach((spy) => expect(spy).toHaveBeenCalledTimes(1));
    });
  }
});

describe('LuxeBoardScene', () => {
  const canvases: HTMLCanvasElement[] = [];
  let webgl: boolean | undefined;

  afterEach(() => {
    canvases.splice(0).forEach((c) => c.remove());
  });

  /** A scene on a canvas of its own: a disposed scene loses its context, so a canvas serves only one. */
  const sceneIn = (look: Style3D): LuxeBoardScene => {
    const canvas = document.createElement('canvas');
    canvas.style.cssText = 'width: 320px; height: 240px';
    document.body.appendChild(canvas);
    canvases.push(canvas);
    const scene = new LuxeBoardScene(canvas, { interactive: true, look });
    scene.setPosition(initialSquares(), false);
    return scene;
  };

  /** Skips the spec in a browser without WebGL */
  const needsWebGL = (): void => {
    webgl ??= isWebGLAvailable();
    if (!webgl) pending('no WebGL in this browser');
  };

  it('changes style from every look to every other, the stone one included', () => {
    needsWebGL();
    for (const from of LOOKS) {
      const scene = sceneIn(from);
      for (const to of [...LOOKS, from]) expect(() => scene.setLook(to)).withContext(`${from} -> ${to}`).not.toThrow();
      expect(() => scene.dispose()).withContext(`dispose after ${from}`).not.toThrow();
    }
  });

  it('can be disposed whichever look it shows (leaving a page must not fail)', () => {
    needsWebGL();
    for (const look of LOOKS) expect(() => sceneIn(look).dispose()).withContext(look).not.toThrow();
  });

  describe('in the desk view', () => {
    const deskIn = (): LuxeBoardScene => {
      const canvas = document.createElement('canvas');
      canvas.style.cssText = 'width: 320px; height: 240px';
      document.body.appendChild(canvas);
      canvases.push(canvas);
      const scene = new LuxeBoardScene(canvas, { interactive: true, look: 'classic-wood', environment: 'desk' });
      scene.setPosition(initialSquares(), false);
      return scene;
    };

    it('is built and disposed without failing, whether or not its pictures have arrived', async () => {
      needsWebGL();
      const early = deskIn();
      expect(() => early.dispose()).not.toThrow(); // the pictures are still on their way
      const late = deskIn();
      await new Promise((resolve) => setTimeout(resolve, 400));
      expect(() => late.dispose()).not.toThrow();
    }, SLOW_SPEC_MS);

    it('sends the camera to a named view, and back', () => {
      needsWebGL();
      const scene = deskIn();
      const goal = () => (scene as unknown as { deskGoal: { yaw: number; elev: number } }).deskGoal;
      scene.setView('side');
      expect(goal().yaw).toBeGreaterThan(0);
      scene.setView('low');
      expect(goal().elev).toBeLessThan(0.64);
      scene.setView('reset');
      expect(goal().yaw).toBe(0);
      expect(goal().elev).toBeCloseTo(0.64, 9);
      scene.dispose();
    });

    it('tells the page when the overhead view is asked for, and when a view takes it back', () => {
      needsWebGL();
      const reported: boolean[] = [];
      const canvas = document.createElement('canvas');
      canvas.style.cssText = 'width: 320px; height: 240px';
      document.body.appendChild(canvas);
      canvases.push(canvas);
      const scene = new LuxeBoardScene(canvas, { interactive: true, environment: 'desk', onTopViewChange: (t) => reported.push(t) });
      scene.setView('overhead');
      scene.setView('player');
      scene.setView('low'); // already out of the overhead view: nothing to report
      expect(reported).toEqual([true, false]);
      scene.dispose();
    });

    it('ignores the named views when the camera is fixed (the photo view)', () => {
      needsWebGL();
      const canvas = document.createElement('canvas');
      canvas.style.cssText = 'width: 320px; height: 240px';
      document.body.appendChild(canvas);
      canvases.push(canvas);
      const reported: boolean[] = [];
      const scene = new LuxeBoardScene(canvas, { interactive: true, environment: 'study', onTopViewChange: (t) => reported.push(t) });
      expect(() => scene.setView('overhead')).not.toThrow();
      expect(reported).toEqual([]);
      scene.dispose();
    });
  });

  describe('drawing only when something changed', () => {
    const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
    /** Frames the scene has drawn since it was made (three counts each render() call) */
    const drawn = (scene: LuxeBoardScene): number =>
      (scene as unknown as { renderer: THREE.WebGLRenderer }).renderer.info.render.frame;
    /**
     * Over `ms`: the frames the scene drew, and the animation frames the browser offered (a slow runner offers far
     * fewer than 60 a second, so the counts are compared with each other, not with the clock)
     */
    const watch = async (scene: LuxeBoardScene, ms: number): Promise<{ drawn: number; offered: number }> => {
      const before = drawn(scene);
      let offered = 0;
      let on = true;
      const count = () => { if (on) { offered++; requestAnimationFrame(count); } };
      requestAnimationFrame(count);
      await sleep(ms);
      on = false;
      return { drawn: drawn(scene) - before, offered };
    };
    const drawnIn = async (scene: LuxeBoardScene, ms: number): Promise<number> => (await watch(scene, ms)).drawn;
    /**
     * Waits for the scene to come to rest (its intro played, its pieces in): at most one frame drawn in a window of at
     * least 10 offered, which is the once-a-second redraw at most. False if it never does: a board that draws every
     * frame forever.
     */
    const cameToRest = async (scene: LuxeBoardScene, withinMs = 30_000): Promise<boolean> => {
      const end = performance.now() + withinMs;
      while (performance.now() < end) {
        const w = await watch(scene, 400);
        if (w.offered >= 10 && w.drawn <= 1) return true;
      }
      return false;
    };
    const noMarks = { selected: null, hints: [], lastMove: null, check: null };
    /**
     * A new scene, once it has drawn its first frame (its shaders compile first: seconds, without a GPU) and come to
     * rest. Fails the spec if it never rests: a board that draws every frame forever.
     */
    const restingScene = async (): Promise<LuxeBoardScene> => {
      const scene = sceneIn('marble-gold');
      const end = performance.now() + 30_000;
      while (!(scene as unknown as { firstFrameDrawn: boolean }).firstFrameDrawn && performance.now() < end) await sleep(50);
      expect(drawn(scene)).withContext('the first frame is drawn').toBeGreaterThan(0);
      expect(await cameToRest(scene)).withContext('a board at rest still drew every frame').toBeTrue();
      return scene;
    };

    it('stops drawing once nothing moves, and draws again after each kind of change', async () => {
      needsWebGL();
      const scene = await restingScene();

      const changes: Array<[string, () => void]> = [
        ['a new size', () => scene.resize(330, 250)],
        ['another look', () => scene.setLook('classic-wood')],
        ['a last move marked', () => scene.setHighlights({ ...noMarks, lastMove: [{ row: 6, col: 4 }, { row: 4, col: 4 }] })],
        ['the pointer over the board', () => canvases[canvases.length - 1].dispatchEvent(new PointerEvent('pointermove', { clientX: 160, clientY: 120, bubbles: true }))],
        ['the board turned round', () => scene.setFlipped(true)],
      ];
      for (const [what, change] of changes) {
        expect(await cameToRest(scene)).withContext(`at rest before ${what}`).toBeTrue();
        change();
        expect(await drawnIn(scene, 200)).withContext(what).toBeGreaterThan(0);
      }
      scene.dispose();
    }, SLOW_SPEC_MS);

    it('animates a move to its end, then rests', async () => {
      needsWebGL();
      const scene = await restingScene();
      const moved = initialSquares().map((row) => [...row]);
      moved[4][4] = moved[6][4]; // a pawn two squares on
      moved[6][4] = null;
      scene.setPosition(moved);
      // a hop of about half a second: every frame of it is drawn
      const hop = await watch(scene, 250);
      expect(hop.drawn).withContext(JSON.stringify(hop)).toBeGreaterThanOrEqual(hop.offered - 2);
      expect(hop.drawn).toBeGreaterThan(0);
      expect(await cameToRest(scene, 15_000)).withContext('rests after the move').toBeTrue();
      scene.dispose();
    }, SLOW_SPEC_MS);

    it('keeps drawing while a piece is selected (its targets pulse), and rests once it is not', async () => {
      needsWebGL();
      const scene = await restingScene();
      scene.setHighlights({ ...noMarks, selected: { row: 6, col: 4 }, hints: [{ row: 5, col: 4 }, { row: 4, col: 4 }] });
      await sleep(300); // the lifted piece settles
      const selected = await watch(scene, 600);
      // every frame offered is drawn (a frame may slip past between two counts)
      expect(selected.drawn).withContext(`the markers pulse: ${JSON.stringify(selected)}`).toBeGreaterThanOrEqual(selected.offered - 2);
      scene.setHighlights(noMarks);
      expect(await cameToRest(scene, 15_000)).withContext('rests once deselected').toBeTrue();
      scene.dispose();
    }, SLOW_SPEC_MS);
  });

  describe('bringing in the desk', () => {
    // the room's props (lamp, books, bookcases...) come from a fake loader the spec holds back; the desk itself
    // arrives at once. The models are cached for the page's life: start and end with an empty cache, so that these
    // fakes reach no other spec
    beforeEach(forgetDeskModels);
    afterEach(forgetDeskModels);

    interface Held { geometry: THREE.BufferGeometry; release: () => void }
    let held: Held[];

    const fakeModel = (): THREE.Group => {
      const group = new THREE.Group();
      group.add(new THREE.Mesh(new THREE.BoxGeometry(4, 2, 4), new THREE.MeshStandardMaterial()));
      return group;
    };

    beforeEach(() => {
      held = [];
      const props = new Set<string>(Object.values(DESK_PROP_URLS));
      const real = GLTFLoader.prototype.loadAsync;
      spyOn(GLTFLoader.prototype, 'loadAsync').and.callFake(function (this: GLTFLoader, url: string) {
        // the chess pieces load through GLTFLoader too: theirs are the real files, and nothing holds them back
        if (!props.has(url)) return real.call(this, url);
        const scene = fakeModel();
        if (url === DESK_PROP_URLS.desk) return Promise.resolve({ scene } as any);
        return new Promise((resolve) => {
          const geometry = ((scene.children[0] as THREE.Mesh).geometry);
          held.push({ geometry, release: () => resolve({ scene } as any) });
        });
      });
    });

    const deskWith = (onDeskReady: () => void): LuxeBoardScene => {
      const canvas = document.createElement('canvas');
      canvas.style.cssText = 'width: 320px; height: 240px';
      document.body.appendChild(canvas);
      canvases.push(canvas);
      const scene = new LuxeBoardScene(canvas, { interactive: true, look: 'marble-gold', environment: 'desk', onDeskReady });
      scene.setPosition(initialSquares(), false);
      return scene;
    };
    /** Resolves once `test` holds, polling; false after `ms` */
    const until = async (test: () => boolean, ms = 30_000): Promise<boolean> => {
      const end = performance.now() + ms;
      while (performance.now() < end) {
        if (test()) return true;
        await new Promise((resolve) => setTimeout(resolve, 25));
      }
      return test();
    };
    const propsOf = (scene: LuxeBoardScene) => (scene as unknown as { deskProps: THREE.Object3D[] }).deskProps;

    it('draws the desk without waiting for the room\'s props, which come in once their shaders are ready', async () => {
      needsWebGL();
      let ready = false;
      const scene = deskWith(() => (ready = true));
      expect(await until(() => ready)).withContext('the desk is ready while the props are still on their way').toBeTrue();
      expect(held.length).withContext('the room\'s props were asked for').toBeGreaterThan(0);
      const deskOnly = propsOf(scene).length;
      expect(deskOnly).withContext('only the desk is placed').toBe(1);

      held.forEach((h) => h.release());
      expect(await until(() => propsOf(scene).length > deskOnly)).withContext('the props are placed').toBeTrue();
      const added = propsOf(scene).slice(deskOnly);
      expect(await until(() => added.every((o) => o.visible))).withContext('and shown, once compiled').toBeTrue();
      scene.dispose();
    }, SLOW_SPEC_MS);

    it('frees the props that arrive after the scene is gone', async () => {
      needsWebGL();
      let ready = false;
      const scene = deskWith(() => (ready = true));
      expect(await until(() => ready)).toBeTrue();
      scene.dispose(); // the player left before the room came in
      const freed = held.map((h) => spyOn(h.geometry, 'dispose'));
      held.forEach((h) => h.release());
      expect(await until(() => freed.every((spy) => spy.calls.any()), 5_000)).withContext('every late prop is freed').toBeTrue();
      expect(propsOf(scene).length).withContext('and none is placed').toBe(0);
    }, SLOW_SPEC_MS);
  });

  it('can be disposed while its shaders are still compiling, and nothing fails afterwards', async () => {
    needsWebGL();
    const scene = sceneIn('marble-gold');
    scene.setLook('classic-wood');
    expect(() => scene.dispose()).not.toThrow();
    // the compile watcher wakes up after the scene is gone
    await new Promise((resolve) => setTimeout(resolve, 150));
  }, SLOW_SPEC_MS);
});
