package com.chess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point.
 *
 * The @SpringBootApplication annotation enables:
 *   • @Configuration   — Spring context
 *   • @EnableAutoConfiguration — auto-wire Spring MVC, Jackson, Validation
 *   • @ComponentScan   — scans com.chess.** for @Component, @Service, etc.
 */
@SpringBootApplication
public class ChessApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChessApplication.class, args);
    }
}
