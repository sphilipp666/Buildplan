#!/usr/bin/env bash
set -e

echo "=== Schematic Build Planner - Modrinth Installer ==="

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOD_DIR="${ROOT_DIR}/fabric-mod"
MODRINTH_PROFILES="${APPDATA}/ModrinthApp/profiles"

TARGET_PROFILE=""
for candidate in "Vanilla+ Game (1)" "Vanilla+ Game 2.5" "Vanilla+ Game" "Vanilla +"; do
    if [ -d "${MODRINTH_PROFILES}/${candidate}" ]; then
        TARGET_PROFILE="${MODRINTH_PROFILES}/${candidate}"
        break
    fi
done

if [ -z "${TARGET_PROFILE}" ]; then
    echo "Fehler: Kein passendes Profil in ${MODRINTH_PROFILES} gefunden."
    exit 1
fi

echo "Ziel-Profil: ${TARGET_PROFILE}"

cd "${MOD_DIR}"
./gradlew build --console=plain

mkdir -p "${TARGET_PROFILE}/config/schemplanner/projects"
cp "build/libs/schemplanner-1.0.0.jar" "${TARGET_PROFILE}/mods/"
cp "run/config/schemplanner/projects/test.schem" "${TARGET_PROFILE}/config/schemplanner/projects/"
cp "run/config/schemplanner/projects/test_project.json" "${TARGET_PROFILE}/config/schemplanner/projects/"

echo "Erfolgreich nach ${TARGET_PROFILE} installiert!"
