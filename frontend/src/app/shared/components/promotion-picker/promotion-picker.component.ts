import {
  AfterViewInit, Component, ElementRef, EventEmitter, HostListener, Input, Output, ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PieceColor } from '../../../core/models';
import { PROMOTION_PIECES, PromotionPiece } from '../../../core/utils/promotion.utils';

const GLYPHS: Record<PromotionPiece, string> = {
  queen: '♛︎', rook: '♜︎', bishop: '♝︎', knight: '♞︎',
};

const LABELS: Record<PromotionPiece, string> = {
  queen: 'Dame', rook: 'Tour', bishop: 'Fou', knight: 'Cavalier',
};

/**
 * Asks which piece a pawn reaching the last rank becomes. Sits over the board
 * (2D or 3D); Escape or a click outside cancels the move.
 */
@Component({
  selector: 'app-promotion-picker',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="promo-backdrop" (click)="cancel.emit()"></div>
    <div class="promo-dialog" role="dialog" aria-modal="true" aria-labelledby="promo-title">
      <p id="promo-title" class="promo-title">Promotion</p>
      <div class="promo-options" #options>
        <button
          *ngFor="let p of pieces"
          type="button"
          class="promo-option"
          [class.promo-option--white]="color === 'white'"
          [class.promo-option--black]="color === 'black'"
          [attr.aria-label]="labels[p]"
          [title]="labels[p]"
          (click)="choose.emit(p)"
        >{{ glyphs[p] }}</button>
      </div>
    </div>
  `,
  styles: [`
    :host { position: absolute; inset: 0; z-index: 20; display: grid; place-items: center; }
    .promo-backdrop { position: absolute; inset: 0; background: rgba(8, 6, 4, .55); backdrop-filter: blur(2px); }
    .promo-dialog {
      position: relative; padding: 16px 18px 18px; border-radius: var(--radius-lg);
      background: var(--surface); border: 1px solid var(--borderh); box-shadow: var(--pg), 0 18px 48px rgba(0, 0, 0, .6);
    }
    .promo-title {
      margin: 0 0 12px; text-align: center;
      font: 500 11px var(--font); color: var(--textd); text-transform: uppercase; letter-spacing: .12em;
    }
    .promo-options { display: flex; gap: 10px; }
    .promo-option {
      width: 64px; height: 64px; display: grid; place-items: center; cursor: pointer;
      background: var(--bg3); border: 1px solid var(--border); border-radius: var(--radius-md);
      font-family: 'Segoe UI Symbol', 'Noto Sans Symbols 2', 'DejaVu Sans', 'Arial Unicode MS', sans-serif;
      font-size: 40px; line-height: 1; paint-order: stroke fill; transition: border-color .2s, transform .2s;
    }
    .promo-option:hover, .promo-option:focus-visible {
      border-color: var(--p); transform: translateY(-2px); outline: none; box-shadow: var(--pg);
    }
    .promo-option--white { color: #f8f1e3; -webkit-text-stroke: 1.5px #3a2a18; }
    .promo-option--black { color: #1b140e; -webkit-text-stroke: 1.2px var(--p); }
    @media (max-width: 480px) { .promo-option { width: 52px; height: 52px; font-size: 32px; } }
  `],
})
export class PromotionPickerComponent implements AfterViewInit {
  @Input({ required: true }) color!: PieceColor;
  @Output() choose = new EventEmitter<PromotionPiece>();
  @Output() cancel = new EventEmitter<void>();

  @ViewChild('options') private options?: ElementRef<HTMLElement>;

  readonly pieces = PROMOTION_PIECES;
  readonly glyphs = GLYPHS;
  readonly labels = LABELS;

  ngAfterViewInit(): void {
    // Keyboard users land on the queen, the usual choice
    this.options?.nativeElement.querySelector('button')?.focus();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.cancel.emit();
  }
}
