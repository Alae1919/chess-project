// src/app/shared/components/tab-bar/tab-bar.component.ts
import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, startWith } from 'rxjs';
import { AsyncPipe } from '@angular/common';

export type TabId = 'play' | 'online' | 'saved' | 'profile';

/** Which tab a page belongs to, from its address */
export function tabFor(url: string): TabId | null {
  const [path, query = ''] = url.split('?');
  if (path.startsWith('/home')) return new URLSearchParams(query).get('mode') === 'saved' ? 'saved' : 'play';
  if (path.startsWith('/online')) return 'online';
  if (path.startsWith('/account')) return 'profile';
  return null;
}

/**
 * The phone's main navigation, at the bottom where the thumb is. Hidden above the
 * phone breakpoint, where the top navbar carries the links.
 */
@Component({
  selector: 'app-tab-bar',
  standalone: true,
  imports: [RouterLink, AsyncPipe],
  template: `
    @if ({ id: active$ | async }; as active) {
    <nav class="tabs" aria-label="Navigation principale">
      <a class="tab" routerLink="/home" [class.tab--on]="active.id === 'play'"
         [attr.aria-current]="active.id === 'play' ? 'page' : null">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="6.5" r="2.6"/><path d="M10.2 9.2L9 15h6l-1.2-5.8"/><path d="M7.5 19.5h9l-1-4.5h-7z"/></svg>
        Jouer
      </a>
      <a class="tab" routerLink="/online" [class.tab--on]="active.id === 'online'"
         [attr.aria-current]="active.id === 'online' ? 'page' : null">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17"/><path d="M12 3.5c2.4 2.6 3.4 5.5 3.4 8.5s-1 5.9-3.4 8.5c-2.4-2.6-3.4-5.5-3.4-8.5s1-5.9 3.4-8.5z"/></svg>
        En ligne
      </a>
      <a class="tab" routerLink="/home" [queryParams]="{ mode: 'saved' }" [class.tab--on]="active.id === 'saved'"
         [attr.aria-current]="active.id === 'saved' ? 'page' : null">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10v16l-5-3.6L7 20z"/></svg>
        Parties
      </a>
      <a class="tab" routerLink="/account" [class.tab--on]="active.id === 'profile'"
         [attr.aria-current]="active.id === 'profile' ? 'page' : null">
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="8.5" r="3.6"/><path d="M5 20c1.2-3.6 4-5.5 7-5.5s5.8 1.9 7 5.5"/></svg>
        Profil
      </a>
    </nav>
    }
  `,
  styles: [`
    :host { display: none; }
    @media (max-width: 768px) {
      :host {
        display: block; position: fixed; left: 0; right: 0; bottom: 0; z-index: 90;
      }
    }
    .tabs {
      display: flex; height: calc(var(--tabbar-h) + env(safe-area-inset-bottom));
      padding: 0 8px env(safe-area-inset-bottom);
      background: rgba(14, 11, 8, .88); backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
      border-top: 1px solid var(--border);
    }
    .tab {
      flex: 1; position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
      font: 500 11px var(--font); letter-spacing: .06em; color: var(--textc);
      -webkit-tap-highlight-color: transparent; transition: color .2s;
    }
    .tab:hover { color: var(--text); }
    .tab::before {
      content: ''; position: absolute; top: -1px; width: 22px; height: 2px; border-radius: 0 0 2px 2px;
      background: var(--gold); transform: scaleX(0); transition: transform .25s var(--ease-spring);
    }
    .tab--on, .tab--on:hover { color: var(--gold); }
    .tab--on::before { transform: scaleX(1); }
    .tab:active svg { transform: scale(.9); }
    svg {
      width: 24px; height: 24px; fill: none; stroke: currentColor; stroke-width: 1.6;
      stroke-linecap: round; stroke-linejoin: round; transition: transform .09s;
    }
  `],
})
export class TabBarComponent {
  private router = inject(Router);

  readonly active$ = this.router.events.pipe(
    filter((e): e is NavigationEnd => e instanceof NavigationEnd),
    map((e) => e.urlAfterRedirects),
    startWith(this.router.url),
    map(tabFor),
  );
}
