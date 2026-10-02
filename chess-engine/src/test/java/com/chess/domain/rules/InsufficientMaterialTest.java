package com.chess.domain.rules;

import com.chess.domain.board.FenParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("InsufficientMaterial — positions that can never end in mate")
class InsufficientMaterialTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
        bare kings                            | 4k3/8/8/8/8/8/8/4K3 w - - 0 1         | true
        king and knight vs king               | 4k3/8/8/8/8/8/8/3NK3 w - - 0 1        | true
        king and bishop vs king               | 4k3/8/8/8/8/8/8/3BK3 w - - 0 1        | true
        bishops on the same colour            | 4kb2/8/8/8/8/8/8/2B1K3 w - - 0 1       | true
        bishops on opposite colours           | 3bk3/8/8/8/8/8/8/3BK3 w - - 0 1       | false
        two knights vs king                   | 4k3/8/8/8/8/8/8/2NNK3 w - - 0 1       | false
        knight vs knight                      | 4k3/8/8/8/8/8/8/2N1K1n1 w - - 0 1     | false
        bishop and knight vs king             | 4k3/8/8/8/8/8/8/2NBK3 w - - 0 1       | false
        a pawn is enough                      | 4k3/8/8/8/8/8/P7/4K3 w - - 0 1        | false
        a rook is enough                      | 4k3/8/8/8/8/8/8/R3K3 w - - 0 1        | false
        a queen is enough                     | 4k3/8/8/8/8/8/8/Q3K3 w - - 0 1        | false
        starting position                     | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | false
        """)
    void isDead(String name, String fen, boolean expected) {
        assertEquals(expected, InsufficientMaterial.isDead(FenParser.parse(fen)));
    }
}
