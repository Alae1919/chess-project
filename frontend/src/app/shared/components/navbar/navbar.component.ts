// src/app/shared/components/navbar/navbar.component.ts
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AsyncPipe, NgIf } from '@angular/common';
import { Store } from '@ngrx/store';
import { selectUser } from '../../../store/account/account.reducer';
import { selectCurrentGame } from '../../../store/game/game.selectors';
import { isPlayableStatus } from '../../../core/utils/game-status.utils';
import { map } from 'rxjs';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe, NgIf],
  template: `
    <nav class="nav">
      <a class="nav__logo" routerLink="/">REXCHESS</a>
      <!-- on phones these live in the bottom tab bar -->
      <div class="nav__links">
        <a class="nav__link" routerLink="/home" routerLinkActive="active">Jouer</a>
        <ng-container *ngIf="user$ | async">
          <a class="nav__link" *ngIf="activeGameId$ | async as gameId" [routerLink]="['/game', gameId]" routerLinkActive="active">Partie en cours</a>
          <a class="nav__link" routerLink="/account" routerLinkActive="active">Mon Compte</a>
        </ng-container>
      </div>
      <div class="nav__right">
        <ng-container *ngIf="user$ | async as user; else guestTpl">
          <button type="button" class="nav__notif" aria-label="Notifications">
            <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M6 16V11a6 6 0 0112 0v5l1.5 2h-15z"/><path d="M10 20.5a2 2 0 004 0"/></svg>
          </button>
          <a class="nav__avatar" routerLink="/account" [title]="user.username" aria-label="Mon compte">
            {{ user.username.slice(0, 2).toUpperCase() }}
          </a>
        </ng-container>
        <ng-template #guestTpl>
          <a class="btn-g nav__cta" routerLink="/login">Connexion</a>
          <a class="btn-p nav__cta" routerLink="/register">S'inscrire</a>
        </ng-template>
      </div>
    </nav>
  `,
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  private store = inject(Store);
  user$ = this.store.select(selectUser);
  /** Id of the game still being played, or null when there is none */
  activeGameId$ = this.store.select(selectCurrentGame).pipe(
    map((game) => (game && isPlayableStatus(game.status) ? game.id : null))
  );
}
