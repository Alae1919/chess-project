import { Injectable, NgZone, inject } from '@angular/core';
import { MonoTypeOperatorFunction, defer, tap } from 'rxjs';
import { Metric, onCLS, onFCP, onINP, onLCP, onTTFB } from 'web-vitals';
import { environment } from '../../../environments/environment';

/**
 * The app's own timings. The server keeps only these names (see RumRecorder), so a new one goes
 * on both lists.
 *  - `move.roundtrip`: the player's move, from sending it to the server's answer
 *  - `ai.reply`: asking the AI to move, to its move arriving
 *  - `legal.select`: selecting a piece, to its legal moves arriving
 *  - `game.start`: "start a game", to the game page being shown
 *  - `game.firstFrame`: the 3D board's first drawn frame, from creating it; `cold` for the first board since the page loaded
 *  - `desk.assets`: the desk's room, wood and props, from asking for them to all being in (cold / warm as above)
 *  - `ws.reconnect`: a dropped socket, to it being open again; the variant says which (`game` or `lobby`)
 *  - `csp.violation`: the browser refused something the page asked for (value 1); the variant is the rule it broke.
 *    The page itself never should: one of these means a feature is broken in production only (ng serve sends no CSP)
 */
export type TimingName =
  | 'move.roundtrip' | 'ai.reply' | 'legal.select' | 'game.start'
  | 'game.firstFrame' | 'desk.assets' | 'ws.reconnect' | 'csp.violation';

/** The CSP rules reported by name; any other is `other` (the server keeps the same list) */
const CSP_DIRECTIVES = new Set(['connect-src', 'img-src', 'script-src', 'style-src', 'font-src', 'media-src', 'worker-src']);

/** `script-src-elem` → `script-src`: the rule as security-headers.conf writes it */
export function cspDirective(effective: string): string {
  const base = effective.replace(/-(elem|attr)$/, '');
  return CSP_DIRECTIVES.has(base) ? base : 'other';
}

interface Timing { name: string; value: number; variant?: string }

/** The routes as the router declares them: what the server groups page timings by. */
const ROUTES = new Set(['/', '/home', '/login', '/register', '/game', '/game/:id', '/account', '/online']);

/** `/game/3f2a…?x=1` → `/game/:id`: never a game's id, so every game counts as one page. */
export function routeTemplate(path: string): string {
  const clean = path.split(/[?#]/)[0].replace(/\/+$/, '') || '/';
  if (/^\/game\/[^/]+$/.test(clean)) return '/game/:id';
  return ROUTES.has(clean) ? clean : 'other';
}

/** How often the queue goes out while the page stays open, besides when it is hidden. */
const FLUSH_EVERY_MS = 30_000;

/**
 * Real-user timings: the page's Web Vitals and the app's own timings above, sent in small batches
 * to /api/rum, where they become metrics. Nothing identifies the player: a name, a number, the
 * route's template.
 */
@Injectable({ providedIn: 'root' })
export class TelemetryService {
  private zone = inject(NgZone);
  private queue = new Map<string, Timing[]>();
  private started = false;

  /** Starts watching the page's Web Vitals and sending what was measured. Once per page load. */
  start(): void {
    if (this.started) return;
    this.started = true;
    // the vitals describe how the page loaded, so they belong to the route it was loaded on,
    // even when the browser reports them later (on leaving the page, say)
    const loadedOn = routeTemplate(location.pathname);
    const vital = (m: Metric) => this.add(loadedOn, { name: m.name, value: m.value });
    onLCP(vital);
    onINP(vital);
    onCLS(vital);
    onTTFB(vital);
    onFCP(vital);

    // outside Angular: a timer that fires every 30 s must not run change detection every 30 s
    this.zone.runOutsideAngular(() => {
      setInterval(() => this.flush(), FLUSH_EVERY_MS);
      // the last chance to send: the page may never be shown again
      document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'hidden') this.flush();
      });
      window.addEventListener('pagehide', () => this.flush());
      // the desk's props once lost their colours to a refused blob: fetch, and nothing said so
      document.addEventListener('securitypolicyviolation', (e) => this.timing('csp.violation', 1, cspDirective(e.effectiveDirective)));
    });
  }

  /** Records one of the app's timings, in ms, against the current route. */
  timing(name: TimingName, ms: number, variant?: string): void {
    if (!Number.isFinite(ms) || ms < 0) return;
    this.add(routeTemplate(location.pathname), { name, value: Math.round(ms), ...(variant ? { variant } : {}) });
  }

  /** Sends what is queued, one request per route. */
  flush(): void {
    if (!this.queue.size) return;
    const url = `${environment.apiUrl}/rum`;
    for (const [route, timings] of this.queue) {
      // a string goes out as text/plain, which needs no CORS preflight (see RumController)
      const body = JSON.stringify({ route, timings });
      const sent = typeof navigator.sendBeacon === 'function' && navigator.sendBeacon(url, body);
      if (!sent) fetch(url, { method: 'POST', body, keepalive: true }).catch(() => undefined);
    }
    this.queue.clear();
  }

  private add(route: string, timing: Timing): void {
    const list = this.queue.get(route) ?? [];
    list.push(timing);
    this.queue.set(route, list);
  }
}

/**
 * Times an observable from subscription to its first value (an HTTP call, from sending the request
 * to its answer) and records it. Errors are not timed: a failure is not a response time.
 */
export function timed<T>(telemetry: TelemetryService, name: TimingName, variant?: string): MonoTypeOperatorFunction<T> {
  return (source) => defer(() => {
    const start = performance.now();
    let first = true;
    return source.pipe(tap(() => {
      if (!first) return;
      first = false;
      telemetry.timing(name, performance.now() - start, variant);
    }));
  });
}
