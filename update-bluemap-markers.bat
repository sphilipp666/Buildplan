@echo off
setlocal
echo ======================================================================
echo   BlueMap 3D-Marker Generator (Map: kekwelt)
echo ======================================================================
echo.

cd /d "%~dp0"

node scripts/generate_bluemap_markers.js kekwelt

echo.
echo ======================================================================
echo Fertig! Die Dateien liegen in 'build/bluemap/':
echo   1. kekwelt_markers.conf (fuer config/bluemap/maps/kekwelt.conf)
echo   2. markers.json         (fuer web/maps/kekwelt/live/markers.json)
echo ======================================================================
pause
