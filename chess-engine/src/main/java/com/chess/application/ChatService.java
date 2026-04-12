package com.chess.application;

import com.chess.api.dto.ChatDto;
import com.chess.infrastructure.api.exception.GameNotFoundException;
import com.chess.persistence.entity.ChatMessageEntity;
import com.chess.persistence.repository.ChatMessageRepository;
import com.chess.persistence.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMessageRepository chatRepo;
    private final GameRepository gameRepo;

    public ChatService(ChatMessageRepository chatRepo, GameRepository gameRepo) { 
        this.chatRepo = chatRepo; 
        this.gameRepo = gameRepo;
    }

    public List<ChatDto.ChatMessage> getMessages(UUID gameId) {
        if (!gameRepo.existsById(gameId)) {
            throw new GameNotFoundException(gameId.toString());
        }
        return chatRepo.findByGameIdOrderBySentAtAsc(gameId)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public ChatDto.ChatMessage send(UUID gameId, UUID senderId,
                                    String senderUsername, String content) {
        if (!gameRepo.existsById(gameId)) {
            throw new GameNotFoundException(gameId.toString());
        }
        var entity = new ChatMessageEntity();
        entity.setGameId(gameId);
        entity.setSenderId(senderId);
        entity.setSenderUsername(senderUsername);
        entity.setContent(content);
        chatRepo.save(entity);
        return toDto(entity);
    }

    private ChatDto.ChatMessage toDto(ChatMessageEntity e) {
        return new ChatDto.ChatMessage(
            e.getId().toString(), e.getGameId().toString(),
            e.getSenderId().toString(), e.getSenderUsername(),
            e.getContent(), e.getSentAt()
        );
    }
}


// ─── REST CONTROLLERS ────────────────────────────────────────────────────────
