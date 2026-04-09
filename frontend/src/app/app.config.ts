// src/app/app.config.ts
import { ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { provideStoreDevtools } from '@ngrx/store-devtools';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';
import { gameReducer } from './store/game/game.reducer';
import { accountReducer } from './store/account/account.reducer';
import { GameEffects } from './store/game/game.effects';
import { AccountEffects } from './store/account/account.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding(), withViewTransitions()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimations(),
    provideStore({
      game: gameReducer,
      account: accountReducer,
    }),
    provideEffects([GameEffects, AccountEffects]),
    provideStoreDevtools({ maxAge: 25, logOnly: false }),
  ],
};
