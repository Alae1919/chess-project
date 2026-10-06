import { tabFor } from './tab-bar.component';

describe('tabFor', () => {
  it('lights the tab of the page the player is on', () => {
    expect(tabFor('/home')).toBe('play');
    expect(tabFor('/online')).toBe('online');
    expect(tabFor('/account')).toBe('profile');
  });

  it('tells the saved games apart from the rest of the home page', () => {
    expect(tabFor('/home?mode=saved')).toBe('saved');
    expect(tabFor('/home?mode=ai')).toBe('play');
  });

  it('has no tab for the pages without the bar: the landing page, signing in, a game', () => {
    expect(tabFor('/')).toBeNull();
    expect(tabFor('/login?returnUrl=%2Fhome')).toBeNull();
    expect(tabFor('/game/abc')).toBeNull();
  });
});
