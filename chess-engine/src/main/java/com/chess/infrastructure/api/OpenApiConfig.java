package com.chess.infrastructure.api;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chessEngineOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Chess Engine API")
                .version("1.0")
                .description("""
                    REST API for the chess engine.

                    Typical game loop:
                      1. POST /api/games          → get gameId
                      2. GET  /api/games/{id}     → see board state
                      3. POST /api/games/{id}/moves        → human move
                      4. POST /api/games/{id}/ai-move      → AI responds
                      5. Repeat 2-4 until status ≠ ONGOING
                      6. DELETE /api/games/{id}   → clean up
                    """)
                .contact(new Contact()
                    .name("Chess Engine")
                    .url("https://github.com/your-repo/chess-engine")));
    }
}

