// src/app/features/landing/components/search-stage.component.ts
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, Input, OnDestroy, ViewChild } from '@angular/core';
import { ramp, ScrubVideo } from './scrub-video';
import { assetUrl, stillSources } from '../../../core/assets/asset-url';

/** The search tree growing out of the knight, then narrowing to the one square: the clip, and stills of three of its frames */
const MEDIA = {
  video: 'assets/landing/search.mp4',
  start: 'assets/landing/search-start',
  tree: 'assets/landing/search-tree',
  end: 'assets/landing/search-end',
};

/**
 * Act III's picture: the engine's search drawn as light on the board. The scroll plays the clip, so
 * the tree grows as the search goes deeper and narrows as the replies are weighed. Phones, data
 * savers and reduced motion get three stills of it, crossfaded. Driven through {@link set}.
 */
@Component({
  selector: 'app-search-stage',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <img class="frame" alt="" decoding="async" loading="lazy" [src]="startStill.src" [attr.srcset]="startStill.srcset" sizes="100vw" />
    @if (stillsOnly) {
      <img class="frame" #tree alt="" decoding="async" loading="lazy" [src]="treeStill.src" [attr.srcset]="treeStill.srcset" sizes="100vw" />
      <img class="frame" #end alt="" decoding="async" loading="lazy" [src]="endStill.src" [attr.srcset]="endStill.srcset" sizes="100vw" />
    } @else {
      <video class="frame" #video muted playsinline preload="none" aria-hidden="true" disablepictureinpicture></video>
    }
  `,
  styles: [`
    :host { position: absolute; inset: 0; display: block; background: #030201; overflow: hidden; }
    .frame { position: absolute; inset: 0; width: 100%; height: 100%; display: block; object-fit: cover; object-position: var(--x, 60%) 50%; }
    /* a narrow screen shows a slice of the frame: it starts on the knight and drifts to the square that is chosen */
    img + img, video { opacity: 0; }
    video { transition: opacity .6s ease; }
    :host(.is-live) video { opacity: 1; }
  `],
})
export class SearchStageComponent implements AfterViewInit, OnDestroy {
  /** Use the stills only (phones, data saver, reduced motion) */
  @Input() stillsOnly = false;

  @ViewChild('tree') private treeImg?: ElementRef<HTMLImageElement>;
  @ViewChild('end') private endImg?: ElementRef<HTMLImageElement>;
  @ViewChild('video') private videoEl?: ElementRef<HTMLVideoElement>;

  readonly startStill = stillSources(MEDIA.start, [1280, 2560]);
  readonly treeStill = stillSources(MEDIA.tree, [1280, 2560]);
  readonly endStill = stillSources(MEDIA.end, [1280, 2560]);
  private hostEl = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private progress = -1;
  private scrub?: ScrubVideo;

  ngAfterViewInit(): void {
    const v = this.videoEl?.nativeElement;
    if (v) this.scrub = new ScrubVideo(v, assetUrl(MEDIA.video), () => this.hostEl.classList.add('is-live'));
  }

  /** The visitor is getting close to act III: start loading its clip (once). */
  prepare(): void {
    this.scrub?.load();
  }

  /** progress: 0 the first frame … 1 the last; active: on screen */
  set(progress: number, active: boolean): void {
    if (!active || progress === this.progress) return;
    this.progress = progress;
    this.scrub?.seek(progress);
    this.hostEl.style.setProperty('--x', `${(60 - 11 * ramp(progress, 0.4, 0.9)).toFixed(1)}%`);
    if (this.treeImg) this.treeImg.nativeElement.style.opacity = String(ramp(progress, 0.05, 0.3));
    if (this.endImg) this.endImg.nativeElement.style.opacity = String(ramp(progress, 0.5, 0.8));
  }

  ngOnDestroy(): void {
    this.scrub?.dispose();
  }
}
