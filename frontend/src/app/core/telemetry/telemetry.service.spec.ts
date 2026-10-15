import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { TelemetryService, routeTemplate, timed } from './telemetry.service';

describe('routeTemplate', () => {
  it('turns a game id into the route template', () => {
    expect(routeTemplate('/game/3f2a9c1e-1111-4222-8333-444455556666')).toBe('/game/:id');
    expect(routeTemplate('/game/abc?x=1#y')).toBe('/game/:id');
  });

  it('keeps known routes and folds the rest into one', () => {
    expect(routeTemplate('/')).toBe('/');
    expect(routeTemplate('/home/')).toBe('/home');
    expect(routeTemplate('/game')).toBe('/game');
    expect(routeTemplate('/wp-admin/setup.php')).toBe('other');
  });
});

describe('TelemetryService', () => {
  let telemetry: TelemetryService;
  let beacons: Array<{ url: string; body: any }>;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    telemetry = TestBed.inject(TelemetryService);
    beacons = [];
    spyOn(navigator, 'sendBeacon').and.callFake((url: string | URL, body?: BodyInit | null) => {
      beacons.push({ url: String(url), body: JSON.parse(String(body)) });
      return true;
    });
  });

  it('sends queued timings in one beacon per route, then forgets them', () => {
    telemetry.timing('move.roundtrip', 123.4);
    telemetry.timing('game.firstFrame', 900, 'cold');
    telemetry.flush();
    telemetry.flush();

    expect(beacons.length).toBe(1);
    expect(beacons[0].url).toBe('/api/rum');
    expect(beacons[0].body.timings).toEqual([
      { name: 'move.roundtrip', value: 123 },
      { name: 'game.firstFrame', value: 900, variant: 'cold' },
    ]);
  });

  it('ignores values that are not durations', () => {
    telemetry.timing('move.roundtrip', -5);
    telemetry.timing('move.roundtrip', NaN);
    telemetry.flush();
    expect(beacons.length).toBe(0);
  });

  it('timed() records the time to the first value only, and nothing on error', () => {
    spyOn(telemetry, 'timing');
    const answer = new Subject<number>();
    answer.pipe(timed(telemetry, 'ai.reply')).subscribe();
    answer.next(1);
    answer.next(2);
    expect(telemetry.timing).toHaveBeenCalledTimes(1);
    expect((telemetry.timing as jasmine.Spy).calls.mostRecent().args[0]).toBe('ai.reply');

    const failing = new Subject<number>();
    failing.pipe(timed(telemetry, 'move.roundtrip')).subscribe({ error: () => undefined });
    failing.error(new Error('nope'));
    expect(telemetry.timing).toHaveBeenCalledTimes(1);
  });
});
