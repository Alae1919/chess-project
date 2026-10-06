// src/app/features/game/components/player-strip.component.ts
import { Component, Input } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';
import { GamePlayer, Piece, PieceColor } from '../../../core/models';
import { aiLevelLabel } from '../../../core/utils/ai-levels';
import { formatClock, isLowTime } from '../../../core/utils/clock.utils';

const SYMBOLS: Record<PieceColor, Record<string, string>> = {
  white: { king: '♔', queen: '♕', rook: '♖', bishop: '♗', knight: '♘', pawn: '♙' },
  black: { king: '♚', queen: '♛', rook: '♜', bishop: '♝', knight: '♞', pawn: '♟' },
};

/** One player on the phone layout: who, what they took, their clock. */
@Component({
  selector: 'app-player-strip',
  standalone: true,
  imports: [NgFor, NgIf],
  template: `
    <div class="strip" [class.strip--active]="active">
      <div class="avatar" [class.avatar--ai]="player.isAi" [class.avatar--white]="!player.isAi && player.color === 'white'">
        {{ player.isAi ? 'IA' : player.username.slice(0, 2).toUpperCase() }}
      </div>
      <div class="info">
        <span class="name">{{ player.isAi ? 'IA · ' + level : player.username }}</span>
        <span class="sub">
          <ng-container *ngIf="player.isAi && thinking; else idle">
            <span class="dots" aria-hidden="true"><span></span><span></span><span></span></span> réfléchit
          </ng-container>
          <ng-template #idle>
            <span *ngIf="player.isAi">Niveau {{ player.aiDifficulty }}/6</span>
            <span *ngIf="!player.isAi && player.elo">{{ player.elo }}</span>
          </ng-template>
          <span class="captures" *ngIf="captures.length" aria-label="Pièces prises">
            <span *ngFor="let c of captures" [class.cap--white]="c.color === 'white'">{{ symbol(c) }}</span>
          </span>
        </span>
      </div>
      <div class="clock" role="timer" [attr.aria-label]="'Temps restant ' + clock"
           [class.clock--running]="active" [class.clock--low]="low">
        {{ clock }}
      </div>
    </div>
  `,
  styles: [`
    :host { display: block; padding: 8px 14px; }
    .strip { display: flex; align-items: center; gap: 12px; }

    .avatar {
      width: 42px; height: 42px; flex-shrink: 0; border-radius: 12px;
      display: flex; align-items: center; justify-content: center;
      font: 600 14px var(--font); color: var(--gold);
      background: #1b1510; border: 1px solid rgba(201, 164, 92, .35); transition: box-shadow .3s;
    }
    .avatar--ai { font: 600 18px var(--serif); }
    .avatar--white { background: var(--text); color: #140f08; border-color: transparent; }
    .strip--active .avatar { box-shadow: 0 0 0 2px var(--bg), 0 0 0 3px rgba(230, 194, 122, .75); }

    .info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
    .name { font: 500 15px var(--font); color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .sub { display: flex; align-items: center; gap: 8px; min-height: 18px; font-size: 12px; color: var(--textc); }

    .dots { display: inline-flex; gap: 3px; }
    .dots span { width: 4px; height: 4px; border-radius: 50%; background: var(--p); animation: dot-pulse 1s infinite; }
    .dots span:nth-child(2) { animation-delay: .2s; }
    .dots span:nth-child(3) { animation-delay: .4s; }

    .captures {
      font: 15px 'Segoe UI Symbol', 'Noto Sans Symbols 2', serif; letter-spacing: -.12em; line-height: 1;
      white-space: nowrap; overflow: hidden; color: #17110b; text-shadow: 0 0 1px rgba(255, 240, 210, .9);
    }
    .cap--white { color: var(--text); text-shadow: 0 0 1px #000; }

    .clock {
      min-width: 86px; height: 42px; padding: 0 12px; flex-shrink: 0; border-radius: 11px;
      display: flex; align-items: center; justify-content: flex-end; gap: 8px;
      font: 400 24px var(--font); font-variant-numeric: tabular-nums; color: var(--textc);
      background: rgba(255, 255, 255, .035); border: 1px solid rgba(201, 164, 92, .14);
      transition: background .3s, color .3s, box-shadow .3s;
    }
    .clock--running {
      font-weight: 500; color: #140f08; border-color: transparent;
      background: linear-gradient(135deg, #f3dda2, #e6c27a 50%, #c9a45c); box-shadow: 0 0 22px rgba(230, 194, 122, .3);
    }
    .clock--running::before {
      content: ''; width: 6px; height: 6px; border-radius: 50%; background: currentColor;
      animation: glow-pulse 1s ease-in-out infinite;
    }
    .clock--low { color: #fff4ef; background: #b23a28; border-color: transparent; box-shadow: 0 0 22px rgba(214, 64, 46, .45); }
  `],
})
export class PlayerStripComponent {
  @Input({ required: true }) player!: GamePlayer;
  /** Their move in a game still going: the clock runs */
  @Input() active = false;
  /** The AI is working out its move */
  @Input() thinking = false;

  get level(): string { return aiLevelLabel(this.player.aiDifficulty); }
  /** The server leaves the list out when nothing has been taken */
  get captures(): Piece[] { return this.player.capturedPieces ?? []; }
  get clock(): string { return formatClock(this.player.timeRemainingMs ?? 0); }
  get low(): boolean { return isLowTime(this.player.timeRemainingMs); }

  /** U+FE0E keeps the pawns from turning into emoji */
  symbol(piece: Piece): string {
    const sym = SYMBOLS[piece.color]?.[piece.type];
    return sym ? sym + '︎' : '';
  }
}
