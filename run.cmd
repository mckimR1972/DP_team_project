@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
if errorlevel 1 (
    pause
    exit /b 1
)
java -jar build\HolubLife.jar
if errorlevel 1 pause
