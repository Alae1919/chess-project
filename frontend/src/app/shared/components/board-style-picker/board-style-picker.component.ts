// src/app/shared/components/board-style-picker/board-style-picker.component.ts
import { Component, Input, inject } from '@angular/core';
import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { BOARD_MODES, STYLES_2D, STYLES_3D } from '../../board/board-styles';
import { PIECE_SETS, pieceImageUrl, pieceImageUrls } from '../../board/piece-sets';
import { PieceSet } from '../../../core/models';

/**
 * Pick the board mode (3D / 2D) and its style. Changes apply immediately and are saved
 * (browser + account), so it can be used mid-game as well as on the account page.
 */
@Component({
  selector: 'app-board-style-picker',
  standalone: true,
  imports: [AsyncPipe, NgFor, NgIf],
  template: `
    <!-- Phones, in the game sheet: a 2D/3D switch and the swatches of the active mode -->
    <div class="cmp" *ngIf="compact ? (prefsService.prefs$ | async) : null as prefs">
      <div class="cmp__row">
        <div class="cmp__modes" role="radiogroup" aria-label="Mode du plateau">
          <button *ngFor="let m of modes" type="button" role="radio" class="cmp__mode"
                  [class.cmp__mode--on]="prefs.mode === m.id" [attr.aria-checked]="prefs.mode === m.id"
                  [disabled]="m.id === '3d' && !prefsService.webgl" (click)="prefsService.setMode(m.id)">{{ m.label }}</button>
        </div>
        <div class="cmp__swatches" role="radiogroup" aria-label="Style du plateau">
          <ng-container *ngIf="prefs.mode === '3d'; else flat">
            <button *ngFor="let s of styles3d" type="button" role="radio" class="cmp__swatch"
                    [class.cmp__swatch--on]="prefs.style3d === s.id" [attr.aria-checked]="prefs.style3d === s.id"
                    [attr.aria-label]="s.label" [style.--light]="s.swatch[0]" [style.--dark]="s.swatch[1]"
                    (click)="prefsService.setStyle3d(s.id)"></button>
          </ng-container>
          <ng-template #flat>
            <button *ngFor="let s of styles2d" type="button" role="radio" class="cmp__swatch"
                    [class.cmp__swatch--on]="prefs.style2d === s.id" [attr.aria-checked]="prefs.style2d === s.id"
                    [attr.aria-label]="s.label" [style.--light]="s.swatch[0]" [style.--dark]="s.swatch[1]"
                    (click)="prefsService.setStyle2d(s.id)"></button>
          </ng-template>
        </div>
      </div>
      <p class="cmp__label">Style : {{ styleLabel(prefs) }}</p>
      <ng-container *ngIf="prefs.mode === '2d'">
        <div class="cmp__sets" role="radiogroup" aria-label="Jeu de pièces">
          <button *ngFor="let p of pieceSets" type="button" role="radio" class="cmp__set"
                  [class.cmp__set--on]="prefs.pieceSet === p.id" [attr.aria-checked]="prefs.pieceSet === p.id"
                  [attr.aria-label]="p.label" (click)="prefsService.setPieceSet(p.id)">
            <span *ngIf="p.id === 'glyph'; else img" class="cmp__glyph" aria-hidden="true">♞</span>
            <ng-template #img><img [src]="previewUrl(p.id, 'white')" alt="" /><img [src]="previewUrl(p.id, 'black')" alt="" /></ng-template>
          </button>
        </div>
        <p class="cmp__label">Pièces : {{ pieceSetLabel(prefs.pieceSet) }}</p>
      </ng-container>
    </div>

    <div class="bsp" *ngIf="!compact ? (prefsService.prefs$ | async) : null as prefs">
      <div class="bsp__modes" role="radiogroup" aria-label="Mode du plateau">
        <button
          *ngFor="let m of modes"
          type="button" role="radio"
          class="bsp__mode"
          [class.bsp__mode--sel]="prefs.mode === m.id"
          [attr.aria-checked]="prefs.mode === m.id"
          [disabled]="m.id === '3d' && !prefsService.webgl"
          [title]="m.id === '3d' && !prefsService.webgl ? 'WebGL indisponible sur ce navigateur' : ''"
          (click)="prefsService.setMode(m.id)"
        >
          <span class="bsp__mode-label">{{ m.label }}</span>
          <span class="bsp__mode-hint">{{ m.hint }}</span>
        </button>
      </div>

      <div class="bsp__group" *ngIf="both || prefs.mode === '3d'">
        <p class="bsp__title">Style 3D</p>
        <div class="bsp__styles" role="radiogroup" aria-label="Style du plateau 3D">
          <button
            *ngFor="let s of styles3d"
            type="button" role="radio"
            class="bsp__style"
            [class.bsp__style--sel]="prefs.style3d === s.id"
            [attr.aria-checked]="prefs.style3d === s.id"
            (click)="prefsService.setStyle3d(s.id)"
          >
            <span class="bsp__swatch" [style.--light]="s.swatch[0]" [style.--dark]="s.swatch[1]" [style.--accent]="s.swatch[2]"></span>
            <span class="bsp__style-label">{{ s.label }}</span>
          </button>
        </div>
      </div>

      <div class="bsp__group" *ngIf="both || prefs.mode === '2d'">
        <p class="bsp__title">Style 2D</p>
        <div class="bsp__styles" role="radiogroup" aria-label="Style du plateau 2D">
          <button
            *ngFor="let s of styles2d"
            type="button" role="radio"
            class="bsp__style"
            [class.bsp__style--sel]="prefs.style2d === s.id"
            [attr.aria-checked]="prefs.style2d === s.id"
            (click)="prefsService.setStyle2d(s.id)"
          >
            <span class="bsp__swatch" [style.--light]="s.swatch[0]" [style.--dark]="s.swatch[1]" [style.--accent]="s.swatch[2]"></span>
            <span class="bsp__style-label">{{ s.label }}</span>
          </button>
        </div>
      </div>

      <div class="bsp__group" *ngIf="both || prefs.mode === '2d'">
        <p class="bsp__title">Pièces 2D</p>
        <div class="bsp__styles" role="radiogroup" aria-label="Jeu de pièces du plateau 2D">
          <button
            *ngFor="let p of pieceSets"
            type="button" role="radio"
            class="bsp__style"
            [class.bsp__style--sel]="prefs.pieceSet === p.id"
            [attr.aria-checked]="prefs.pieceSet === p.id"
            [title]="p.hint"
            (click)="prefsService.setPieceSet(p.id)"
          >
            <span class="bsp__pieces">
              <span *ngIf="p.id === 'glyph'; else img" class="bsp__glyph" aria-hidden="true">♞</span>
              <ng-template #img><img [src]="previewUrl(p.id, 'white')" alt="" /><img [src]="previewUrl(p.id, 'black')" alt="" /></ng-template>
            </span>
            <span class="bsp__style-label">{{ p.label }}</span>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; }
    .bsp { display: flex; flex-direction: column; gap: 16px; }

    .bsp__modes { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
    .bsp__mode {
      display: flex; flex-direction: column; align-items: flex-start; gap: 2px; text-align: left;
      padding: 10px 12px; background: var(--bg2); border: 1px solid var(--border); border-radius: 8px;
      color: var(--textd); cursor: pointer; transition: all .2s;
    }
    .bsp__mode:hover:not(:disabled) { border-color: var(--borderh); color: var(--text); }
    .bsp__mode:disabled { opacity: .45; cursor: not-allowed; }
    .bsp__mode--sel { background: var(--pd); border-color: var(--p); color: var(--p); }
    .bsp__mode-label { font: 600 15px var(--serif); letter-spacing: .08em; }
    .bsp__mode-hint { font: 400 11px var(--font); color: var(--textm); line-height: 1.3; }
    .bsp__mode--sel .bsp__mode-hint { color: var(--textd); }

    .bsp__title {
      font: 600 11px var(--font); letter-spacing: .12em; text-transform: uppercase;
      color: var(--textm); margin-bottom: 8px;
    }
    .bsp__styles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .bsp__style {
      display: flex; flex-direction: column; align-items: center; gap: 7px; padding: 10px 6px 8px;
      background: var(--bg2); border: 1px solid var(--border); border-radius: 8px;
      color: var(--textd); cursor: pointer; transition: all .2s;
    }
    .bsp__style:hover { border-color: var(--borderh); color: var(--text); }
    .bsp__style--sel { background: var(--pd); border-color: var(--p); color: var(--p); }
    .bsp__style-label { font: 500 11px var(--font); text-align: center; line-height: 1.2; }

    /* mini board: 2x2 checker with the piece colour as a dot */
    .bsp__swatch {
      position: relative; width: 40px; height: 40px; border-radius: 3px; overflow: hidden;
      background:
        conic-gradient(var(--dark) 25%, var(--light) 0 50%, var(--dark) 0 75%, var(--light) 0);
      box-shadow: 0 0 0 1px rgba(255, 255, 255, .12);
    }
    .bsp__swatch::after {
      content: ''; position: absolute; left: 50%; top: 50%; width: 14px; height: 14px; border-radius: 50%;
      transform: translate(-50%, -50%); background: var(--accent); box-shadow: 0 1px 3px rgba(0, 0, 0, .6);
    }

    /* a piece set: a white and a black knight of the set, on a light and a dark square */
    .bsp__pieces {
      display: flex; width: 56px; height: 34px; border-radius: 3px; overflow: hidden;
      box-shadow: 0 0 0 1px rgba(255, 255, 255, .12);
    }
    .bsp__pieces img, .bsp__glyph { flex: 1; min-width: 0; height: 100%; object-fit: contain; padding: 2px; box-sizing: border-box; }
    .bsp__pieces > img:first-child, .bsp__pieces > .bsp__glyph { background: #f0d9b5; }
    .bsp__pieces > img:last-child { background: #b58863; }
    .bsp__glyph { display: flex; align-items: center; justify-content: center; font: 24px/1 'Segoe UI Symbol', 'Noto Sans Symbols 2', serif; color: #17110b; background: #f0d9b5; }

    .cmp__sets { display: flex; justify-content: flex-end; gap: 10px; margin-top: 12px; }
    .cmp__set {
      display: flex; width: 72px; height: 40px; padding: 0; border: 0; border-radius: 10px; overflow: hidden; cursor: pointer;
      background: #f0d9b5; box-shadow: 0 0 0 1px rgba(201, 164, 92, .25); transition: box-shadow .2s;
    }
    .cmp__set img, .cmp__glyph { flex: 1; min-width: 0; height: 100%; object-fit: contain; padding: 3px; box-sizing: border-box; }
    .cmp__set img:last-child { background: #b58863; }
    .cmp__glyph { display: flex; align-items: center; justify-content: center; font: 26px/1 'Segoe UI Symbol', 'Noto Sans Symbols 2', serif; color: #17110b; }
    .cmp__set--on { box-shadow: 0 0 0 2px #120e0a, 0 0 0 4px var(--gold); }

    .cmp__row { display: flex; align-items: center; gap: 10px; }
    .cmp__modes { flex: none; display: flex; padding: 3px; background: rgba(255, 255, 255, .04); border: 1px solid var(--border); border-radius: 12px; }
    .cmp__mode {
      width: 48px; height: 40px; border: 0; border-radius: 9px; background: transparent; cursor: pointer;
      font: 500 14px var(--font); color: var(--textd);
    }
    .cmp__mode:disabled { opacity: .4; cursor: not-allowed; }
    .cmp__mode--on { background: var(--gold-grad); color: #140f08; }
    .cmp__swatches { flex: 1; display: flex; justify-content: flex-end; gap: 10px; }
    .cmp__swatch {
      width: 48px; height: 48px; padding: 0; border: 0; border-radius: 12px; cursor: pointer;
      background: conic-gradient(var(--light) 0 25%, var(--dark) 0 50%, var(--light) 0 75%, var(--dark) 0);
      box-shadow: 0 0 0 1px rgba(201, 164, 92, .25); transition: box-shadow .2s;
    }
    .cmp__swatch--on { box-shadow: 0 0 0 2px #120e0a, 0 0 0 4px var(--gold); }
    .cmp__label { margin: 8px 0 0; text-align: right; font-size: 12px; color: var(--textc); }

    /* phones: still three across, a little tighter */
    @media (max-width: 420px) {
      .bsp__style { padding: 10px 4px 8px; border-radius: 12px; }
      .bsp__mode { border-radius: 12px; }
      .bsp__swatch { width: 36px; height: 36px; border-radius: 6px; }
    }
  `],
})
export class BoardStylePickerComponent {
  prefsService = inject(BoardPrefsService);

  constructor() {
    // fetch every set's pieces now, so choosing one shows it at once instead of piece by piece
    if (typeof Image !== 'undefined') {
      PIECE_SETS.filter((p) => p.id !== 'glyph').forEach((p) => pieceImageUrls(p.id).forEach((url) => { new Image().src = url; }));
    }
  }

  /** Show the 3D and the 2D styles together (account page) instead of only the active mode's */
  @Input() both = false;
  /** The phone's game sheet: a switch and swatches, no descriptions */
  @Input() compact = false;

  /** "Luxe", "Marbre & Or": the style the swatches show as chosen */
  styleLabel(prefs: { mode: string; style2d: string; style3d: string }): string {
    const list: ReadonlyArray<{ id: string; label: string }> = prefs.mode === '3d' ? STYLES_3D : STYLES_2D;
    const id = prefs.mode === '3d' ? prefs.style3d : prefs.style2d;
    return list.find((s) => s.id === id)?.label ?? '';
  }

  readonly pieceSets = PIECE_SETS;

  /** A knight of the set, as the picker shows it */
  previewUrl(set: PieceSet, color: 'white' | 'black'): string {
    return pieceImageUrl(set, { color, type: 'knight' });
  }

  pieceSetLabel(id: PieceSet): string {
    return PIECE_SETS.find((p) => p.id === id)?.label ?? '';
  }

  readonly modes = BOARD_MODES;
  readonly styles3d = STYLES_3D;
  readonly styles2d = STYLES_2D;
}
