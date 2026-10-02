// src/app/core/models/index.ts

// ─── Auth ────────────────────────────────────────────────────────────────────

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

// ─── User / Account ──────────────────────────────────────────────────────────

export interface User {
  id: string;
  username: string;
  email: string;
  avatarUrl?: string;
  country: string;
  elo: number;
  rank: PlayerRank;
  memberSince: Date;
  stats: UserStats;
  preferences: UserPreferences;
  subscription: Subscription;
  achievements: Achievement[];
}

export type PlayerRank = 'Bronze' | 'Argent' | 'Or' | 'Platine' | 'Diamant' | 'Grand Maître';

export interface UserStats {
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
  winRate: number;
  currentStreak: number;
  bestStreak: number;
  eloHistory: EloPoint[];
}

export interface EloPoint {
  date: Date;
  elo: number;
}

export interface UserPreferences {
  boardTheme: BoardTheme;
  pieceStyle: PieceStyle;
  /** Which board the game page draws, and the look chosen for each */
  boardMode?: BoardMode;
  boardStyle3d?: Style3D;
  boardStyle2d?: Style2D;
  language: string;
  soundEnabled: boolean;
  animationsEnabled: boolean;
  notificationsEnabled: boolean;
  confirmMoves: boolean;
  showLegalMoves: boolean;
  enableUndo: boolean;
  realTimeAnalysis: boolean;
}

export type BoardMode = '2d' | '3d';
export type Style3D = 'marble-gold' | 'classic-wood' | 'ebony-ivory';
export type Style2D = 'classic-wood' | 'luxe' | 'slate-blue';
export type BoardTheme = 'classic-wood' | 'marble-green' | 'slate' | 'blue-night';
export type PieceStyle = 'standard' | 'modern' | 'minimalist';

export interface Subscription {
  plan: 'Free' | 'Silver' | 'Gold' | 'Platine';
  active: boolean;
  renewsAt?: Date;
}

export interface Achievement {
  id: string;
  icon: string;
  name: string;
  description: string;
  unlocked: boolean;
  progress?: number;
  target?: number;
}

// ─── Game ────────────────────────────────────────────────────────────────────

export interface Game {
  id: string;
  mode: GameMode;
  status: GameStatus;
  playerWhite: GamePlayer;
  playerBlack: GamePlayer;
  board: BoardState;
  moves: Move[];
  currentTurn: PieceColor;
  timeControl: TimeControl;
  enPassantTarget: Square | null;
  castlingRights: CastlingRights;
  halfMoveClock: number;
  fullMoveNumber: number;
  result?: GameResult;
  opening?: string;
  fen?: string;
  /** Who has an unanswered draw offer on the table */
  drawOfferedBy?: PieceColor | null;
  createdAt: Date;
  updatedAt: Date;
}

export type GameMode = 'ai' | 'local' | 'online' | 'saved';
export type GameStatus =
  | 'waiting' | 'active' | 'check' | 'paused' | 'finished' | 'aborted'
  | 'checkmate' | 'stalemate' | 'draw_50_move' | 'draw_insufficient_material' | 'draw_repetition' | 'white_flagged' | 'black_flagged' | 'white_abandoned' | 'black_abandoned'
  | 'white_resigned' | 'black_resigned' | 'draw_agreed';
export type PieceColor = 'white' | 'black';

export interface GamePlayer {
  userId?: string;
  username: string;
  elo?: number;
  color: PieceColor;
  timeRemainingMs: number;
  capturedPieces: Piece[];
  isAi?: boolean;
  aiDifficulty?: AiDifficulty;
}

export type AiDifficulty = 1 | 2 | 3 | 4 | 5 | 6;

export interface BoardState {
  squares: (Piece | null)[][];  // [row][col], row 0 = rank 8
}

export interface Piece {
  type: PieceType;
  color: PieceColor;
}

export type PieceType = 'king' | 'queen' | 'rook' | 'bishop' | 'knight' | 'pawn';

export interface Square {
  row: number;  // 0–7 (0 = rank 8)
  col: number;  // 0–7 (0 = file a)
}

export interface Move {
  from: Square;
  to: Square;
  piece: Piece;
  capturedPiece?: Piece;
  promotion?: PieceType;
  isEnPassant?: boolean;
  isCastling?: 'kingside' | 'queenside';
  isCheck?: boolean;
  isCheckmate?: boolean;
  algebraicNotation: string;
  timestamp: Date;
}

export interface CastlingRights {
  whiteKingside: boolean;
  whiteQueenside: boolean;
  blackKingside: boolean;
  blackQueenside: boolean;
}

export interface TimeControl {
  type: 'blitz' | 'rapid' | 'classical' | 'unlimited';
  initialMs: number;
  incrementMs: number;
}

export interface GameResult {
  winner?: PieceColor;
  reason: GameEndReason;
  /** Rating points gained or lost; only set for finished online games */
  whiteEloChange?: number | null;
  blackEloChange?: number | null;
}

export type GameEndReason =
  | 'checkmate'
  | 'stalemate'
  | 'resignation'
  | 'timeout'
  | 'draw_agreement'
  | 'threefold_repetition'
  | 'fifty_move_rule'
  | 'insufficient_material'
  | 'abandonment';

// ─── Game Options (Page 1) ────────────────────────────────────────────────────

export interface GameOptions {
  mode: GameMode;
  aiDifficulty: AiDifficulty;
  playerColor: PieceColor | 'random';
  timeControl: TimeControl;
  enableUndo: boolean;
  confirmMoves: boolean;
  showLegalMoves: boolean;
  realTimeAnalysis: boolean;
  savedGameId?: string;
}

// ─── Saved Game ───────────────────────────────────────────────────────────────

export interface SavedGame {
  id: string;
  gameId: string;
  opponentName: string;
  mode: GameMode;
  turnNumber: number;
  playerColor: PieceColor;
  opening?: string;
  savedAt: Date;
  thumbnailFen?: string;
}

// ─── Chat ────────────────────────────────────────────────────────────────────

export interface ChatMessage {
  id: string;
  gameId: string;
  senderId: string;
  senderUsername: string;
  content: string;
  sentAt: Date;
}

// ─── Match History ────────────────────────────────────────────────────────────

export interface MatchHistory {
  id: string;
  opponentUsername: string;
  mode: GameMode;
  timeControlLabel: string;
  result: 'win' | 'loss' | 'draw';
  playerColor: PieceColor;
  movesCount: number;
  eloDelta?: number;
  playedAt: Date;
}

// ─── Evaluation (Engine) ─────────────────────────────────────────────────────

export interface PositionEvaluation {
  score: number;       // centipawns, positive = white advantage
  depth: number;
  bestMove?: string;   // algebraic
  openingName?: string;
}

// ─── Online / Lobby ───────────────────────────────────────────────────────────

export interface UserSummary {
  id: string;
  username: string;
  elo: number;
  avatarUrl?: string;
}

export interface QueueEntry {
  queueEntryId: string;
  status: 'queued' | 'matched';
  joinedAt: string;
  estimatedWaitSeconds?: number;
}

export interface GameInvitation {
  invitationId: string;
  inviterUsername: string;
  inviteeUsername: string;
  status: 'pending' | 'accepted' | 'declined' | 'expired' | 'cancelled';
  timeControlType: string;
  timeControlInitialMs: number;
  timeControlIncrementMs: number;
  createdAt: string;
  expiresAt: string;
}

export interface MatchFoundPayload {
  gameId: string;
  opponentUsername: string;
  opponentElo: number;
  playerColor: PieceColor;
  timeControlType: string;
  timeControlInitialMs: number;
  timeControlIncrementMs: number;
}
