// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./features/landing/pages/landing.page').then((m) => m.LandingPage),
  },
  {
    path: 'home',
    loadComponent: () =>
      import('./features/home/pages/home.page').then((m) => m.HomePage),
  },
{
  path: 'login',
  loadComponent: () => import('./features/auth/pages/login/login.page').then((m) => m.LoginPage),
},
{
  path: 'register',
  loadComponent: () => import('./features/auth/pages/register/register.page').then((m) => m.RegisterPage),
},
  {
    path: 'game',
    loadComponent: () =>
      import('./features/game/pages/game.page').then((m) => m.GamePage),
    //canActivate: [authGuard],
  },
  {
    path: 'game/:id',
    loadComponent: () =>
      import('./features/game/pages/game.page').then((m) => m.GamePage),
    //canActivate: [authGuard],
  },
  {
    path: 'account',
    loadComponent: () =>
      import('./features/account/pages/account.page').then((m) => m.AccountPage),
    //canActivate: [authGuard],
  },
  {
    path: 'online',
    loadComponent: () =>
      import('./features/online/pages/online-lobby.page').then((m) => m.OnlineLobbyPage),
    canActivate: [authGuard],
  },
  {
    path: '**',
    redirectTo: '',
  },
];
