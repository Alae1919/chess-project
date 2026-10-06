package com.chess.infrastructure.api.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler — what a client is told")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("our own explanations reach the client")
    void deliberateMessagesAreKept() {
        ProblemDetail pd = handler.handleIllegalState(new IllegalStateException("There is no draw offer to answer."));

        assertEquals("There is no draw offer to answer.", pd.getDetail());
        assertEquals(409, pd.getStatus());
    }

    @Test
    @DisplayName("an enum lookup that fails doesn't name the Java class")
    void enumClassNamesAreHidden() {
        ProblemDetail pd = handler.handleIllegalArgument(new IllegalArgumentException(
            "No enum constant com.chess.persistence.entity.DatabaseEnums.TimeControlKind.weird"));

        assertFalse(pd.getDetail().contains("com.chess"), pd.getDetail());
        assertEquals(400, pd.getStatus());
    }

    @Test
    @DisplayName("a message that mentions internals is replaced, for illegal-state errors too")
    void internalsAreHiddenForIllegalState() {
        ProblemDetail pd = handler.handleIllegalState(new IllegalStateException(
            "failed in com.chess.application.GameApplicationService.restore(GameApplicationService.java:301)"));

        assertFalse(pd.getDetail().contains("com.chess"), pd.getDetail());
    }

    @Test
    @DisplayName("an exception with no message still gets a detail")
    void missingMessageGetsADefault() {
        ProblemDetail pd = handler.handleIllegalArgument(new IllegalArgumentException());

        assertNotNull(pd.getDetail());
        assertFalse(pd.getDetail().isBlank());
    }
}
