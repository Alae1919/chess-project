package com.chess.api.controller;

import com.chess.api.dto.ChatDto;
import com.chess.application.ChatService;
import com.chess.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/games/{gameId}/chat")
@Tag(name = "Chat", description = "In-game chat messages")
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;

    public ChatController(ChatService chatService, UserService userService) {
        this.chatService = chatService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get all chat messages for a game")
    public List<ChatDto.ChatMessage> getMessages(@PathVariable UUID gameId) {
        return chatService.getMessages(gameId);
    }

    @PostMapping
    @Operation(summary = "Send a chat message")
    public ChatDto.ChatMessage sendMessage(
            @PathVariable UUID gameId,
            @Valid @RequestBody ChatDto.SendMessageRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return chatService.send(gameId, userId, userDetails.getUsername(), req.content());
    }
}
