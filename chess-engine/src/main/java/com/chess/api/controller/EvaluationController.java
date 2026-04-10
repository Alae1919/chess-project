package com.chess.api.controller;

import com.chess.api.dto.EvaluationDto;
import com.chess.domain.board.FenParser;
import com.chess.engine.eval.Evaluator;
import com.chess.engine.search.AlphaBetaSearch;
import com.chess.domain.model.Color;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/evaluate")
@Tag(name = "Evaluation", description = "Position evaluation using the chess engine")
public class EvaluationController {

    private final AlphaBetaSearch search   = new AlphaBetaSearch();
    private final Evaluator       evaluator = new Evaluator();

    @PostMapping
    @Operation(summary = "Evaluate a position from a FEN string",
               description = "Returns score in centipawns (positive = White advantage), "
                           + "best move in UCI format, and search depth.")
    public EvaluationDto.PositionEvaluation evaluate(
            @Valid @RequestBody EvaluationDto.EvaluateRequest req) {
        var board = FenParser.parse(req.fen());
        var best  = search.findBestMove(board, req.depth());
        int score = evaluator.evaluate(board, board.activeColor());

        return new EvaluationDto.PositionEvaluation(
            score,
            req.depth(),
            best.map(Object::toString).orElse(null),
            null   // opening name — extend with ECO table later
        );
    }
}
