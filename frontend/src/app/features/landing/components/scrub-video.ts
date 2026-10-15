// src/app/features/landing/components/scrub-video.ts

/**
 * A clip played by the scroll rather than by the clock: {@link seek} shows the frame for a point of
 * the clip, one seek at a time (the newest request wins). The clip loads when {@link load} is called,
 * and not before the page itself has loaded: the clips are megabytes, and must not compete with it.
 */
export class ScrubVideo {
  private live = false;
  private seeking = false;
  /** The time asked for while the video was still seeking to the last one */
  private pending: number | null = null;
  private progress = 0;

  private loading = false;

  constructor(private readonly video: HTMLVideoElement, private readonly src: string, private readonly onLive: () => void) {
    video.addEventListener('seeked', this.onSeeked);
    video.addEventListener('loadeddata', () => {
      this.live = true;
      this.onLive();
      this.seek(this.progress);
    }, { once: true });
  }

  /** Start loading the clip (once), as soon as the page has loaded and the browser is idle. */
  load(): void {
    if (this.loading) return;
    this.loading = true;
    const start = (): void => {
      const v = this.video;
      if (!v.isConnected) return; // the page went away meanwhile
      v.src = this.src;
      v.preload = 'auto';
      v.load();
    };
    whenIdleAfterLoad(start);
  }

  /** Show the frame at `progress`: 0 the first frame … 1 the last */
  seek(progress: number): void {
    this.progress = progress;
    const v = this.video;
    if (!this.live || !v.duration) return;
    const t = Math.min(v.duration - 0.05, Math.max(0, progress * v.duration));
    if (this.seeking) { this.pending = t; return; }
    if (Math.abs(v.currentTime - t) < 1 / 48) return;
    this.seeking = true;
    v.currentTime = t;
  }

  dispose(): void {
    this.video.removeEventListener('seeked', this.onSeeked);
    this.video.removeAttribute('src');
    this.video.load();
  }

  private onSeeked = (): void => {
    this.seeking = false;
    if (this.pending === null) return;
    const t = this.pending;
    this.pending = null;
    if (Math.abs(this.video.currentTime - t) >= 1 / 48) { this.seeking = true; this.video.currentTime = t; }
  };
}

/** Runs `work` once the page has loaded and the main thread is idle (soon after, where idleness cannot be asked about) */
export function whenIdleAfterLoad(work: () => void): void {
  const idle = (): void => {
    const ric = (window as { requestIdleCallback?: (cb: () => void, o?: { timeout: number }) => number }).requestIdleCallback;
    if (ric) ric(work, { timeout: 2000 });
    else setTimeout(work, 200);
  };
  if (document.readyState === 'complete') idle();
  else addEventListener('load', idle, { once: true });
}

/** Smoothstep of `x` between `a` and `b`, for crossfading stills */
export function ramp(x: number, a: number, b: number): number {
  const k = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return k * k * (3 - 2 * k);
}
