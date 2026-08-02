@echo off
setlocal

set "TOOL_ROOT=%~dp0."
set "PREVIEW_LAUNCHER=%TOOL_ROOT%\build\install\modularui2-preview\bin\modularui2-preview.bat"

if not exist "%PREVIEW_LAUNCHER%" (
    call "%TOOL_ROOT%\gradlew.bat" -p "%TOOL_ROOT%" installDist
    if errorlevel 1 exit /b %ERRORLEVEL%
)

call "%PREVIEW_LAUNCHER%" %*
exit /b %ERRORLEVEL%
