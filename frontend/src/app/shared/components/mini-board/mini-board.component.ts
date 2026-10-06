// src/app/shared/components/mini-board/mini-board.component.ts
import { Component, Input } from '@angular/core';
import { NgFor } from '@angular/common';
import { Piece, PieceType } from '../../../core/models';

// Solid glyphs for both colours, told apart by CSS; U+FE0E keeps the pawn from turning into an emoji
const GLYPHS: Record<PieceType, string> = {
  king: '♚', queen: '♛', rook: '♜', bishop: '♝', knight: '♞', pawn: '♟︎',
};

/** A thumbnail of a position: no coordinates, no interaction. */
@Component({
  selector: 'app-mini-board',
  standalone: true,
  imports: [NgFor],
  template: `
    <div class="mb" role="img" [attr.aria-label]="label" [style.--mb-size.px]="size">
      <span *ngFor="let c of cells" class="mb__sq" [class.mb__sq--light]="c.light"
            [class.mb__p--white]="c.white">{{ c.glyph }}</span>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .mb {
      width: var(--mb-size); height: var(--mb-size); display: grid; grid-template-columns: repeat(8, 1fr);
      border-radius: 6px; overflow: hidden; box-shadow: 0 0 0 1px rgba(214, 169, 78, .38);
    }
    .mb__sq {
      display: flex; align-items: center; justify-content: center; background: #5a4630;
      font: calc(var(--mb-size) / 8 * .82)/1 'Segoe UI Symbol', 'Noto Sans Symbols 2', 'DejaVu Sans', serif;
      color: #17110b; text-shadow: 0 0 1px rgba(255, 240, 210, .9);
    }
    .mb__sq--light { background: #d8c9b2; }
    .mb__p--white { color: #fbf6ea; text-shadow: 0 0 1px #1a120a, 0 0 1px #1a120a; }
  `],
})
export class MiniBoardComponent {
  /** [row][col], row 0 = rank 8, as in BoardState */
  @Input() set squares(value: (Piece | null)[][] | null | undefined) {
    this.cells = [];
    for (let row = 0; row < 8; row++) {
      for (let col = 0; col < 8; col++) {
        const piece = value?.[row]?.[col] ?? null;
        this.cells.push({
          light: (row + col) % 2 === 0,
          glyph: piece ? GLYPHS[piece.type] : '',
          white: piece?.color === 'white',
        });
      }
    }
  }
  /** Edge length in px */
  @Input() size = 72;
  @Input() label = 'Position de la partie';

  cells: { light: boolean; glyph: string; white: boolean }[] = [];
}
