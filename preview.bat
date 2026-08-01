@echo off
setlocal

if "%~1"=="" (
    echo Usage: preview.bat fully.qualified.PreviewClass [output-directory]
    exit /b 2
)

set "TOOL_ROOT=%~dp0."
set "PREVIEW_CLASS=%~1"

if "%~2"=="" (
    call "%TOOL_ROOT%\gradlew.bat" -p "%TOOL_ROOT%" preview "-PpreviewClass=%PREVIEW_CLASS%"
) else (
    call "%TOOL_ROOT%\gradlew.bat" -p "%TOOL_ROOT%" preview "-PpreviewClass=%PREVIEW_CLASS%" "-PpreviewOutput=%~2"
)

exit /b %ERRORLEVEL%
