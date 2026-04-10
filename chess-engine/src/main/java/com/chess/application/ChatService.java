package com.chess.application;

import com.chess.api.dto.ChatDto;
import com.chess.persistence.entity.ChatMessageEntity;
import com.chess.persistence.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChatService {

    private final ChatMessageRepository chatRepo;

    public ChatService(ChatMessageRepository chatRepo) { this.chatRepo = chatRepo; }

    public List<ChatDto.ChatMessage> getMessages(UUID gameId) {
        return chatRepo.findByGameIdOrderBySentAtAsc(gameId)
            .stream().map(this::toDto).toList();
    }

    @Transactional
    public ChatDto.ChatMessage send(UUID gameId, UUID senderId,
                                    String senderUsername, String content) {
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
