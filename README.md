# Minecraft Bauplaner (Visual Build Planner)

Ein ganzheitliches System zur visuellen Bauplanung für private Minecraft Fabric Server (**Minecraft 26.2 "Chaos Cubed"**).

Dieses Projekt besteht aus perfekt aufeinander abgestimmten Komponenten:
1. **Web-Planer (Static Web-App für GitHub Pages):** Interaktiver 3D-Voxel-Planer im Browser zum Platzieren, Rotieren und Exportieren von Bauplänen (`.schem` + `.json`).
2. **Fabric Client-Mod (Ghost-Rendering, Kopier-Funktion & To-Do-Liste):** Client-Mod für Fabric 26.2, die Baupläne als echte 3D-Modelle direkt in die Minecraft-Welt projiziert, Bereiche in der Welt als Sponge-Schematics speichert und Materiallisten mit dem Spieler-Inventar abgleicht.
3. **GitHub-gestützte Synchronisation:** Automatischer Download und Abgleich von geteilten Team-Bauplänen direkt aus dem GitHub-Repository (`shared_projects/`) per Ingame-Befehl `/schem sync` oder GUI-Button.

---

## Repository-Struktur

```text
Buildplan/
├── shared_projects/                     # Geteilte Team-Baupläne (GitHub-Sync Quelle)
│   ├── index.json                       # Manifest aller geteilten Projekte
│   ├── demo_turm.json                   # Projekt-Konfiguration (Origin, Rotation)
│   └── demo_turm.schem                  # Sponge-Schematic Modelldatei
├── web/                                 # Web-Planer (GitHub Pages)
│   ├── index.html                       # Haupt-UI mit 3D Viewport & Sidebar
│   ├── style.css                        # Modernes Minecraft-Dark-Theme
│   ├── app.js                           # Three.js Initialisierung, TransformControls & Export
│   ├── nbt.js                           # Browser NBT & Sponge-Schematic Parser
│   ├── palette.js                       # Minecraft Block-Farbmapping
│   └── sample.js                        # Prozedurale Beispiel-Baupläne
├── fabric-mod/                          # Fabric Client-Mod (Java 25, Minecraft 26.2)
│   ├── build.gradle                     # Fabric Loom Build-Konfiguration
│   ├── gradle.properties                # Minecraft 26.2, Fabric API
│   └── src/main/java/net/schemplanner/
│       ├── SchemPlannerClient.java      # Client Mod Initializer, Tick-Handler & Commands
│       ├── config/
│       │   ├── ProjectConfig.java       # JSON Schema POJO
│       │   └── ProjectManager.java      # Verwaltet Baupläne in projects/ und schematics/
│       ├── sync/
│       │   └── GitHubProjectSync.java   # Asynchroner HTTP-Download & Abgleich von GitHub
│       ├── selection/
│       │   └── AreaSelectionManager.java# 3D-Bereichsauswahl in der Welt (Pos1 & Pos2)
│       ├── schematic/
│       │   ├── SpongeSchematic.java     # Multi-Format Parser (.schem, .litematic, .nbt)
│       │   ├── SchematicWriter.java     # Exportiert Ingame-Bereiche als Sponge v2 .schem
│       │   ├── PlannedBlock.java        # Record für Welt-Koordinaten & BlockState
│       │   └── MaterialRequirement.java # Soll-/Ist-Abgleich mit Inventar
│       ├── render/
│       │   └── GhostBlockRenderer.java  # Hologramm-Renderer, 3D-Texturen & Gizmo-Boxen
│       ├── gui/
│       │   ├── MaterialListScreen.java  # 3-Tab Vanilla GUI Screen (Baupläne, Material, Kopieren)
│       │   ├── MaterialListWidget.java  # Scrollbare Material-Eintragsliste
│       │   └── SchematicListWidget.java # Projekt- & Schematics-Verwaltung
│       └── input/
│           └── KeyBindings.java         # Registrierte Tastenbelegungen
└── .github/workflows/
    └── pages.yml                        # Automatisches GitHub Pages Deployment
```

---

## 🚀 Team-Workflow & GitHub-Synchronisation

### 1. Wie trage ich meine GitHub-Repo-URL im Mod ein?
Der Mod legt beim ersten Start automatisch die Konfigurationsdatei an:
```text
.minecraft/config/schemplanner/sync_config.json
```
Inhalt:
```json
{
  "baseUrl": "https://raw.githubusercontent.com/sphilipp666/Buildplan/main/shared_projects/"
}
```
- Ersetze einfach `<USER>/<REPO>` durch dein eigenes GitHub-Repository (z. B. `https://raw.githubusercontent.com/DeinName/DeinRepo/main/shared_projects/`).
- Wichtig: Die URL muss auf den Ordner mit der `index.json` verweisen und mit einem Schrägstrich `/` enden.

---

### 2. Wie lade ich als Spieler neue Bauten via Web-Planer hoch?
1. **Web-Planer öffnen:** Rufe die GitHub-Pages-Webseite deines Repositories auf (oder starte lokal `start-web.bat`).
2. **Schematic ablegen:** Ziehe deine `.schem`-Datei (z. B. im Spiel per `V`/`B` kopiert) per Drag & Drop in den Browser.
3. **Positionieren & Drehen:**
   - Nutze das **Transform-Gizmo** oder die Koordinatenfelder (X, Y, Z), um das Gebäude millimetergenau auszurichten (Snap: 1 Block).
   - Wähle die gewünschte Drehung (0°, 90°, 180°, 270°).
4. **Projekt-JSON exportieren:**
   - Klicke auf **"💾 Projekt-JSON exportieren"**. Der Browser lädt z. B. `rathaus.json` herunter.
5. **Im GitHub-Repository bereitstellen:**
   - Lege `rathaus.json` und `rathaus.schem` in den Ordner `shared_projects/`.
   - Trage den Bauplan in `shared_projects/index.json` ein:
     ```json
     [
       {
         "id": "rathaus",
         "title": "Neues Rathaus",
         "jsonUrl": "rathaus.json",
         "schemUrl": "rathaus.schem"
       }
     ]
     ```
   - Commit & Push auf den `main`-Branch – fertig!

---

### 3. Wie führen die Mitspieler `/schem sync` ingame aus?
Sobald ein neues Gebäude im GitHub-Repository liegt, können alle Mitspieler die neuen Baupläne ohne Spielneustart abrufen:

- **Option A (Chat-Befehl):**
  Gib im Minecraft-Chat einfach ein:
  ```text
  /schem sync
  ```
  *(Alternativer Alias: `/project sync`)*

- **Option B (Über das Menü):**
  1. Drücke **`P`**, um das Bauplan-Menü zu öffnen.
  2. Klicke unten auf den Button **`[GitHub Sync]`**.

**Was passiert beim Sync?**
- Der Mod kontaktiert GitHub asynchron im Hintergrund.
- Neue `.json`- und `.schem`-Dateien werden automatisch in `.minecraft/config/schemplanner/projects/` und `.minecraft/schematics/` geladen.
- Alle Baupläne und Hologramme werden sofort in der Welt aktualisiert!

---

## 👥 Multi-User Team-Kollaboration & Sichtbarkeitsfilter

Wenn ihr zu sechst auf dem Server spielt, wird die Welt schnell voll von Schematics. Deshalb bietet SchemPlanner ein integriertes Rollen-, Zuweisungs- und Filtersystem:

### 1. Zuweisung von Bauplänen (`assignedPlayers`)
- Jeder Bauplan hat einen **Titel**, einen **Bauleiter (`owner`)** und eine Liste an **zuständigen Spielern (`assignedPlayers`)**.
- Im Menü (`P`) kann sich jeder Spieler mit einem Klick auf **`[+Ich]`** bzw. **`[-Ich]`** selbst einem Projekt zuweisen oder entfernen.
- Über Chat-Befehle:
  - `/schem assign <projectId> <spieler>`: Weist einem Spieler das Projekt zu.
  - `/schem unassign <projectId> <spieler>`: Entfernt einen Spieler vom Projekt.
  - `/schem list`: Zeigt alle Baupläne, deren Status und zuständige Teams an.

### 2. Persönlicher Sichtbarkeitsfilter (`[🎯 Nur meine Bauten]`)
- Um nicht von 20 gleichzeitigen Hologrammen überfordert zu werden, gibt es im Menü (`P`) den Filter-Button:
  - **`[Filter: 🎯 Nur meine Bauten]`**: Es werden **nur** Baupläne als Ghost-Blöcke gerendert, bei denen du eingetragen bist (oder unzugewiesene öffentliche Projekte). Auch die Materialliste berechnet dann **nur deinen persönlichen Materialbedarf**!
  - **`[Filter: 🌐 Alle Bauten anzeigen]`**: Zeigt alle freigeschalteten Baupläne der gesamten Stadt.
- Per Befehl:
  - `/schem filter mine` (oder `/schem filter all`)
- Zusätzlich besitzt jeder Bauplan einen individuellen **`[An] / [Aus]`** Schalter und einen Status-Badge (`[Plan]`, `[Bau]`, `[Fertig]`), der durch Anklicken im Menü weitergeschaltet werden kann.

---

## 🗺️ BlueMap Livemap-Integration (Tipps & Ausblick)

Wenn ihr später eine BlueMap Livemap auf eurem Server betreibt, könnt ihr diese nahtlos in die Stadtplanung einbinden:
1. **BlueMap Web-Embed (Iframe):**
   - Ihr könnt die Livemap direkt als Hintergrund im Web-Planer einbetten:
     `<iframe src="http://dein-server.de:8100" style="width:100%; height:100%; border:none;"></iframe>`
2. **BlueMap Markers API (`markers.json`):**
   - BlueMap liest Markierungen aus `plugins/BlueMap/markers.json` (oder Fabric Config).
   - Aus der `index.json` bzw. den Projekt-JSONs (`origin.x`, `origin.z`, `title`) lässt sich mit einem einfachen Python-/Node-Script eine `markers.json` generieren, sodass geplante Gebäude direkt auf der Livemap als farbige Marker oder Bounding-Boxen sichtbar sind!

---

## 🎮 Ingame-Steuerung & Befehle

### Tastenbelegungen
| Taste | Funktion | Beschreibung |
| :--- | :--- | :--- |
| **P** | **Bauplan-Menü** | Öffnet das 3-Tab Menü mit Filter, Zuweisung, Materialliste und Kopier-Tool. |
| **M** | **Materialliste** | Schnelltaste zur Anzeige aller benötigten Materialien & Inventarabgleich. |
| **V** | **Kopieren: Pos 1** | Setzt Auswahlpunkt 1 auf den anvisierten Block. |
| **B** | **Kopieren: Pos 2** | Setzt Auswahlpunkt 2. Eine 3D-Cyan-Box umspannt sofort das Gebäude. |
| **C** | **Crosshair-Snap** | Lässt den aktiven Bauplan beim Platzieren sofort zum anvisierten Block springen. |
| **R** | **Drehen** | Dreht den Bauplan im Justierungsmodus um 90°. |
| **Enter** | **Fixieren** | Schließt die Ingame-Justierung ab und fixiert die Position. |
| **Bild-Auf** | Layer +1 | Schaltet die maximale Bau-Ebene um 1 nach oben. |
| **Bild-Ab** | Layer -1 | Schaltet die maximale Bau-Ebene um 1 nach unten. |
| **Pos1 / Home** | Filter An/Aus | Wechselt zwischen aktiver Bauebene und Gesamtanzeige. |
| **F10** | Reload | Lädt alle lokalen Dateien neu ein. |

### Chat-Befehle (`/schem` oder `/project`)
- `/schem sync` – Lädt fehlende/neue Baupläne von GitHub herunter.
- `/schem filter` – Wechselt zwischen `[🎯 Nur meine Bauten]` und `[🌐 Alle Bauten]`.
- `/schem filter <all|mine>` – Setzt den Sichtbarkeitsfilter direkt.
- `/schem assign <projectId> <spieler>` – Weist einen Spieler einem Bauplan zu.
- `/schem unassign <projectId> <spieler>` – Entfernt einen Spieler von einem Bauplan.
- `/schem list` – Listet alle Baupläne, IDs, Status und Teams im Chat auf.

---

## 🔨 Kompilieren & Installieren

### 1. Kompilieren
```bash
cd fabric-mod
./gradlew build
```
Die erzeugte JAR liegt in `fabric-mod/build/libs/schemplanner-1.0.0.jar`.

### 2. Installation im Modrinth Client
Kopiere `schemplanner-1.0.0.jar` in den `mods/`-Ordner deines Profils:
```text
C:\Users\<Name>\AppData\Roaming\ModrinthApp\profiles\<Profilname>\mods\
```

---

## Lizenz
MIT License – Frei verwendbar für private und öffentliche Minecraft-Server.
