# Minecraft Bauplaner (Visual Build Planner)

Ein ganzheitliches System zur visuellen Bauplanung für private Minecraft Fabric Server (**Minecraft 26.2 "Chaos Cubed"**).

Dieses Projekt besteht aus zwei perfekt aufeinander abgestimmten Komponenten:
1. **Web-Planer (Static Web-App für GitHub Pages):** Interaktiver 3D-Voxel-Planer im Browser zum Platzieren, Rotieren und Exportieren von Bauplänen (`.schem` + `.json`).
2. **Fabric Client-Mod (Ghost-Rendering & To-Do-Liste):** Client-Mod für Fabric 26.2, die freigeschaltete Baupläne als semitransparente Hologramme direkt in die Minecraft-Welt projiziert und Materiallisten mit dem Spieler-Inventar abgleicht.

---

## Repository-Struktur

```text
Buildplan/
├── web/                                 # Teil 1: Static Web-App (GitHub Pages)
│   ├── index.html                       # Haupt-UI mit 3D Viewport & Sidebar
│   ├── style.css                        # Modernes Minecraft-Dark-Theme
│   ├── app.js                           # Three.js Initialisierung, InstancedMesh & Export
│   ├── nbt.js                           # Standalone Browser NBT & Sponge-Schematic Parser
│   ├── palette.js                       # Minecraft Block-Farbmapping
│   └── sample.js                        # Prozeduraler Beispiel-Bauplan
├── fabric-mod/                          # Teil 2: Fabric Client-Mod (Java 25, Minecraft 26.2)
│   ├── build.gradle                     # Fabric Loom 1.17 Build-Konfiguration
│   ├── gradle.properties                # Minecraft 26.2, Loader 0.19.3, Fabric API 0.152.2+26.2
│   ├── settings.gradle
│   ├── gradle/wrapper/                  # Gradle 9.5.1 Wrapper
│   └── src/main/
│       ├── java/net/schemplanner/
│       │   ├── SchemPlannerClient.java  # Client Mod Initializer & Tick Event
│       │   ├── config/
│       │   │   ├── ProjectConfig.java   # JSON Schema POJO
│       │   │   └── ProjectManager.java  # Lädt .json & .schem aus config/schemplanner/projects/
│       │   ├── schematic/
│       │   │   ├── SpongeSchematic.java # NBT & VarInt Parser (Sponge v1/v2/v3)
│       │   │   ├── PlannedBlock.java    # Record für Welt-Koordinaten & BlockState
│       │   │   └── MaterialRequirement.java # Soll-/Ist-Abgleich mit Inventar
│       │   ├── render/
│       │   │   ├── GhostBlockRenderer.java       # Hologramm-Renderer & Layer-Filter
│       │   │   └── TranslucentVertexConsumer.java # Alpha-Erzwingung für Block-Modelle
│       │   ├── mixin/
│       │   │   └── WorldRendererMixin.java       # Resilienter TAIL-Hook in WorldRenderer.render(...)
│       │   ├── gui/
│       │   │   ├── MaterialListScreen.java       # Vanilla GUI Screen für Material-Bedarf
│       │   │   └── MaterialListWidget.java       # Scrollbare Material-Eintragsliste
│       │   └── input/
│       │       └── KeyBindings.java              # Tastenbelegungen (Page Up, Page Down, M, F10)
│       └── resources/
│           ├── fabric.mod.json
│           ├── schemplanner.mixins.json
│           └── assets/schemplanner/lang/
│               ├── de_de.json           # Deutsche Übersetzung
│               └── en_us.json           # Englische Übersetzung
├── sample-projects/                     # Test-Dateien zum direkten Ausprobieren
│   ├── beispiel_haus.json
│   └── beispiel_haus.schem
└── .github/workflows/
    └── deploy-pages.yml                 # Automatisches GitHub Pages Deployment
```

---

## Teil 1: Web-Planer (GitHub Pages)

### Features
- **Drag-and-Drop & Dateiauswahl:** Unterstützt Sponge-Schematics (`.schem`, Versionen 1, 2 und 3).
- **Client-seitiger NBT & VarInt Parser:** Dekomprimiert GZIP nativ im Browser via `DecompressionStream` (mit Pako-Fallback) und dekodiert Palette sowie Block-Daten ohne Server/Backend.
- **Three.js 3D Voxel Preview:**
  - High-Performance Rendering via `InstancedMesh` mit Minecraft-Farbpalette und Unterstützung für transparente Blöcke (Glas, Wasser).
  - Orientierungs-Raster (Grid) im Maßstab 1 Einheit = 1 Minecraft-Block sowie Himmelsrichtungs-Kompass (Rot = Ost/+X, Grün = Oben/+Y, Blau = Süd/+Z).
  - Volle `OrbitControls`-Kamera (Drehen mit Linksklick, Schwenken mit Rechtsklick, Zoomen mit Mausrad).
  - Transform-Gizmo und numerische Ganzzahl-Eingabefelder für Minecraft-Koordinaten (X, Y, Z).
  - Rotation in 90°-Schritten (0°, 90°, 180°, 270°).
  - Interaktiver Layer-Slider zum schichtweisen Betrachten des Gebäudes von unten nach oben.
- **Material-Übersicht:** Listet alle im Bauplan vorkommenden Blöcke aufgeschlüsselt nach Anzahl und 64er-Stacks auf.
- **Export:** Erzeugt die fertige Projekt-JSON per Mausklick nach vorgegebenem Schema.

### Export-Schema (`<projekt_name>.json`)
```json
{
  "id": "rathaus_nord",
  "schematic": "rathaus_nord.schem",
  "origin": {
    "x": 100,
    "y": 64,
    "z": -250
  },
  "rotation": 0,
  "unlocked": true
}
```

---

## Teil 2: Fabric Client-Mod (Minecraft 26.2)

### Anforderungen & Spezifikationen für 26.2
- **Minecraft:** `26.2` (Chaos Cubed Update)
- **Fabric Loader:** `>= 0.19.3`
- **Fabric API:** `0.152.2+26.2`
- **Java:** `Java 25` (Standard-Laufzeitumgebung für 26.x)
- **Gradle:** `Gradle 9.5.1` & `Fabric Loom 1.17`
- **Keine Obfuskations-Mappings:** Ab 26.1+ liefert Mojang unverschleierten Java-Code aus, weshalb keine Mappings mehr deklariert werden müssen.

### Installation & Verzeichnisstruktur
Die Mod liest alle Baupläne aus dem Minecraft-Konfigurationsordner:
```text
.minecraft/config/schemplanner/projects/
├── rathaus_nord.json
├── rathaus_nord.schem
├── beispiel_haus.json
└── beispiel_haus.schem
```
*Tipp: Der Ordner wird beim ersten Spielstart automatisch angelegt. Zum Testen können die Dateien aus dem Ordner `sample-projects/` direkt dorthin kopiert werden.*

### Steuerung & Keybindings

| Taste | Aktion | Beschreibung |
| :--- | :--- | :--- |
| **Bild-Auf (Page Up)** | Layer +1 | Schaltet die maximale Bau-Ebene um 1 nach oben. |
| **Bild-Ab (Page Down)**| Layer -1 | Schaltet die maximale Bau-Ebene um 1 nach unten. |
| **Pos1 / Home** | Layer-Filter umschalten | Wechselt zwischen Einzel-Ebene und Anzeige aller Ebenen (`currentLayerY = Integer.MAX_VALUE`). |
| **M** | To-Do Materialliste | Öffnet das Vanilla-Screen-UI mit Soll-/Ist-Vergleich des Spieler-Inventars. |
| **F10** | Reload | Lädt alle JSON- und Schematic-Dateien aus dem Config-Ordner neu ein. |

### Ingame Ghost-Rendering (Hologramm)
- Freigeschaltete Baupläne (`"unlocked": true`) werden an den Zielkoordinaten semitransparent gerendert.
- **Intelligenter Bau-Status:**
  - **Luft:** Transparenter Ghost-Block wird angezeigt.
  - **Korrekt gebaut:** Entspricht der gesetzte Block bereits dem Bauplan, wird er ausgeblendet.
  - **Falscher Block:** Befindet sich an der Stelle ein falscher Block, wird eine rote Warn-Box gerendert.
- **Layer-Filter:** Blöcke oberhalb von `currentLayerY` werden ausgeblendet; Blöcke auf der aktuellen aktiven Ebene werden cyan hervorgehoben.

### To-Do Materialliste (Screen UI)
- Tastendruck auf `M` öffnet die Übersicht aller benötigten Baumaterialien.
- Zeigt Soll-Bedarf, aktuellen Inventar-Bestand und noch fehlende Blöcke an.
- Farbliche Hervorhebung (Grün: Fertig im Inventar vorhanden / Rot: Fehlt noch).
- Integriertes Suchfeld zum schnellen Filtern nach Blocknamen sowie Toggle-Button ("Nur Fehlende anzeigen").

### Kompilieren & Bauen der Mod
```bash
cd fabric-mod
./gradlew build
```
Die fertige Mod-JAR befindet sich anschließend in:
`fabric-mod/build/libs/schemplanner-1.0.0.jar`
Diese Datei einfach in den `.minecraft/mods/` Ordner kopieren.

---

## Schnellanleitung & Singleplayer-Test

### 1. Web-Planer lokal starten
Führe im Hauptverzeichnis einfach aus:
- **Windows:** `start-web.bat` (oder `python -m http.server 8080 --directory web`)
- **Linux/macOS:** `./start-web.sh`
- Öffne `http://localhost:8080` im Browser.
- Klicke auf **"🎲 3x3 Test-Würfel"**, um die Testdaten zu visualisieren und mit Klick auf **"Projekt-JSON exportieren"** die passende `test_project.json` zu erhalten.

### 2. Mod im Test-Client starten (1-Klick-Start)
- **Windows:** `test-run.bat`
- **Linux/macOS:** `./test-run.sh`
*Das Skript stellt sicher, dass die Test-Schematic (`test.schem`) und Konfiguration (`test_project.json`) in `fabric-mod/run/config/schemplanner/projects/` liegen, führt den Build durch und startet den Client (`runClient`).*

### 3. Checkliste für den Singleplayer-Test
1. **Neue Welt erstellen:** Singleplayer-Welt erstellen (Kreativ- oder Überlebensmodus).
2. **Teleportieren:** In den Chat eingeben:
   `/tp 0 70 0`
3. **Ghost-Hologramm begutachten:**
   - Bei Koordinate `(0, 70, 0)` erscheint der 3x3 Würfel (Steinboden, Eichenstämme an den Ecken, Eichenholzbretter) als semitransparentes Ghost-Hologramm.
4. **Layer-by-Layer-Steuerung testen:**
   - Drücke **Bild-Auf (Page Up)** und **Bild-Ab (Page Down)**: Die angezeigten Schichten filtern sich stufenweise (Chat-Nachricht bestätigt die aktive Y-Ebene).
   - Drücke **Pos1 / Home**: Schaltet zwischen dem Ebenenfilter und allen Ebenen um.
5. **Echtzeit-Blockvergleich testen:**
   - Setze an einer Ghost-Position einen korrekten Block (z. B. Eichenholzbretter): Der Ghost-Block verschwindet an dieser Stelle.
   - Setze an einer Ghost-Position einen falschen Block (z. B. Schmutz/Dirt): Eine rote Warn-Kontur markiert den Fehler.
6. **Materialliste & Inventarabgleich testen:**
   - Drücke **M**: Die To-Do-Liste öffnet sich (benötigt: 13 Oak Planks, 9 Stone, 4 Oak Log).
   - Nimm z. B. 13 Eichenholzbretter ins Inventar und öffne erneut **M**: Der Eintrag wechselt sofort auf Grün (`✔ Vorhanden`).

## Lizenz
MIT License - Frei verwendbar für private und öffentliche Minecraft-Server.
