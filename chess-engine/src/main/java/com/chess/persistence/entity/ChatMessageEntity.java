package com.chess.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "sender_username", nullable = false, length = 50)
    private String senderUsername;

    @Column(nullable = false)
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public UUID getId()              { return id; }
    public UUID getGameId()          { return gameId; }
    public void setGameId(UUID v)    { this.gameId = v; }
    public UUID getSenderId()        { return senderId; }
    public void setSenderId(UUID v)  { this.senderId = v; }
    public String getSenderUsername(){ return senderUsername; }
    public void setSenderUsername(String v){ this.senderUsername = v; }
    public String getContent()       { return content; }
    public void setContent(String v) { this.content = v; }
    public Instant getSentAt()       { return sentAt; }
}


// ─── REPOSITORIES ────────────────────────────────────────────────────────────
