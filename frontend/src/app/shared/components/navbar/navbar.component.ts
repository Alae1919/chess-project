// src/app/shared/components/navbar/navbar.component.ts
import { Component, inject, HostListener } from '@angular/core';
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
    <nav class="nav" [class.nav--open]="menuOpen">
      <a class="nav__logo" routerLink="/" (click)="closeMenu()">REXCHESS</a>

      <button class="nav__hamburger" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen" aria-label="Menu">
        {{ menuOpen ? '✕' : '☰' }}
      </button>

      <div class="nav__links" [class.nav__links--open]="menuOpen">
        <a class="nav__link" routerLink="/home" routerLinkActive="active" (click)="closeMenu()">Jouer</a>
        <ng-container *ngIf="user$ | async">
          <a class="nav__link" *ngIf="activeGameId$ | async as gameId" [routerLink]="['/game', gameId]" routerLinkActive="active" (click)="closeMenu()">Partie en cours</a>
          <a class="nav__link" routerLink="/account" routerLinkActive="active" (click)="closeMenu()">Mon Compte</a>
        </ng-container>
      </div>

      <div class="nav__right">
        <ng-container *ngIf="user$ | async as user; else guestTpl">
          <div class="nav__notif" title="Notifications">🔔</div>
          <a class="nav__avatar" routerLink="/account" [title]="user.username" (click)="closeMenu()">
            {{ user.username.slice(0, 2).toUpperCase() }}
          </a>
        </ng-container>
        <ng-template #guestTpl>
          <a class="btn-g nav__cta" routerLink="/login" (click)="closeMenu()">Connexion</a>
          <a class="btn-p nav__cta" routerLink="/register" (click)="closeMenu()">S'inscrire</a>
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
  menuOpen = false;

  toggleMenu(): void { this.menuOpen = !this.menuOpen; }
  closeMenu(): void  { this.menuOpen = false; }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeMenu(); }
}
