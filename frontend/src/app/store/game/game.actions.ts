// src/app/store/game/game.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { PieceColor, Game, GameOptions, Move, PositionEvaluation, SavedGame, Square, ChatMessage } from '../../core/models';

export const GameActions = createActionGroup({
  source: 'Game',
  events: {
    // Init
    'Create Game': props<{ options: GameOptions }>(),
    'Create Game Success': props<{ game: Game }>(),
    'Create Game Failure': props<{ error: string }>(),

    'Load Game': props<{ gameId: string }>(),
    'Load Game Success': props<{ game: Game }>(),
    'Load Game Failure': props<{ error: string }>(),

    // Moves
    'Select Square': props<{ square: Square }>(),
    'Clear Selection': emptyProps(),
    'Load Legal Moves Success': props<{ squares: Square[] }>(),

    'Submit Move': props<{ move: Omit<Move, 'algebraicNotation' | 'timestamp'> }>(),
    'Submit Move Success': props<{ game: Game }>(),
    'Submit Move Failure': props<{ error: string }>(),
    'Cancel Promotion': emptyProps(),

    'Undo Move': emptyProps(),
    'Undo Move Success': props<{ game: Game }>(),

    'Receive Move': props<{ game: Game }>(),  // via WebSocket

    // AI
    'Request AI Move': emptyProps(),
    'AI Move Success': props<{ game: Game }>(),
    'AI Move Failure': props<{ error: string }>(),

    // Game actions
    'Save Game': emptyProps(),
    'Save Game Success': props<{ savedGame: SavedGame }>(),
    'Offer Draw': emptyProps(),
    'Draw Response': props<{ accepted: boolean }>(),
    'Resign': emptyProps(),
    'Game Over': props<{ game: Game }>(),
    'Game Updated': props<{ game: Game }>(),   // same game, new state (e.g. a draw offer)
    'Dismiss Notice': emptyProps(),

    // The opponent's connection (online games). `until` is when they forfeit, epoch ms.
    'Opponent Disconnected': props<{ color: PieceColor; until: number }>(),
    'Opponent Reconnected': emptyProps(),

    // Evaluation
    'Update Evaluation': props<{ evaluation: PositionEvaluation }>(),

    // Saved games
    'Load Saved Games': emptyProps(),
    'Load Saved Games Success': props<{ savedGames: SavedGame[] }>(),

    // Chat
    'Load Chat Messages Success': props<{ messages: ChatMessage[] }>(),
    'Send Chat Message': props<{ content: string }>(),
    'Receive Chat Message': props<{ message: ChatMessage }>(),

    // Timer
    'Tick Timer': emptyProps(),

    // A game action (resign, chat...) failed; the effect stays alive
    'Request Failed': props<{ error: string }>(),

    // Reset
    'Reset Game': emptyProps(),
  },
});