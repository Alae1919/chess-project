#!/usr/bin/env sh
# Runs the REXCHESS engine as a UCI engine. Needs Java 17+ (set JAVA_HOME to it); build first: mvn -f chess-engine/pom.xml -DskipTests compile
DIR="$(cd "$(dirname "$0")" && pwd)"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
exec "$JAVA" -Xss4m -cp "$DIR/../../chess-engine/target/classes" com.chess.engine.uci.UciMain "$@"
