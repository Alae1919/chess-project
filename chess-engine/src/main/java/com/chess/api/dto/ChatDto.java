package com.chess.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ChatDto {

    public record ChatMessage(
        String  id,
        String  gameId,
        String  senderId,
        String  senderUsername,
        String  content,
        Instant sentAt
    ) {}

    public record SendMessageRequest(
        @NotBlank @Size(max = 500) String content
    ) {}
}
