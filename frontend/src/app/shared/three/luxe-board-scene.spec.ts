import { Style3D } from '../../core/models';
import { LOOKS_3D } from './board-looks';
import { initialSquares, isWebGLAvailable, LuxeBoardScene } from './luxe-board-scene';
import { disposeLuxeMaterials, makeLuxeMaterials } from './luxe-pieces';

const LOOKS = Object.keys(LOOKS_3D) as Style3D[];

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

  it('can be disposed while its shaders are still compiling, and nothing fails afterwards', async () => {
    needsWebGL();
    const scene = sceneIn('marble-gold');
    scene.setLook('classic-wood');
    expect(() => scene.dispose()).not.toThrow();
    // the compile watcher wakes up after the scene is gone
    await new Promise((resolve) => setTimeout(resolve, 150));
  });
});
