package com.chess.api.controller;

import com.chess.api.dto.InvitationDto;
import com.chess.application.InvitationService;
import com.chess.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Invitations", description = "Friend game challenge invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final UserService       userService;

    public InvitationController(InvitationService invitationService, UserService userService) {
        this.invitationService = invitationService;
        this.userService       = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a game invitation to a friend")
    public InvitationDto.InvitationResponse sendInvitation(
            @Valid @RequestBody InvitationDto.SendInvitationRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return invitationService.sendInvitation(userId, req);
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending received invitations")
    public List<InvitationDto.InvitationResponse> getPendingInvitations(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return invitationService.getPendingInvitations(userId);
    }

    @PostMapping("/{invitationId}/respond")
    @Operation(summary = "Accept or decline a received invitation")
    public Object respondToInvitation(
            @PathVariable UUID invitationId,
            @Valid @RequestBody InvitationDto.RespondRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return invitationService.respondToInvitation(userId, invitationId, req.response());
    }

    @DeleteMapping("/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancel a sent invitation (inviter only)")
    public void cancelInvitation(
            @PathVariable UUID invitationId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        invitationService.cancelInvitation(userId, invitationId);
    }
}
