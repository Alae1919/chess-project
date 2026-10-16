// src/app/shared/three/frame-step.ts
// The board's easing was tuned frame by frame, at 60 Hz. These turn the time a frame took into the frames it stands
// for, so the intro and the easing last as long on a slow machine (or a fast screen) as on the one they were tuned on.

/** One frame at 60 Hz, the pace the easing factors were chosen at */
export const FRAME_MS = 1000 / 60;

/**
 * The longest stretch one frame may stand for. After a stall (a shader compiles, the page was busy) the animation
 * carries on from where it was, and does not jump to its end.
 */
export const MAX_STEP_MS = 100;

/** The 60 Hz frames that `ms` stand for (at most MAX_STEP_MS worth, never negative) */
export const framesIn = (ms: number): number => Math.min(Math.max(ms, 0), MAX_STEP_MS) / FRAME_MS;

/** A factor chosen as "this fraction of the way, each frame", for a step of `frames` frames: the same distance, covered in one go */
export const easeFor = (k: number, frames: number): number => 1 - Math.pow(1 - k, frames);
