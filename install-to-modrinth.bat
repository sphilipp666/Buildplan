@echo off
setlocal enabledelayedexpansion

echo ======================================================================
echo   Schematic Build Planner - Modrinth Instanz Installer
echo ======================================================================
echo.

set ROOT_DIR=%~dp0
set MOD_DIR=%ROOT_DIR%fabric-mod
set MODRINTH_PROFILES=%APPDATA%\ModrinthApp\profiles

echo [1/4] Suche aktive Modrinth "Vanilla+" Profile...
set "TARGET_PROFILE="

if exist "%MODRINTH_PROFILES%\Vanilla+ Game (1)" (
    set "TARGET_PROFILE=%MODRINTH_PROFILES%\Vanilla+ Game (1)"
) else if exist "%MODRINTH_PROFILES%\Vanilla+ Game 2.5" (
    set "TARGET_PROFILE=%MODRINTH_PROFILES%\Vanilla+ Game 2.5"
) else if exist "%MODRINTH_PROFILES%\Vanilla+ Game" (
    set "TARGET_PROFILE=%MODRINTH_PROFILES%\Vanilla+ Game"
) else if exist "%MODRINTH_PROFILES%\Vanilla +" (
    set "TARGET_PROFILE=%MODRINTH_PROFILES%\Vanilla +"
)

if not defined TARGET_PROFILE (
    echo [FEHLER] Kein passendes "Vanilla+" Profil in %MODRINTH_PROFILES% gefunden!
    pause
    exit /b 1
)

echo       Gefunden: !TARGET_PROFILE!

echo [2/4] Ueberpruefe Java 25 Umgebung...
if exist "%LOCALAPPDATA%\jdk-25\jdk-25.0.4+7\bin\java.exe" (
    set "JAVA_HOME=%LOCALAPPDATA%\jdk-25\jdk-25.0.4+7"
    set "PATH=!JAVA_HOME!\bin;!PATH!"
)

cd /d "%MOD_DIR%"

echo [3/4] Baue Mod JAR via Gradle...
call gradlew.bat build --console=plain
if %ERRORLEVEL% neq 0 (
    echo [FEHLER] Gradle Build fehlgeschlagen!
    pause
    exit /b %ERRORLEVEL%
)

echo [4/4] Kopiere schemplanner JAR und Test-Projektdaten in Modrinth...
set "MODS_DIR=!TARGET_PROFILE!\mods"
set "CONFIG_DIR=!TARGET_PROFILE!\config\schemplanner\projects"

if not exist "!CONFIG_DIR!" mkdir "!CONFIG_DIR!"

copy /Y "build\libs\schemplanner-1.0.0.jar" "!MODS_DIR!\schemplanner-1.0.0.jar" >NUL
copy /Y "run\config\schemplanner\projects\test.schem" "!CONFIG_DIR!\test.schem" >NUL
copy /Y "run\config\schemplanner\projects\test_project.json" "!CONFIG_DIR!\test_project.json" >NUL

echo.
echo ======================================================================
echo   ERFOLGREICH INSTALLIERT!
echo   Ziel-Instanz: !TARGET_PROFILE!
echo   Mod:          !MODS_DIR!\schemplanner-1.0.0.jar
echo   Test-Daten:   !CONFIG_DIR!\test.schem
echo ======================================================================
echo.
echo Du kannst jetzt einfach Modrinth oeffnen und dein Profil "Vanilla+ Game" starten!
echo.
pause
