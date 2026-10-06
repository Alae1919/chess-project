// src/app/features/landing/pages/landing.page.ts
import { Component, inject } from '@angular/core';
import { AsyncPipe, NgFor, NgIf } from '@angular/common';
import { RouterLink } from '@angular/router';
import { BoardPrefsService } from '../../../core/services/board-prefs.service';
import { map } from 'rxjs';
import { ChessBoard3DComponent } from '../../../shared/components/chess-board-3d/chess-board-3d.component';
import { FloatyPiecesComponent } from '../../../shared/components/floaty-pieces/floaty-pieces.component';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [AsyncPipe, NgFor, NgIf, RouterLink, ChessBoard3DComponent, FloatyPiecesComponent],
  templateUrl: './landing.page.html',
  styleUrls: ['./landing.page.scss'],
})
export class LandingPage {
  /** The decorative board wears the player's chosen 3D style */
  readonly look$ = inject(BoardPrefsService).prefs$.pipe(map((p) => p.style3d));

  /** Facts about the engine (see docs/how-the-ai-works.md) */
  readonly stats = [
    { value: '6', label: "Niveaux d'IA" },
    { value: '≈ 2900', label: 'Elo maximum' },
    { value: 'NNUE', label: 'Réseau neuronal' },
  ];

  readonly features = [
    { icon: '♟', title: 'IA adaptative', desc: 'Moteur haute performance adapté à votre niveau — du débutant au Grand Maître.' },
    { icon: '♜', title: 'Jeu en ligne', desc: 'Affrontez des joueurs du monde entier. Classement ELO en temps réel.' },
    { icon: '♛', title: 'Analyse en direct', desc: 'Évaluation de position, coups légaux, ouvertures reconnues automatiquement.' },
  ];

  readonly modes = [
    { icon: '♟', label: "Jouer contre l'IA", tag: 'Populaire', gold: false },
    { icon: '♞', label: 'Partie locale', tag: '', gold: false },
    { icon: '♜', label: 'Jeu en ligne', tag: 'ELO Classé', gold: true },
  ];

  scrollTo(id: string): void {
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
  }
}
