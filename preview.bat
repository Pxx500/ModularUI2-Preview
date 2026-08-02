@echo off
setlocal

if "%~1"=="" (
    echo Usage: preview.bat project-directory [fully.qualified.PreviewClass] [output-directory]
    echo        preview.bat project-directory --actions actions-file
    echo        preview.bat project-directory --interactive
    exit /b 2
)

set "TOOL_ROOT=%~dp0."
set "PREVIEW_PROJECT=%~f1"
set "PREVIEW_CLASS=%~2"
set "PREVIEW_DIST=%TOOL_ROOT%\build\install\modularui2-preview"
set "PREVIEW_JAVA_FILE=%PREVIEW_DIST%\bin\java-executable.txt"

if not exist "%PREVIEW_JAVA_FILE%" (
    call "%TOOL_ROOT%\gradlew.bat" -p "%TOOL_ROOT%" installDist
    if errorlevel 1 exit /b %ERRORLEVEL%
)

set /p PREVIEW_JAVA=<"%PREVIEW_JAVA_FILE%"

if "%~2"=="" (
    "%PREVIEW_JAVA%" -Djoml.nounsafe=true -classpath "%PREVIEW_DIST%\lib\*" dev.modularui.preview.UiPreviewMain "%PREVIEW_PROJECT%"
) else if "%~3"=="" (
    "%PREVIEW_JAVA%" -Djoml.nounsafe=true -classpath "%PREVIEW_DIST%\lib\*" dev.modularui.preview.UiPreviewMain "%PREVIEW_PROJECT%" "%PREVIEW_CLASS%"
) else if "%~4"=="" (
    "%PREVIEW_JAVA%" -Djoml.nounsafe=true -classpath "%PREVIEW_DIST%\lib\*" dev.modularui.preview.UiPreviewMain "%PREVIEW_PROJECT%" "%PREVIEW_CLASS%" "%~3"
) else (
    "%PREVIEW_JAVA%" -Djoml.nounsafe=true -classpath "%PREVIEW_DIST%\lib\*" dev.modularui.preview.UiPreviewMain "%PREVIEW_PROJECT%" "%PREVIEW_CLASS%" "%~3" "%~4"
)

exit /b %ERRORLEVEL%
