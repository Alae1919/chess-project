import { easeFor, FRAME_MS, framesIn, MAX_STEP_MS } from './frame-step';

describe('frame-step', () => {
  describe('framesIn', () => {
    it('counts a 60 Hz frame as one', () => {
      expect(framesIn(FRAME_MS)).toBeCloseTo(1, 9);
      expect(framesIn(FRAME_MS * 3)).toBeCloseTo(3, 9);
    });

    it('counts a frame of a faster screen as part of one', () => {
      expect(framesIn(1000 / 120)).toBeCloseTo(0.5, 9);
    });

    it('stops at MAX_STEP_MS: a stall is not the animation running on', () => {
      expect(framesIn(MAX_STEP_MS)).toBeCloseTo(MAX_STEP_MS / FRAME_MS, 9);
      expect(framesIn(60_000)).toBe(framesIn(MAX_STEP_MS));
    });

    it('is never negative', () => {
      expect(framesIn(-5)).toBe(0);
    });
  });

  describe('easeFor', () => {
    it('is the factor itself for one frame, and nothing for none', () => {
      expect(easeFor(0.15, 1)).toBeCloseTo(0.15, 12);
      expect(easeFor(0.15, 0)).toBe(0);
    });

    it('covers in one step what the factor covers frame by frame', () => {
      let left = 1;
      for (let i = 0; i < 4; i++) left *= 1 - 0.18;
      expect(1 - easeFor(0.18, 4)).toBeCloseTo(left, 12);
    });

    it('never goes past the goal, however long the step', () => {
      expect(easeFor(0.3, framesIn(60_000))).toBeLessThan(1);
    });
  });
});
