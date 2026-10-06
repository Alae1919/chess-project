// ─────────────────────────────────────────────────────────────────────────────
// src/app/features/home/pages/home.page.ts
// ─────────────────────────────────────────────────────────────────────────────
import { Component, DestroyRef, HostListener, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { GameActions } from '../../../store/game/game.actions';
import { selectCurrentGame, selectNotice, selectSavedGames } from '../../../store/game/game.selectors';
import { selectUser } from '../../../store/account/account.reducer';
import { AiDifficulty, Game, GameMode, GameOptions, PieceColor, TimeControl, User } from '../../../core/models';
import { AuthService } from '../../../core/services/auth.service';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { combineLatest, map } from 'rxjs';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { FloatyPiecesComponent } from '../../../shared/components/floaty-pieces/floaty-pieces.component';
import { MiniBoardComponent } from '../../../shared/components/mini-board/mini-board.component';
import { SheetDragDirective } from '../../../shared/directives/sheet-drag.directive';
import { isPlayableStatus } from '../../../core/utils/game-status.utils';
import { playerColorOf } from '../../../core/utils/game-result.utils';
import { isPhoneViewport } from '../../../core/utils/viewport';
import { AI_LEVEL_ELO, AI_LEVEL_LABELS, aiLevelLabel } from '../../../core/utils/ai-levels';

type TimeChoice = 'blitz' | 'rapid' | 'classical';
type RuleKey = 'enableUndo' | 'confirmMoves' | 'showLegalMoves' | 'realTimeAnalysis' | 'soundEnabled';

/** The game still going, as the "Reprendre" card shows it */
export interface ResumeCard {
  id: string;
  squares: Game['board']['squares'];
  title: string;
  sub: string;
}

export function resumeCard(game: Game | null, user: User | null): ResumeCard | null {
  if (!game || !isPlayableStatus(game.status)) return null;
  const mine = playerColorOf(game, user?.id);
  const them = mine === 'white' ? game.playerBlack : game.playerWhite;
  const title = game.mode === 'local' ? 'Partie locale'
    : them.isAi ? `vs IA · ${aiLevelLabel(them.aiDifficulty)}`
    : `vs ${them.username}`;
  const turn = !mine ? `aux ${game.currentTurn === 'white' ? 'Blancs' : 'Noirs'}`
    : game.currentTurn === mine ? 'à vous de jouer' : "à l'adversaire";
  return { id: game.id, squares: game.board?.squares ?? [], title, sub: `Tour ${game.fullMoveNumber} · ${turn}` };
}

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, ChessBoard3DComponent, FloatyPiecesComponent, MiniBoardComponent, SheetDragDirective],
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
})
export class HomePage implements OnInit {
  /** The decorative board wears the player's chosen 3D style */
  readonly look$ = inject(BoardPrefsService).prefs$.pipe(map((p) => p.style3d));
  private store = inject(Store);
  private fb = inject(FormBuilder);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  savedGames$ = this.store.select(selectSavedGames);
  /** Why the last game could not be started, if it could not */
  readonly notice$ = this.store.select(selectNotice);

  readonly greeting$ = this.store.select(selectUser).pipe(
    map((user) => (user ? `${new Date().getHours() < 18 ? 'Bonjour' : 'Bonsoir'}, ${user.username}` : 'REXCHESS')),
  );
  readonly resume$ = combineLatest([this.store.select(selectCurrentGame), this.store.select(selectUser)]).pipe(
    map(([game, user]) => resumeCard(game, user)),
  );

  selectedMode: GameMode = 'ai';
  selectedDifficulty: AiDifficulty = 1;
  selectedColor: PieceColor | 'random' = 'white';
  selectedTime: TimeChoice = 'blitz';
  /** The game options, open as a bottom sheet on a phone */
  setupOpen = false;

  optionsForm: FormGroup = this.fb.group({
    enableUndo: [true],
    confirmMoves: [false],
    showLegalMoves: [true],
    realTimeAnalysis: [false],
    soundEnabled: [true],
  });

  readonly difficultyLabels = AI_LEVEL_LABELS;
  readonly difficultyElo = AI_LEVEL_ELO;
  readonly timeControls: Record<TimeChoice, TimeControl> = {
    blitz:     { type: 'blitz',     initialMs: 5 * 60 * 1000,  incrementMs: 0 },
    rapid:     { type: 'rapid',     initialMs: 10 * 60 * 1000, incrementMs: 0 },
    classical: { type: 'classical', initialMs: 30 * 60 * 1000, incrementMs: 0 },
  };
  readonly timeChoices: { id: TimeChoice; label: string; mins: string }[] = [
    { id: 'blitz', label: 'Blitz', mins: '5′' },
    { id: 'rapid', label: 'Rapide', mins: '10′' },
    { id: 'classical', label: 'Classique', mins: '30′' },
  ];
  readonly rules: { key: RuleKey; label: string; hint?: string }[] = [
    { key: 'enableUndo', label: 'Annuler un coup', hint: 'Revenir au coup précédent' },
    { key: 'confirmMoves', label: 'Confirmer les coups' },
    { key: 'showLegalMoves', label: 'Afficher les coups légaux' },
    { key: 'realTimeAnalysis', label: 'Analyse en temps réel' },
    { key: 'soundEnabled', label: 'Sons et effets' },
  ];

  ngOnInit(): void {
    this.store.dispatch(GameActions.loadSavedGames());
    // The "Parties" tab is this page with ?mode=saved
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      if (params.get('mode') === 'saved') this.selectMode('saved');
      else if (this.selectedMode === 'saved') this.selectMode('ai');
    });
  }

  selectMode(mode: GameMode): void {
    this.selectedMode = mode;
  }

  /**
   * A mode card was tapped. On a phone that opens the next step straight away: the options
   * sheet, the lobby. Saved games have an address of their own, the "Parties" tab.
   */
  pickMode(mode: GameMode): void {
    const onSaved = this.route.snapshot.queryParamMap.get('mode') === 'saved';
    if (mode === 'saved') {
      this.router.navigate(['/home'], { queryParams: { mode: 'saved' } });
      return;
    }
    this.selectMode(mode);
    if (onSaved) this.router.navigate(['/home']);
    if (!isPhoneViewport()) return;
    if (mode === 'online') this.router.navigate(['/online']);
    else this.setupOpen = true;
  }

  @HostListener('document:keydown.escape')
  closeSetup(): void {
    this.setupOpen = false;
  }

  selectDifficulty(level: number): void {
    this.selectedDifficulty = level as AiDifficulty;
  }

  selectColor(color: PieceColor | 'random'): void {
    this.selectedColor = color;
  }

  selectTime(time: TimeChoice): void {
    this.selectedTime = time;
  }

  toggleRule(key: RuleKey): void {
    const control = this.optionsForm.get(key);
    control?.setValue(!control.value);
  }

  /** "Maître (≈ 2550) · Blancs · Rapide 10′" */
  get summary(): string {
    const time = this.timeChoices.find((t) => t.id === this.selectedTime)!;
    const color = { white: 'Blancs', black: 'Noirs', random: 'Couleur au hasard' }[this.selectedColor];
    const clock = `${time.label} ${time.mins}`;
    if (this.selectedMode === 'ai') {
      const i = this.selectedDifficulty - 1;
      return `${AI_LEVEL_LABELS[i]} (≈ ${AI_LEVEL_ELO[i]}) · ${color} · ${clock}`;
    }
    return `${color} · ${clock}`;
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
