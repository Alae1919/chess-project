package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_moves")
public class GameMoveEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private GameEntity game;

    @Column(name = "move_number", nullable = false)
    private int moveNumber;

    @Column(nullable = false, length = 10)
    private String color;

    @Column(name = "from_row", nullable = false) private int fromRow;
    @Column(name = "from_col", nullable = false) private int fromCol;
    @Column(name = "to_row",   nullable = false) private int toRow;
    @Column(name = "to_col",   nullable = false) private int toCol;

    @Column(name = "piece_type",   nullable = false, length = 10) private String pieceType;
    @Column(name = "piece_color",  nullable = false, length = 10) private String pieceColor;
    @Column(name = "captured_type",  length = 10) private String capturedType;
    @Column(name = "captured_color", length = 10) private String capturedColor;

    @Column(length = 10)
    private String promotion;

    @Column(name = "is_en_passant", nullable = false) private boolean isEnPassant = false;
    @Column(name = "is_castling",   length = 10)      private String isCastling;
    @Column(name = "is_check",      nullable = false) private boolean isCheck = false;
    @Column(name = "is_checkmate",  nullable = false) private boolean isCheckmate = false;

    @Column(name = "algebraic_notation", nullable = false, length = 10)
    private String algebraicNotation;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt = Instant.now();

    public GameMoveEntity() {}

    public Long getId()                    { return id; }
    public GameEntity getGame()            { return game; }
    public void setGame(GameEntity v)      { this.game = v; }
    public int getMoveNumber()             { return moveNumber; }
    public void setMoveNumber(int v)       { this.moveNumber = v; }
    public String getColor()               { return color; }
    public void setColor(String v)         { this.color = v; }
    public int getFromRow()                { return fromRow; }
    public void setFromRow(int v)          { this.fromRow = v; }
    public int getFromCol()                { return fromCol; }
    public void setFromCol(int v)          { this.fromCol = v; }
    public int getToRow()                  { return toRow; }
    public void setToRow(int v)            { this.toRow = v; }
    public int getToCol()                  { return toCol; }
    public void setToCol(int v)            { this.toCol = v; }
    public String getPieceType()           { return pieceType; }
    public void setPieceType(String v)     { this.pieceType = v; }
    public String getPieceColor()          { return pieceColor; }
    public void setPieceColor(String v)    { this.pieceColor = v; }
    public String getCapturedType()        { return capturedType; }
    public void setCapturedType(String v)  { this.capturedType = v; }
    public String getCapturedColor()       { return capturedColor; }
    public void setCapturedColor(String v) { this.capturedColor = v; }
    public String getPromotion()           { return promotion; }
    public void setPromotion(String v)     { this.promotion = v; }
    public boolean isEnPassant()           { return isEnPassant; }
    public void setEnPassant(boolean v)    { this.isEnPassant = v; }
    public String getIsCastling()          { return isCastling; }
    public void setIsCastling(String v)    { this.isCastling = v; }
    public boolean isCheck()               { return isCheck; }
    public void setCheck(boolean v)        { this.isCheck = v; }
    public boolean isCheckmate()           { return isCheckmate; }
    public void setCheckmate(boolean v)    { this.isCheckmate = v; }
    public String getAlgebraicNotation()   { return algebraicNotation; }
    public void setAlgebraicNotation(String v){ this.algebraicNotation = v; }
    public Instant getPlayedAt()           { return playedAt; }
    public void setPlayedAt(Instant v)     { this.playedAt = v; }
}
