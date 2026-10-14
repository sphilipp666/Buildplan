#!/usr/bin/env bash
set -e

echo "======================================================================"
echo "  Minecraft Fabric 26.2 - Bauplaner Test-Umgebung & Singleplayer-Start"
echo "======================================================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="$SCRIPT_DIR/fabric-mod"
RUN_CONFIG_DIR="$MOD_DIR/run/config/schemplanner/projects"
SAMPLE_DIR="$SCRIPT_DIR/sample-projects"

echo "[1/4] Ueberpruefe Testdaten-Verzeichnis..."
mkdir -p "$RUN_CONFIG_DIR"

echo "[2/4] Stelle sicher, dass Test-Schematic vorhanden ist..."
if [ ! -f "$RUN_CONFIG_DIR/test.schem" ]; then
    if [ -f "$SAMPLE_DIR/test.schem" ]; then
        cp "$SAMPLE_DIR/test.schem" "$RUN_CONFIG_DIR/test.schem"
        echo "      test.schem erfolgreich kopiert."
    fi
fi

if [ ! -f "$RUN_CONFIG_DIR/test_project.json" ]; then
    if [ -f "$SAMPLE_DIR/test_project.json" ]; then
        cp "$SAMPLE_DIR/test_project.json" "$RUN_CONFIG_DIR/test_project.json"
        echo "      test_project.json erfolgreich kopiert."
    fi
fi

echo "[3/4] Ueberpruefe Java-Umgebung (Java 25 erforderlich)..."
if ! command -v java &>/dev/null && [ -z "$JAVA_HOME" ]; then
    echo "ERROR: Java 25 wurde nicht gefunden. Bitte installiere Java 25 und setze JAVA_HOME."
    exit 1
fi

cd "$MOD_DIR"
chmod +x gradlew

echo "[4/4] Starte Build und Test-Client (runClient)..."
./gradlew build

echo ""
echo "======================================================================"
echo "  Build erfolgreich! Starte jetzt Minecraft Client (runClient)..."
echo "  In-Game Anleitung:"
echo "  1. Neue Singleplayer-Welt erstellen (Kreativ- oder Ueberlebensmodus)"
echo "  2. Befehl: /tp 0 70 0"
echo "  3. Ghost-Block Wuerfel (Stein + Eichenholz) sichtbar bei Y=70"
echo "  4. Tasten: Bild-Auf/Bild-Ab fuer Layer, M fuer Materialliste"
echo "======================================================================"
echo ""

./gradlew runClient
