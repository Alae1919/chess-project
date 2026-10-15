import {
  clampDeskView, DESK_HOME, DESK_LIMITS, DESK_PRESETS, deskOffset, DeskView, easeView, nudge, orbitDrag, panDrag, sameView, zoomBy,
} from './desk-camera';

const inside = (v: DeskView): boolean => {
  const L = DESK_LIMITS;
  return Math.abs(v.yaw) <= L.yaw + 1e-9 && v.elev >= L.elevMin - 1e-9 && v.elev <= L.elevMax + 1e-9
    && v.zoom >= L.zoomMin - 1e-9 && v.zoom <= L.zoomMax + 1e-9 && Math.abs(v.panX) <= L.panX + 1e-9 && Math.abs(v.panZ) <= L.panZ + 1e-9;
};

describe('the desk camera', () => {
  it('starts at the reference view, which is inside its own limits', () => {
    expect(inside(DESK_HOME)).toBeTrue();
    for (const p of Object.values(DESK_PRESETS)) expect(inside(p)).toBeTrue();
  });

  it('stands in front of the board and above the desk at the home view', () => {
    const [x, y, z] = deskOffset(DESK_HOME, 10);
    expect(x).toBeCloseTo(0, 9);
    expect(y).toBeCloseTo(10 * Math.sin(DESK_HOME.elev), 9);
    expect(z).toBeCloseTo(10 * Math.cos(DESK_HOME.elev), 9);
  });

  it('can never be put outside its limits, whatever it is asked', () => {
    const wild = clampDeskView({ yaw: 9, elev: -4, zoom: 0.01, panX: -50, panZ: 50 });
    expect(inside(wild)).toBeTrue();
    expect(wild.elev).toBeGreaterThan(0); // never under the desk
  });

  it('keeps the camera above the desk and at least as far as the nearest zoom, at every corner of the limits', () => {
    const L = DESK_LIMITS;
    for (const yaw of [-L.yaw, L.yaw]) for (const elev of [L.elevMin, L.elevMax]) for (const zoom of [L.zoomMin, L.zoomMax]) {
      const [x, y, z] = deskOffset({ yaw, elev, zoom, panX: 0, panZ: 0 }, 10);
      expect(y).toBeGreaterThan(0);
      expect(Math.hypot(x, y, z)).toBeCloseTo(10 * zoom, 9);
    }
  });

  describe('dragging', () => {
    it('turns the view with the horizontal movement, up to the limit', () => {
      const a = orbitDrag(DESK_HOME, 0, 20, 0);
      expect(a.view.yaw).toBeGreaterThan(0);
      expect(orbitDrag(DESK_HOME, 0, 100000, 0).view.yaw).toBe(DESK_LIMITS.yaw);
      expect(orbitDrag(DESK_HOME, 0, -100000, 0).view.yaw).toBe(-DESK_LIMITS.yaw);
    });

    it('raises the camera when dragged down and lowers it when dragged up, within the limits', () => {
      expect(orbitDrag(DESK_HOME, 0, 0, 30).view.elev).toBeGreaterThan(DESK_HOME.elev);
      expect(orbitDrag(DESK_HOME, 0, 0, -30).view.elev).toBeLessThan(DESK_HOME.elev);
      expect(orbitDrag(DESK_HOME, 0, 0, -100000).view.elev).toBe(DESK_LIMITS.elevMin);
    });

    it('slides into the top-down view only once the camera is as high as it goes, and out again before it comes down', () => {
      const high = { ...DESK_HOME, elev: DESK_LIMITS.elevMax };
      expect(orbitDrag(DESK_HOME, 0, 0, 30).top).toBe(0);
      expect(orbitDrag(high, 0, 0, 30).top).toBeGreaterThan(0);
      expect(orbitDrag(high, 0, 0, 100000).top).toBe(1);
      const back = orbitDrag(high, 0.5, 0, -10);
      expect(back.top).toBeLessThan(0.5);
      expect(back.view.elev).toBe(DESK_LIMITS.elevMax);
    });

    it('slides all the way out of the top-down view, then lowers the camera, in one long drag', () => {
      const high = { ...DESK_HOME, elev: DESK_LIMITS.elevMax };
      const r = orbitDrag(high, 1, 0, -100000);
      expect(r.top).toBe(0);
      expect(r.view.elev).toBe(DESK_LIMITS.elevMin);
    });

    it('pans against the drag, and stays on the desk', () => {
      expect(panDrag(DESK_HOME, 40, 0, 0.01).panX).toBeLessThan(0);
      expect(inside(panDrag(DESK_HOME, -1e6, 1e6, 1))).toBeTrue();
    });
  });

  it('zooms in for a scale above 1 and stays within the zoom limits', () => {
    expect(zoomBy(DESK_HOME, 1.05).zoom).toBeLessThan(1);
    expect(zoomBy(DESK_HOME, 1e6).zoom).toBe(DESK_LIMITS.zoomMin);
    expect(zoomBy(DESK_HOME, 1e-6).zoom).toBe(DESK_LIMITS.zoomMax);
  });

  it('nudges with the arrow keys and never leaves the limits', () => {
    let v = DESK_HOME;
    for (let i = 0; i < 200; i++) v = nudge(v, 'right', false);
    expect(v.yaw).toBe(DESK_LIMITS.yaw);
    for (let i = 0; i < 200; i++) v = nudge(v, 'left', true);
    expect(v.panX).toBe(-DESK_LIMITS.panX);
    expect(inside(v)).toBeTrue();
  });

  it('eases towards a view and arrives', () => {
    let v: DeskView = { ...DESK_HOME };
    const to = DESK_PRESETS.side;
    for (let i = 0; i < 200; i++) v = easeView(v, to, 0.15);
    expect(sameView(v, to)).toBeTrue();
  });
});
