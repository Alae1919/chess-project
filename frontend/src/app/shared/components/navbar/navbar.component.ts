// src/app/shared/components/navbar/navbar.component.ts
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AsyncPipe, NgIf } from '@angular/common';
import { Store } from '@ngrx/store';
import { selectUser } from '../../../store/account/account.reducer';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe, NgIf],
  template: `
    <nav class="nav">
      <a class="nav-brand" routerLink="/home">♟ Rex<span>Chess</span></a>

      <div class="nav-links">
        <a class="nav-btn" routerLink="/home" routerLinkActive="active">Jouer</a>
        <a class="nav-btn" routerLink="/game" routerLinkActive="active">Partie en cours</a>
        <a class="nav-btn" routerLink="/account" routerLinkActive="active">Mon Compte</a>
      </div>

      <div class="nav-right">
        <ng-container *ngIf="user$ | async as user; else guestTpl">
          <div class="notif-btn" title="Notifications">🔔</div>
          <a class="avatar" routerLink="/account" [title]="user.username">
            {{ user.username.slice(0, 2).toUpperCase() }}
          </a>
        </ng-container>
        <ng-template #guestTpl>
          <a class="nav-btn" routerLink="/login">Connexion</a>
        </ng-template>
      </div>
    </nav>
  `,
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  private store = inject(Store);
  user$ = this.store.select(selectUser);
}
