import { Component, HostBinding, inject, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { filter } from 'rxjs';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/services/auth.service';
import { AccountActions } from './store/account/account.actions';
import { SessionActions } from './store/session/session.actions';
import { LobbyWebSocketService } from './core/services/lobby-websocket.service';
import { WebSocketService } from './core/services/websocket.service';
import { InvitationToastComponent } from './features/online/components/invitation-toast.component';
import { makeMarble } from './shared/three/marble';
import { TabBarComponent, tabFor } from './shared/components/tab-bar/tab-bar.component';

/** The landing page, whatever query or fragment follows the slash */
export function isLandingUrl(url: string): boolean {
  return url.split(/[?#]/)[0] === '/';
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, InvitationToastComponent, TabBarComponent],
  template: `
    @if (showNavbar) { <app-navbar [overlay]="isLanding" /> }
    <main class="app-main" [class.app-main--tabbed]="showTabBar">
      <router-outlet />
    </main>
    @if (showTabBar) { <app-tab-bar /> }
    <app-invitation-toast />
  `,
  styles: [`
    :host    { display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden; }
    .app-main { flex: 1; overflow-y: auto; overscroll-behavior: none; display: flex; flex-direction: column; }
    /* the landing film runs under a see-through bar, from the very top of the screen */
    :host(.app--landing) app-navbar { position: absolute; top: 0; left: 0; right: 0; z-index: 100; }
    /* room under the content for the phone's bottom tab bar */
    @media (max-width: 768px) {
      .app-main--tabbed { padding-bottom: calc(var(--tabbar-h) + env(safe-area-inset-bottom)); }
    }
  `],
})
export class AppComponent implements OnInit {
  private store = inject(Store);
  private authService = inject(AuthService);
  private lobbyWsService = inject(LobbyWebSocketService);
  private gameWsService = inject(WebSocketService);
  private router = inject(Router);

  /** The game arena has its own top bar */
  showNavbar = !location.pathname.startsWith('/game');
  /** The phone's bottom tab bar, on the pages it leads to */
  showTabBar = tabFor(location.pathname + location.search) !== null;
  /** The landing page draws its film under the navbar */
  @HostBinding('class.app--landing') isLanding = isLandingUrl(location.pathname);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.showNavbar = !e.urlAfterRedirects.startsWith('/game');
        this.showTabBar = tabFor(e.urlAfterRedirects) !== null;
        this.isLanding = isLandingUrl(e.urlAfterRedirects);
      });
  }

  ngOnInit(): void {
    this.paintMarbleBackground();
    // The profile and the sockets live exactly as long as the session: invites
    // arrive right after logging in, and nothing stays open after logging out
    this.authService.loggedIn$.subscribe((loggedIn) => {
      if (loggedIn) {
        this.store.dispatch(AccountActions.loadProfile());
        this.lobbyWsService.connect();
      } else {
        this.lobbyWsService.disconnect();
        this.gameWsService.disconnect();
        // and nothing of the last person stays on screen: the navbar, a game, invitations
        this.store.dispatch(SessionActions.ended());
      }
    });
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
