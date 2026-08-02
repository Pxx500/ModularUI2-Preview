@echo off
setlocal

set "TOOL_ROOT=%~dp0."
call :find_jdk
if errorlevel 1 exit /b %ERRORLEVEL%

set "PREVIEW_LAUNCHER=%TOOL_ROOT%\bin\modularui2-preview.bat"
if not exist "%PREVIEW_LAUNCHER%" (
    set "PREVIEW_LAUNCHER=%TOOL_ROOT%\build\install\modularui2-preview\bin\modularui2-preview.bat"
    if not exist "%PREVIEW_LAUNCHER%" (
        call "%TOOL_ROOT%\gradlew.bat" -p "%TOOL_ROOT%" installDist
        if errorlevel 1 exit /b %ERRORLEVEL%
    )
)

call "%PREVIEW_LAUNCHER%" %*
exit /b %ERRORLEVEL%

:find_jdk
if defined JAVA_HOME (
    set "PREVIEW_JAVA=%JAVA_HOME%\bin\java.exe"
    set "PREVIEW_JAVAC=%JAVA_HOME%\bin\javac.exe"
) else (
    where java.exe >nul 2>nul || (
        echo ModularUI2 Preview requires JDK 21. Set JAVA_HOME or add java and javac to PATH. 1>&2
        exit /b 2
    )
    where javac.exe >nul 2>nul || (
        echo ModularUI2 Preview requires a JDK with javac, not a JRE. 1>&2
        exit /b 2
    )
    set "PREVIEW_JAVA=java.exe"
    set "PREVIEW_JAVAC=javac.exe"
)

if not exist "%PREVIEW_JAVA%" if not "%PREVIEW_JAVA%"=="java.exe" (
    echo JAVA_HOME does not contain bin\java.exe: %JAVA_HOME% 1>&2
    exit /b 2
)
if not exist "%PREVIEW_JAVAC%" if not "%PREVIEW_JAVAC%"=="javac.exe" (
    echo JAVA_HOME does not contain bin\javac.exe: %JAVA_HOME% 1>&2
    exit /b 2
)

set "PREVIEW_JAVA_VERSION="
for /f "tokens=1,2,3" %%A in ('"%PREVIEW_JAVA%" -version 2^>^&1') do if not defined PREVIEW_JAVA_VERSION (
    if /i "%%B"=="version" (set "PREVIEW_JAVA_VERSION=%%~C") else set "PREVIEW_JAVA_VERSION=%%~B"
)
for /f "tokens=1 delims=." %%V in ("%PREVIEW_JAVA_VERSION%") do set "PREVIEW_JAVA_MAJOR=%%V"
set "PREVIEW_JAVA_MAJOR_NUMBER=0"
set /a PREVIEW_JAVA_MAJOR_NUMBER=%PREVIEW_JAVA_MAJOR% >nul 2>nul
if %PREVIEW_JAVA_MAJOR_NUMBER% LSS 21 (
    echo ModularUI2 Preview requires JDK 21 or newer; found %PREVIEW_JAVA_VERSION%. 1>&2
    exit /b 2
)
exit /b 0
