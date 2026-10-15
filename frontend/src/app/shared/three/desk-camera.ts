// src/app/shared/three/desk-camera.ts
// The camera of the desk view, as plain numbers (no three.js): where it stands for a given view, how a drag or a wheel
// turn changes the view, and the limits that keep it where the room behind the desk still looks right.

/** Where the player is looking from: turned around the board, raised above the desk, closer or further, shifted over the desk */
export interface DeskView {
  /** Turn around the board (rad); 0 = straight on from the white side, positive = to the right */
  yaw: number;
  /** Height above the desk (rad); 0 = at table level, π/2 = straight down */
  elev: number;
  /** Distance factor (1 = the framed distance) */
  zoom: number;
  /** Shift of the point looked at, over the desk, in board units */
  panX: number;
  panZ: number;
}

/** The limits of the view. Beyond them the backdrop would show its edges, or the camera would go under the desk. */
export const DESK_LIMITS = {
  yaw: 0.26,           // 15° either way
  elevMin: 0.44,       // 25°
  elevMax: 1.2,        // 69°: past this, dragging slides into the top-down view
  zoomMin: 0.85,
  zoomMax: 1.15,
  panX: 1.6,
  panZ: 1.0,
};

/** The view of the reference picture: straight on, about 37° above the desk */
export const DESK_HOME: Readonly<DeskView> = { yaw: 0, elev: 0.64, zoom: 1, panX: 0, panZ: 0 };

export type DeskPreset = 'player' | 'low' | 'side' | 'overhead';

/** Named views. `overhead` is not a pose of this camera: the top-down slide takes it (see `topAmount`). */
export const DESK_PRESETS: Record<Exclude<DeskPreset, 'overhead'>, DeskView> = {
  player: { ...DESK_HOME },
  low: { yaw: 0, elev: DESK_LIMITS.elevMin, zoom: 0.9, panX: 0, panZ: 0.2 },
  side: { yaw: DESK_LIMITS.yaw, elev: 0.55, zoom: 1, panX: 0, panZ: 0 },
};

/** Radians of turn per pixel dragged, and of rise per pixel dragged */
const YAW_PER_PX = 0.0045;
const ELEV_PER_PX = 0.005;
const HALF_PI = Math.PI / 2;

const clamp = (v: number, lo: number, hi: number): number => Math.max(lo, Math.min(hi, v));

export function clampDeskView(v: DeskView): DeskView {
  const L = DESK_LIMITS;
  return {
    yaw: clamp(v.yaw, -L.yaw, L.yaw),
    elev: clamp(v.elev, L.elevMin, L.elevMax),
    zoom: clamp(v.zoom, L.zoomMin, L.zoomMax),
    panX: clamp(v.panX, -L.panX, L.panX),
    panZ: clamp(v.panZ, -L.panZ, L.panZ),
  };
}

/** Where the camera stands (relative to nothing: add the target), `dist` being the framed distance. */
export function deskOffset(v: DeskView, dist: number): [number, number, number] {
  const d = dist * v.zoom;
  return [Math.sin(v.yaw) * Math.cos(v.elev) * d, Math.sin(v.elev) * d, Math.cos(v.yaw) * Math.cos(v.elev) * d];
}

/**
 * A drag with the primary button: turns the view, and raises or lowers it. Dragging down raises the camera; once it is
 * as high as the view goes, more of the same slides into the top-down view (`top`, 0..1) and back, as the old board did.
 */
export function orbitDrag(v: DeskView, top: number, dx: number, dy: number): { view: DeskView; top: number } {
  const L = DESK_LIMITS;
  const slide = HALF_PI - L.elevMax; // the part of the rise that the top-down slide covers
  let elev = v.elev;
  let t = top;
  let d = dy * ELEV_PER_PX;
  if (d > 0) {
    const used = Math.min(d, L.elevMax - elev);
    elev += used;
    d -= used;
    if (d > 0) t = clamp(t + d / slide, 0, 1);
  } else if (d < 0) {
    if (t > 0) {
      const used = Math.min(-d, t * slide);
      t -= used / slide;
      d += used;
    }
    elev += d;
  }
  return { view: clampDeskView({ ...v, yaw: v.yaw + dx * YAW_PER_PX, elev }), top: t };
}

/** A drag with the secondary button (or shift): shifts the point looked at over the desk, as if grabbing the desk. */
export function panDrag(v: DeskView, dx: number, dy: number, unitsPerPx: number): DeskView {
  // the camera looks along the desk: a drag to the right carries the desk right, so the view moves left
  const cy = Math.cos(v.yaw);
  const sy = Math.sin(v.yaw);
  const rx = -dx * unitsPerPx;
  const rz = -dy * unitsPerPx / Math.max(Math.sin(v.elev), 0.35);
  return clampDeskView({ ...v, panX: v.panX + rx * cy + rz * sy, panZ: v.panZ - rx * sy + rz * cy });
}

/** The wheel or a pinch: `scale` > 1 brings the board closer. */
export function zoomBy(v: DeskView, scale: number): DeskView {
  return clampDeskView({ ...v, zoom: v.zoom / scale });
}

/** Arrow keys: turn (and tilt) the view, or with `pan` shift it over the desk. */
export function nudge(v: DeskView, key: 'left' | 'right' | 'up' | 'down', pan: boolean): DeskView {
  const step = 0.04;
  if (pan) {
    const dx = key === 'left' ? -0.25 : key === 'right' ? 0.25 : 0;
    const dz = key === 'up' ? -0.25 : key === 'down' ? 0.25 : 0;
    return clampDeskView({ ...v, panX: v.panX + dx, panZ: v.panZ + dz });
  }
  const dyaw = key === 'left' ? -step : key === 'right' ? step : 0;
  const delev = key === 'up' ? -step : key === 'down' ? step : 0;
  return clampDeskView({ ...v, yaw: v.yaw + dyaw, elev: v.elev + delev });
}

/** Move `from` towards `to` by this fraction, for every part of the view (the easing of the camera). */
export function easeView(from: DeskView, to: DeskView, k: number): DeskView {
  const f = (a: number, b: number): number => a + (b - a) * k;
  return { yaw: f(from.yaw, to.yaw), elev: f(from.elev, to.elev), zoom: f(from.zoom, to.zoom), panX: f(from.panX, to.panX), panZ: f(from.panZ, to.panZ) };
}

/** Is `a` as good as `b`? (the easing has arrived) */
export function sameView(a: DeskView, b: DeskView, eps = 1e-3): boolean {
  return (['yaw', 'elev', 'zoom', 'panX', 'panZ'] as const).every((k) => Math.abs(a[k] - b[k]) < eps);
}
