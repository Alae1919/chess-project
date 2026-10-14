// src/app/features/landing/components/shot.component.ts
import { ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';
import { Shot } from '../landing-media';

/** One shot of footage: its still, and its video clip when it has one and the visitor can afford it. */
@Component({
  selector: 'app-shot',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shot.video && withVideo) {
      <video #video muted playsinline loop preload="none" aria-hidden="true"
             [poster]="src" [style.object-position]="focus">
        <source [src]="shot.video.src" [type]="shot.video.type" />
      </video>
    } @else {
      <img [src]="src" alt="" decoding="async"
           [attr.srcset]="srcset" sizes="100vw"
           [attr.fetchpriority]="first ? 'high' : 'low'" [style.object-position]="focus" />
    }
  `,
  styles: [`
    :host { position: absolute; inset: 0; display: block; overflow: hidden; }
    img, video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; will-change: transform; }
  `],
})
export class ShotComponent {
  @Input({ required: true }) shot!: Shot;
  /** Load and play clips (not on phones, data saver or reduced motion) */
  @Input() withVideo = false;
  /** The first shot to appear loads before the others */
  @Input() first = false;

  @ViewChild('video') private video?: ElementRef<HTMLVideoElement>;
  private readonly phone = matchMedia('(max-width: 768px)').matches;

  get src(): string {
    return `${this.shot.still}-${this.shot.widths[1]}.webp`;
  }

  get srcset(): string {
    const [small, large] = this.shot.widths;
    return `${this.shot.still}-${small}.webp ${small}w, ${this.shot.still}-${large}.webp ${large}w`;
  }

  get focus(): string {
    return (this.phone && this.shot.phoneFocus) || this.shot.focus;
  }
  private playing = false;

  /** Play the clip while the shot is on screen; stills ignore this */
  setPlaying(playing: boolean): void {
    const v = this.video?.nativeElement;
    if (!v || playing === this.playing) return;
    this.playing = playing;
    if (playing) {
      v.currentTime = 0;
      v.play().catch(() => { /* autoplay refused: the poster stays */ });
    } else {
      v.pause();
    }
  }
}
