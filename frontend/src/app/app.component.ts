import { Component, inject, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { filter } from 'rxjs';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/services/auth.service';
import { AccountActions } from './store/account/account.actions';
import { LobbyWebSocketService } from './core/services/lobby-websocket.service';
import { InvitationToastComponent } from './features/online/components/invitation-toast.component';
import { makeMarble } from './shared/three/marble';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, InvitationToastComponent],
  template: `
    @if (showNavbar) { <app-navbar /> }
    <main class="app-main">
      <router-outlet />
    </main>
    <app-invitation-toast />
  `,
  styles: [`
    :host    { display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    .app-main { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
  `],
})
export class AppComponent implements OnInit {
  private store = inject(Store);
  private authService = inject(AuthService);
  private lobbyWsService = inject(LobbyWebSocketService);
  private router = inject(Router);

  /** The game arena has its own top bar */
  showNavbar = !location.pathname.startsWith('/game');

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => (this.showNavbar = !e.urlAfterRedirects.startsWith('/game')));
  }

  ngOnInit(): void {
    this.paintMarbleBackground();
    if (this.authService.isLoggedIn) {
      this.store.dispatch(AccountActions.loadProfile());
      this.lobbyWsService.connect();
    }
  }

  /** Black marble with gold veining behind every page; drawn once, off the critical path. */
  private paintMarbleBackground(): void {
    const paint = () => {
      const canvas = makeMarble(1400, '#0e0c0a', 'rgba(196,156,92,', ['rgba(40,32,24,.35)', 'rgba(0,0,0,.45)'], 7);
      document.body.style.backgroundImage = `url(${canvas.toDataURL('image/jpeg', 0.85)})`;
    };
    const idle = (window as any).requestIdleCallback as ((cb: () => void) => void) | undefined;
    idle ? idle(paint) : setTimeout(paint, 50);
  }
}
