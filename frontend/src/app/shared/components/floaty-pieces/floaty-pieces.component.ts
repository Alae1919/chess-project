// src/app/shared/components/floaty-pieces/floaty-pieces.component.ts
import { Component } from '@angular/core';
import { NgFor } from '@angular/common';

const SYMBOLS = ['♔', '♕', '♖', '♗', '♘', '♙', '♚', '♛', '♜', '♝', '♞', '♟'];

/** Faint chess glyphs drifting up behind the page (decorative). */
@Component({
  selector: 'app-floaty-pieces',
  standalone: true,
  imports: [NgFor],
  template: `
    <div class="floaty" aria-hidden="true">
      <div
        *ngFor="let it of items"
        class="flt"
        [style.left.%]="it.left"
        [style.font-size.px]="it.size"
        [style.animation-duration.s]="it.dur"
        [style.animation-delay.s]="it.delay"
      >{{ it.sym }}</div>
    </div>
  `,
  styles: [`
    .floaty { position: fixed; inset: 0; pointer-events: none; z-index: 0; overflow: hidden; }
    .flt {
      position: absolute; bottom: -40px; opacity: 0; font-family: serif; color: var(--text);
      animation: float-up linear infinite;
    }
    @media (prefers-reduced-motion: reduce) { .flt { animation: none; display: none; } }
  `],
})
export class FloatyPiecesComponent {
  readonly items = Array.from({ length: 12 }, (_, i) => ({
    sym: SYMBOLS[i % 12],
    left: 4 + (i * 8.3) % 90,
    delay: (i * 1.7) % 16,
    dur: 16 + (i * 2.9) % 14,
    size: 14 + (i * 5) % 24,
  }));
}
