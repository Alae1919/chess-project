import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavbarComponent } from './shared/components/navbar/navbar.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, NavbarComponent],
  template: `
    <app-navbar />
    <main class="app-main">
      <router-outlet />
    </main>
  `,
  styles: [`
    :host    { display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
    .app-main { flex: 1; overflow-y: auto; display: flex; flex-direction: column; }
  `],
})
export class AppComponent {}
