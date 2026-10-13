import { ACTS, actAt, stillTime } from './landing.page';
import { isLandingUrl } from '../../../app.component';

describe('LandingPage acts', () => {
  it('runs the five acts in order, each at rest after it starts and before the next', () => {
    expect(ACTS.map((a) => a.numeral)).toEqual(['I', 'II', 'III', 'IV', 'V']);
    ACTS.forEach((a, i) => {
      expect(a.rest).toBeGreaterThan(a.start);
      if (i < ACTS.length - 1) expect(a.rest).toBeLessThan(ACTS[i + 1].start);
    });
    expect(ACTS[ACTS.length - 1].rest).toBeLessThanOrEqual(100);
  });

  it('finds the act a point of the film belongs to', () => {
    expect(actAt(0)).toBe(0);
    expect(actAt(15.9)).toBe(0);
    expect(actAt(16)).toBe(1);
    expect(actAt(47)).toBe(2);
    expect(actAt(74)).toBe(3);
    expect(actAt(100)).toBe(4);
  });
});

describe('stillTime (reduced motion)', () => {
  it('shows the opening title at the top, then each act at rest', () => {
    expect(stillTime(0)).toBe(0);
    expect(stillTime(4)).toBe(0);
    expect(stillTime(6)).toBe(ACTS[0].rest);
    expect(stillTime(20)).toBe(ACTS[1].rest);
    expect(stillTime(99)).toBe(ACTS[4].rest);
  });
});

describe('isLandingUrl', () => {
  it('is the root only, whatever follows it', () => {
    expect(isLandingUrl('/')).toBeTrue();
    expect(isLandingUrl('/?ref=x')).toBeTrue();
    expect(isLandingUrl('/#act-3')).toBeTrue();
    expect(isLandingUrl('/home')).toBeFalse();
    expect(isLandingUrl('/game/1')).toBeFalse();
  });
});
