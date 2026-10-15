import { Style3D } from '../../core/models';
import { LOOKS_3D } from './board-looks';
import { initialSquares, isWebGLAvailable, LuxeBoardScene } from './luxe-board-scene';
import { disposeLuxeMaterials, makeLuxeMaterials } from './luxe-pieces';

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

  it('can be disposed while its shaders are still compiling, and nothing fails afterwards', async () => {
    needsWebGL();
    const scene = sceneIn('marble-gold');
    scene.setLook('classic-wood');
    expect(() => scene.dispose()).not.toThrow();
    // the compile watcher wakes up after the scene is gone
    await new Promise((resolve) => setTimeout(resolve, 150));
  }, SLOW_SPEC_MS);
});
