@echo off
echo ========================================================
echo   Running CRMS 2.0 Full Regression Test Suite          
echo ========================================================

echo Compiling Java source files...
javac -encoding UTF-8 -cp "lib\sqlite-jdbc.jar;." -d out *.java model\*.java repository\*.java service\*.java util\*.java menu\*.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo Running ApiTester across all 14 acceptance audit phases...
java -cp "out;lib\sqlite-jdbc.jar" ApiTester
