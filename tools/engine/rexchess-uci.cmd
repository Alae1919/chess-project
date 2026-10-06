@echo off
rem Runs the REXCHESS engine as a UCI engine. Needs Java 17+ (set JAVA_HOME to it); build first: mvn -f chess-engine/pom.xml -DskipTests compile
setlocal
set "JAVA=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA%" set "JAVA=java"
"%JAVA%" -Xss4m -cp "%~dp0..\..\chess-engine\target\classes" com.chess.engine.uci.UciMain %*
