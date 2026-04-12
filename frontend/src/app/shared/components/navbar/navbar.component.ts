// src/app/shared/components/navbar/navbar.component.ts
import { Component, inject, HostListener } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AsyncPipe, NgIf } from '@angular/common';
import { Store } from '@ngrx/store';
import { selectUser } from '../../../store/account/account.reducer';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe, NgIf],
  template: `
    <nav class="nav" [class.nav--open]="menuOpen">
      <a class="nav-brand" routerLink="/home" (click)="closeMenu()">♟ Rex<span>Chess</span></a>

      <button class="nav-hamburger" (click)="toggleMenu()" [attr.aria-expanded]="menuOpen" aria-label="Menu">
        {{ menuOpen ? '✕' : '☰' }}
      </button>

      <div class="nav-links" [class.nav-links--open]="menuOpen">
        <a class="nav-btn" routerLink="/home" routerLinkActive="active" (click)="closeMenu()">Jouer</a>
        <ng-container *ngIf="user$ | async">
          <a class="nav-btn" routerLink="/game" routerLinkActive="active" (click)="closeMenu()">Partie en cours</a>
          <a class="nav-btn" routerLink="/account" routerLinkActive="active" (click)="closeMenu()">Mon Compte</a>
        </ng-container>
      </div>

      <div class="nav-right">
        <ng-container *ngIf="user$ | async as user; else guestTpl">
          <div class="notif-btn" title="Notifications">🔔</div>
          <a class="avatar" routerLink="/account" [title]="user.username" (click)="closeMenu()">
            {{ user.username.slice(0, 2).toUpperCase() }}
          </a>
        </ng-container>
        <ng-template #guestTpl>
          <a class="nav-btn" routerLink="/login" (click)="closeMenu()">Connexion</a>
        </ng-template>
      </div>
    </nav>
  `,
  styleUrls: ['./navbar.component.scss'],
})
export class NavbarComponent {
  private store = inject(Store);
  user$ = this.store.select(selectUser);
  menuOpen = false;

  toggleMenu(): void { this.menuOpen = !this.menuOpen; }
  closeMenu(): void  { this.menuOpen = false; }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.closeMenu(); }
}
