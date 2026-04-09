# RexChess — Frontend Angular 17

Application d'échecs full-stack. Le frontend Angular communique avec un backend Java (Spring Boot) via REST + WebSocket.

---

## 🚀 Démarrage rapide

```bash
npm install
ng serve
# → http://localhost:4200
```

Le backend Java doit tourner sur `http://localhost:8080`.  
Le proxy Angular (`proxy.conf.json`) redirige automatiquement `/api` et `/ws`.

---

## 🗂️ Architecture complète

```
src/
├── app/
│   ├── app.component.ts          ← Racine : navbar + router-outlet
│   ├── app.config.ts             ← Providers globaux (router, HTTP, NgRx, animations)
│   ├── app.routes.ts             ← Routes lazy-loaded
│   │
│   ├── core/                     ← Singleton — chargé une seule fois
│   │   ├── models/
│   │   │   └── index.ts          ← Toutes les interfaces TypeScript
│   │   ├── services/
│   │   │   ├── auth.service.ts       ← Login, register, refresh token, logout
│   │   │   ├── game.service.ts       ← CRUD parties, coups, IA, sauvegarde
│   │   │   ├── account.service.ts    ← Profil, préférences, historique, succès
│   │   │   ├── chat.service.ts       ← Messages de chat par partie
│   │   │   └── websocket.service.ts  ← Connexion WS temps réel (coups, chat, timer)
│   │   ├── guards/
│   │   │   └── auth.guard.ts         ← Protège /game et /account
│   │   └── interceptors/
│   │       ├── auth.interceptor.ts   ← Injecte Bearer token sur chaque requête
│   │       └── error.interceptor.ts  ← Gère 401 + refresh token automatique
│   │
│   ├── store/                    ← NgRx State Management
│   │   ├── game/
│   │   │   ├── game.actions.ts   ← Toutes les actions (createGame, submitMove, chat…)
│   │   │   ├── game.reducer.ts   ← État + reducer + selectors
│   │   │   └── game.effects.ts   ← Side effects (appels API, WebSocket, timer)
│   │   └── account/
│   │       └── account.reducer.ts ← Actions + reducer + effects + selectors
│   │
│   ├── shared/                   ← Composants réutilisables
│   │   └── components/
│   │       ├── navbar/           ← Barre de navigation globale
│   │       └── chess-board/      ← Échiquier interactif (connecté au store)
│   │
│   └── features/                 ← Pages (lazy-loaded)
│       ├── home/
│       │   └── pages/home.page   ← Page 1 : options de jeu
│       ├── game/
│       │   └── pages/game.page   ← Page 2 : partie en cours
│       └── account/
│           └── pages/account.page ← Page 3 : gestion du compte
│
├── environments/
│   ├── environment.ts            ← Dev  : localhost:8080
│   └── environment.prod.ts       ← Prod : votre domaine
│
└── styles.scss                   ← Design system global (variables CSS, composants)
```

---

## 🔌 Intégration avec le backend Java

### REST API attendue

| Méthode | Endpoint                          | Description                          |
|---------|-----------------------------------|--------------------------------------|
| POST    | `/api/auth/login`                 | Login → retourne `AuthTokens`        |
| POST    | `/api/auth/register`              | Inscription                          |
| POST    | `/api/auth/refresh`               | Refresh token                        |
| GET     | `/api/auth/me`                    | Utilisateur connecté                 |
| POST    | `/api/games`                      | Créer une partie                     |
| GET     | `/api/games/:id`                  | Récupérer une partie                 |
| POST    | `/api/games/:id/moves`            | Soumettre un coup (validation Java)  |
| POST    | `/api/games/:id/ai-move`          | Demander le coup de l'IA             |
| DELETE  | `/api/games/:id/moves/last`       | Annuler le dernier coup              |
| GET     | `/api/games/:id/legal-moves`      | Coups légaux pour une case           |
| GET     | `/api/games/:id/evaluation`       | Évaluation de position               |
| POST    | `/api/games/:id/save`             | Sauvegarder la partie                |
| GET     | `/api/games/saved`                | Liste des parties sauvegardées       |
| POST    | `/api/games/:id/resign`           | Abandonner                           |
| POST    | `/api/games/:id/draw`             | Proposer/accepter la nulle           |
| GET     | `/api/account/profile`            | Profil utilisateur                   |
| PATCH   | `/api/account/profile`            | Modifier le profil                   |
| PUT     | `/api/account/preferences`        | Mettre à jour les préférences        |
| GET     | `/api/account/history`            | Historique des parties               |
| GET     | `/api/account/achievements`       | Succès débloqués                     |
| DELETE  | `/api/account`                    | Supprimer le compte                  |

### WebSocket

Connexion : `ws://localhost:8080/ws/game/:gameId?token=<jwt>`

Événements reçus du serveur :
```typescript
{ type: 'MOVE_MADE',      payload: Game }       // Coup joué (IA ou adversaire)
{ type: 'GAME_UPDATED',   payload: Game }       // Mise à jour générale
{ type: 'CHAT_MESSAGE',   payload: ChatMessage }// Nouveau message
{ type: 'GAME_OVER',      payload: Game }       // Fin de partie
{ type: 'TIMER_TICK',     payload: any }        // Tick du timer (optionnel)
```

---

## 🏪 État NgRx

### GameState
```typescript
{
  currentGame:    Game | null,
  savedGames:     SavedGame[],
  selectedSquare: Square | null,      // Case sélectionnée par le joueur
  legalMoves:     Square[],           // Coups légaux pour la case sélectionnée
  evaluation:     PositionEvaluation | null,
  chatMessages:   ChatMessage[],
  isLoading:      boolean,
  isAiThinking:   boolean,
  error:          string | null,
}
```

### AccountState
```typescript
{
  user:               User | null,
  matchHistory:       MatchHistory[],
  matchHistoryTotal:  number,
  achievements:       Achievement[],
  isLoading:          boolean,
  error:              string | null,
}
```

---

## 🎨 Design system

Variables CSS définies dans `src/styles.scss` :

| Variable      | Valeur        | Usage                     |
|---------------|---------------|---------------------------|
| `--bg`        | `#0C0F0A`     | Fond principal            |
| `--surface`   | `#1E261B`     | Panneaux / cartes         |
| `--em3`       | `#52B788`     | Accent émeraude           |
| `--gold`      | `#C9A84C`     | Accent or                 |
| `--text`      | `#EEF0EB`     | Texte principal           |
| `--border`    | `rgba(82,183,136,.12)` | Bordures subtiles |

Polices : **Cormorant Garamond** (titres) + **DM Sans** (corps)

---

## 📋 TODO — Intégration

- [ ] Implémenter `AuthService.login()` et stocker le JWT
- [ ] Brancher `GameService.submitMove()` sur votre endpoint Java
- [ ] Configurer `GameService.getLegalMoves()` (ou gérer côté client en attendant)
- [ ] Brancher `WebSocketService` sur votre endpoint STOMP/WS
- [ ] Implémenter la détection d'échec dans `ChessBoardComponent.isKingInCheck()`
- [ ] Ajouter la gestion de la promotion du pion (modal de choix)
- [ ] Ajouter la page de classement (Leaderboard)
- [ ] Ajouter la page d'analyse de partie (replay + commentaires)
