// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/home/pages/home.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { GameActions } from '../../../store/game/game.actions';
import { selectNotice, selectSavedGames } from '../../../store/game/game.selectors';
import { AiDifficulty, GameMode, GameOptions, PieceColor, TimeControl } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { map } from 'rxjs';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { FloatyPiecesComponent } from '../../../shared/components/floaty-pieces/floaty-pieces.component';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ChessBoard3DComponent, FloatyPiecesComponent],
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
})
export class HomePage implements OnInit {
  /** The decorative board wears the player's chosen 3D style */
  readonly look$ = inject(BoardPrefsService).prefs$.pipe(map((p) => p.style3d));
  private store = inject(Store);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private auth = inject(AuthService);

  savedGames$ = this.store.select(selectSavedGames);
  /** Why the last game could not be started, if it could not */
  readonly notice$ = this.store.select(selectNotice);

  selectedMode: GameMode = 'ai';
  selectedDifficulty: AiDifficulty = 1;
  selectedColor: PieceColor | 'random' = 'white';
  selectedTime: 'blitz' | 'rapid' | 'classical' = 'blitz';

  optionsForm: FormGroup = this.fb.group({
    enableUndo: [true],
    confirmMoves: [false],
    showLegalMoves: [true],
    realTimeAnalysis: [false],
    soundEnabled: [true],
  });

  readonly difficultyLabels = ['Facile', 'Moyen', 'Difficile', 'Expert', 'Maître', 'Maximum'];
  /** Approximate strength of each level, measured against Stockfish (see docs/how-the-ai-works.md) */
  readonly difficultyElo = [1100, 1400, 1750, 2050, 2550, 2900];
  readonly timeControls: Record<string, TimeControl> = {
    blitz:     { type: 'blitz',     initialMs: 5 * 60 * 1000,  incrementMs: 0 },
    rapid:     { type: 'rapid',     initialMs: 10 * 60 * 1000, incrementMs: 0 },
    classical: { type: 'classical', initialMs: 30 * 60 * 1000, incrementMs: 0 },
  };

  ngOnInit(): void {
    this.store.dispatch(GameActions.loadSavedGames());
  }

  selectMode(mode: GameMode): void {
    this.selectedMode = mode;
  }

  selectDifficulty(level: number): void {
    this.selectedDifficulty = level as AiDifficulty;
  }

  selectColor(color: PieceColor | 'random'): void {
    this.selectedColor = color;
  }

  selectTime(time: 'blitz' | 'rapid' | 'classical'): void {
    this.selectedTime = time;
  }

  /** A saved game is picked from the list, so there is nothing to start in that mode. */
  get canStart(): boolean {
    return this.selectedMode !== 'saved';
  }

  startGame(): void {
    if (!this.canStart) return;
    // Games belong to an account: without one the request would only fail, silently
    if (!this.auth.isLoggedIn) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/home' } });
      return;
    }
    if (this.selectedMode === 'online') {
      this.router.navigate(['/online']);
      return;
    }
    const options: GameOptions = {
      mode: this.selectedMode,
      aiDifficulty: this.selectedDifficulty,
      playerColor: this.selectedColor,
      timeControl: this.timeControls[this.selectedTime],
      ...this.optionsForm.value,
    };
    this.store.dispatch(GameActions.createGame({ options }));
  }

  loadSaved(id: string): void {
    this.store.dispatch(GameActions.loadGame({ gameId: id }));
  }
}
