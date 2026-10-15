// src/app/features/landing/components/king-stage.component.ts
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, Input, OnDestroy, ViewChild } from '@angular/core';
import { ramp, ScrubVideo } from './scrub-video';
import { assetUrl, stillSources } from '../../../core/assets/asset-url';

/** The camera's push toward the king, and stills of its first and last frames */
const MEDIA = {
  video: 'assets/landing/king-push.mp4',
  start: 'assets/landing/king-start',
  end: 'assets/landing/king-end',
};

/**
 * The solitary king under its spotlight (act I): one camera move, from the king small at the right
 * of the board to his crown, played by the scroll rather than by the clock. Phones, data savers and
 * reduced motion get the clip's first and last frames instead, crossfaded. Driven imperatively by
 * the page's scroll timeline through {@link set}, so scrolling never runs change detection.
 */
@Component({
  selector: 'app-king-stage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img class="frame frame--start" alt="" decoding="async" fetchpriority="high"
         [src]="startStill.src" [attr.srcset]="startStill.srcset" sizes="100vw" />
    @if (stillsOnly) {
      <img class="frame frame--end" #end alt="" decoding="async" loading="lazy"
           [src]="endStill.src" [attr.srcset]="endStill.srcset" sizes="100vw" />
    } @else {
      <video class="frame" #video muted playsinline preload="none" aria-hidden="true" disablepictureinpicture></video>
    }
  `,
  styles: [`
    :host { position: absolute; inset: 0; display: block; background: #030201; overflow: hidden; }
    .frame { position: absolute; inset: 0; width: 100%; height: 100%; display: block; object-fit: cover; }
    /* the king stands right of centre at first, then the camera centres on him: a narrow screen crops to him */
    .frame--start { object-position: 74% 50%; }
    .frame--end { object-position: 50% 50%; }
    video { opacity: 0; transition: opacity .6s ease; }
    :host(.is-live) video { opacity: 1; }
  `],
})
export class KingStageComponent implements AfterViewInit, OnDestroy {
  /** Use the stills only (phones, data saver, reduced motion) */
  @Input() stillsOnly = false;

  @ViewChild('end') private endImg?: ElementRef<HTMLImageElement>;
  @ViewChild('video') private videoEl?: ElementRef<HTMLVideoElement>;

  readonly startStill = stillSources(MEDIA.start, [1280, 2560]);
  readonly endStill = stillSources(MEDIA.end, [1280, 2560]);
  private hostEl = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private state = { light: 0, push: 0, active: false };
  private scrub?: ScrubVideo;

  ngAfterViewInit(): void {
    this.paint();
    const v = this.videoEl?.nativeElement;
    if (v) {
      this.scrub = new ScrubVideo(v, assetUrl(MEDIA.video), () => this.hostEl.classList.add('is-live'));
      // act I is on screen from the start: its clip loads as soon as the page has (after the first frame's still)
      this.scrub.load();
    }
  }

  /** light: 0 dark … 1 full spotlight; push: 0 the first frame … 1 the last; active: on screen */
  set(light: number, push: number, active: boolean): void {
    const s = this.state;
    if (s.light === light && s.push === push && s.active === active) return;
    this.state = { light, push, active };
    if (active) this.scrub?.seek(push);
    this.paint();
  }

  /** Brightness for the light; the crop follows the king; with stills only, a crossfade for the push */
  private paint(): void {
    const { light, push } = this.state;
    const l = light * light * (3 - 2 * light);
    this.hostEl.style.filter = `brightness(${(0.08 + 0.92 * l).toFixed(3)})`;
    if (this.videoEl) this.videoEl.nativeElement.style.objectPosition = `${(74 - 24 * push).toFixed(1)}% 50%`;
    if (this.endImg) this.endImg.nativeElement.style.opacity = String(ramp(push, 0.3, 0.8));
  }

  ngOnDestroy(): void {
    this.scrub?.dispose();
  }
}
