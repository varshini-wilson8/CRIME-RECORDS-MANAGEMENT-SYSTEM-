@echo off
echo ========================================================
echo   Starting CRMS 2.0 - Criminal Case Management System   
echo ========================================================

if not exist out mkdir out

echo Compiling Java source files...
javac -encoding UTF-8 -cp "lib\sqlite-jdbc.jar;." -d out *.java model\*.java repository\*.java service\*.java util\*.java menu\*.java
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo Launching SecureWebServer on http://localhost:8081 ...
echo Default Login:
echo   Username: admin
echo   Password: ChangeMe!2026
echo.
java -cp "out;lib\sqlite-jdbc.jar" SecureWebServer
