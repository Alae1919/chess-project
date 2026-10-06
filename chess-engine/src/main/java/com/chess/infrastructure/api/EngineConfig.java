package com.chess.infrastructure.api;

import com.chess.engine.core.eval.Evaluators;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Applies the engine settings at startup: which evaluation the AI and the analysis use.
 *
 * <pre>
 * app.engine.eval        nnue (default) | classical
 * app.engine.nnue-file   a network file to use instead of the one bundled in the jar
 * </pre>
 */
@Configuration
public class EngineConfig {

    private static final Logger log = LoggerFactory.getLogger(EngineConfig.class);

    private final String eval;
    private final String nnueFile;

    public EngineConfig(@Value("${app.engine.eval:nnue}") String eval,
                        @Value("${app.engine.nnue-file:}") String nnueFile) {
        this.eval = eval;
        this.nnueFile = nnueFile;
    }

    @PostConstruct
    void apply() {
        Evaluators.Mode mode;
        try {
            mode = Evaluators.Mode.valueOf(eval.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("app.engine.eval '{}' is not nnue or classical; using nnue", eval);
            mode = Evaluators.Mode.NNUE;
        }
        String described = Evaluators.configure(mode, nnueFile.isBlank() ? null : Path.of(nnueFile.trim()));
        if (described.contains(":")) log.warn("Engine: {}", described);
        else log.info("Engine: {}", described);
    }
}
