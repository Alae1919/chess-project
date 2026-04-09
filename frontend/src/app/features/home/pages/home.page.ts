// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/home/pages/home.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { GameActions } from '../../../store/game/game.actions';
import { selectSavedGames } from '../../../store/game/game.selectors';
import { AiDifficulty, GameMode, GameOptions, PieceColor, TimeControl } from '../../../core/models';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
})
export class HomePage implements OnInit {
  private store = inject(Store);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  savedGames$ = this.store.select(selectSavedGames);

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

  startGame(): void {
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
