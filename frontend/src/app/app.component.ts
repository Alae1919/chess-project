import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { AuthService } from './core/services/auth.service';
import { AccountActions } from './store/account/account.actions';
import { LobbyWebSocketService } from './core/services/lobby-websocket.service';
import { InvitationToastComponent } from './features/online/components/invitation-toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent, InvitationToastComponent],
  template: `
    <app-navbar />
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

  ngOnInit(): void {
    if (this.authService.isLoggedIn) {
      this.store.dispatch(AccountActions.loadProfile());
      this.lobbyWsService.connect();
    }
  }
}
