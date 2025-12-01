# Web Scraper - PowerShell launcher with optimized JVM settings

$ErrorActionPreference = "Stop"

Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host "  Web Scraper - Starting with optimized memory" -ForegroundColor Cyan
Write-Host "=====================================================" -ForegroundColor Cyan
Write-Host ""

# Set working directory
Push-Location $PSScriptRoot

# JVM options for reduced memory usage
$javaOpts = @(
    "-Xmx512m",
    "-Xms256m",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=200",
    "-Dfile.encoding=UTF-8"
)

Write-Host "Compiling Java files..." -ForegroundColor Yellow

try {
    & javac -encoding UTF-8 -cp "lib/*" src/WebScraperApp.java
    Write-Host "Compilation successful!" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Compilation failed!" -ForegroundColor Red
    Write-Host "Please check your Java installation and library files." -ForegroundColor Red
    Write-Host $_ -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Running Web Scraper..." -ForegroundColor Yellow
Write-Host ""

try {
    & java $javaOpts -cp "lib/*;src" WebScraperApp
} catch {
    Write-Host "ERROR: Application failed!" -ForegroundColor Red
    Write-Host $_ -ForegroundColor Red
    exit 1
}

Pop-Location
