package com.chess.engine.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the CSV fixtures python-chess produced (see training/engine_fixtures.py). */
final class CoreFixtures {

    private CoreFixtures() {}

    /** Lines of "fen,value" as {fen, value} pairs. */
    static List<String[]> read(String name) {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = CoreFixtures.class.getResourceAsStream("/engine/" + name)) {
            if (in == null) throw new IllegalStateException("missing test resource /engine/" + name);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank()) continue;
                int comma = line.lastIndexOf(',');
                rows.add(new String[]{line.substring(0, comma), line.substring(comma + 1)});
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return rows;
    }
}
