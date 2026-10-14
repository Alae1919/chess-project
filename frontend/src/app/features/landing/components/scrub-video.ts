// src/app/features/landing/components/scrub-video.ts

/**
 * A clip played by the scroll rather than by the clock: {@link seek} shows the frame for a point of
 * the clip, one seek at a time (the newest request wins). The clip loads once the page is on screen.
 */
export class ScrubVideo {
  private live = false;
  private seeking = false;
  /** The time asked for while the video was still seeking to the last one */
  private pending: number | null = null;
  private progress = 0;

  constructor(private readonly video: HTMLVideoElement, src: string, private readonly onLive: () => void) {
    video.addEventListener('seeked', this.onSeeked);
    video.addEventListener('loadeddata', () => {
      this.live = true;
      this.onLive();
      this.seek(this.progress);
    }, { once: true });
    const load = (): void => { video.src = src; video.preload = 'auto'; video.load(); };
    if (document.readyState === 'complete') requestAnimationFrame(load);
    else addEventListener('load', load, { once: true });
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

/** Smoothstep of `x` between `a` and `b`, for crossfading stills */
export function ramp(x: number, a: number, b: number): number {
  const k = Math.min(1, Math.max(0, (x - a) / (b - a)));
  return k * k * (3 - 2 * k);
}
