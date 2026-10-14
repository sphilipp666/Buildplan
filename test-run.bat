@echo off
setlocal enabledelayedexpansion

echo ======================================================================
echo   Minecraft Fabric 26.2 - Bauplaner Test-Umgebung & Singleplayer-Start
echo ======================================================================
echo.

set ROOT_DIR=%~dp0
set MOD_DIR=%ROOT_DIR%fabric-mod
set RUN_CONFIG_DIR=%MOD_DIR%\run\config\schemplanner\projects
set SAMPLE_DIR=%ROOT_DIR%sample-projects

echo [1/4] Ueberpruefe Testdaten-Verzeichnis...
if not exist "%RUN_CONFIG_DIR%" (
    echo       Erstelle Verzeichnis: %RUN_CONFIG_DIR%
    mkdir "%RUN_CONFIG_DIR%"
)

echo [2/4] Stelle sicher, dass Test-Schematic vorhanden ist...
if not exist "%RUN_CONFIG_DIR%\test.schem" (
    if exist "%SAMPLE_DIR%\test.schem" (
        copy /Y "%SAMPLE_DIR%\test.schem" "%RUN_CONFIG_DIR%\test.schem" >NUL
        echo       test.schem erfolgreich kopiert.
    ) else (
        echo       [WARNUNG] sample-projects\test.schem nicht gefunden!
    )
)

if not exist "%RUN_CONFIG_DIR%\test_project.json" (
    if exist "%SAMPLE_DIR%\test_project.json" (
        copy /Y "%SAMPLE_DIR%\test_project.json" "%RUN_CONFIG_DIR%\test_project.json" >NUL
        echo       test_project.json erfolgreich kopiert.
    ) else (
        echo       [WARNUNG] sample-projects\test_project.json nicht gefunden!
    )
)

echo [3/4] Ueberpruefe Java-Umgebung (Java 25 erforderlich)...
where java >NUL 2>&1
if %ERRORLEVEL% neq 0 (
    if not defined JAVA_HOME (
        echo.
        echo [FEHLER] Weder 'java' noch JAVA_HOME wurden gefunden!
        echo Minecraft 26.2 benoetigt Java 25 (z. B. Eclipse Adoptium / Temurin 25 oder Oracle JDK 25).
        echo Bitte installiere Java 25 und setze JAVA_HOME.
        echo.
        pause
        exit /b 1
    )
)

cd /d "%MOD_DIR%"

echo [4/4] Starte Build und Test-Client (runClient)...
echo       Fuehre './gradlew build' aus...
call gradlew.bat build
if %ERRORLEVEL% neq 0 (
    echo.
    echo [FEHLER] Build fehlgeschlagen. Pruefe die Fehlermeldungen oben.
    echo.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ======================================================================
echo   Build erfolgreich! Starte jetzt Minecraft Client (runClient)...
echo   In-Game Anleitung:
echo   1. Neue Singleplayer-Welt erstellen (Kreativ- oder Ueberlebensmodus)
echo   2. Befehl: /tp 0 70 0
echo   3. Ghost-Block Wuerfel (Stein + Eichenholz) sichtbar bei Y=70
echo   4. Tasten: Bild-Auf/Bild-Ab fuer Layer, M fuer Materialliste
echo ======================================================================
echo.

call gradlew.bat runClient

if %ERRORLEVEL% neq 0 (
    echo.
    echo [INFO] Client beendet mit Code %ERRORLEVEL%.
    pause
)
