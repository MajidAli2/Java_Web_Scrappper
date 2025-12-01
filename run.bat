@echo off
setlocal enabledelayedexpansion

REM Web Scraper - Run with optimized JVM settings
REM This sets lower memory requirements to prevent out-of-memory errors

cd /d "%~dp0"

echo ====================================================
echo   Web Scraper - Starting with optimized memory
echo ====================================================
echo.

REM Set JVM options for reduced memory usage
set JAVA_OPTS=-Xmx512m -Xms256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200

echo Compiling Java files...
javac -encoding UTF-8 -cp "lib/*" src/WebScraperApp.java
if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed!
    echo Please check your Java installation and library files.
    echo.
    pause
    exit /b 1
)

echo.
echo Running Web Scraper...
echo.

java %JAVA_OPTS% -cp "lib/*;src" WebScraperApp

if errorlevel 1 (
    echo.
    echo ERROR: Application failed to run!
    echo Error code: %errorlevel%
    echo.
)

pause
