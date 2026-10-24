/**
 * Generates BlueMap 3D Extrude Boxes & POI Markers for all projects in shared_projects/
 * Supports BlueMap 5.x HOCON configuration (for config/bluemap/maps/<mapId>.conf)
 * and JSON format (for web/maps/<mapId>/live/markers.json).
 */
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const mapId = process.argv[2] || 'kekwelt';
const repoRoot = path.resolve(__dirname, '..');
const sharedProjectsDir = path.join(repoRoot, 'shared_projects');
const indexPath = path.join(sharedProjectsDir, 'index.json');

if (!fs.existsSync(indexPath)) {
    console.error('shared_projects/index.json nicht gefunden unter:', indexPath);
    process.exit(1);
}

const manifest = JSON.parse(fs.readFileSync(indexPath, 'utf8'));
console.log(`[BlueMap Generator] Lese ${manifest.length} Bauten aus shared_projects/ für Map '${mapId}'...`);

function getSchematicDimensions(schemPath) {
    if (!fs.existsSync(schemPath)) {
        return { width: 10, height: 10, length: 10 };
    }
    try {
        const buf = fs.readFileSync(schemPath);
        const raw = zlib.gunzipSync(buf);
        let width = 10, height = 10, length = 10;
        const wIdx = raw.indexOf(Buffer.from('Width'));
        if (wIdx !== -1) width = raw.readInt16BE(wIdx + 5);
        const hIdx = raw.indexOf(Buffer.from('Height'));
        if (hIdx !== -1) height = raw.readInt16BE(hIdx + 6);
        const lIdx = raw.indexOf(Buffer.from('Length'));
        if (lIdx !== -1) length = raw.readInt16BE(lIdx + 6);
        return { width, height, length };
    } catch (e) {
        console.warn('Konnte Schematic nicht parsen:', schemPath, e.message);
        return { width: 10, height: 10, length: 10 };
    }
}

function getStatusColor(status) {
    const s = (status || 'PLANNED').toUpperCase();
    if (s === 'COMPLETED' || s === 'FERTIG') {
        return {
            name: 'Fertig',
            badge: '🟢 Fertig',
            rgb: { r: 34, g: 197, b: 94 }, // Emerald green
            hex: '#22c55e'
        };
    } else if (s === 'IN_PROGRESS' || s === 'BAU') {
        return {
            name: 'Im Bau',
            badge: '🟠 Im Bau',
            rgb: { r: 249, g: 115, b: 22 }, // Vibrant orange
            hex: '#f97316'
        };
    } else {
        return {
            name: 'Geplant',
            badge: '🟡 Geplant',
            rgb: { r: 234, g: 179, b: 8 }, // Amber gold
            hex: '#eab308'
        };
    }
}

const markersHocon = [];
const markersJson = {};

for (const entry of manifest) {
    const jsonPath = path.join(sharedProjectsDir, entry.jsonUrl || `${entry.id}.json`);
    const schemPath = path.join(sharedProjectsDir, entry.schemUrl || `${entry.id}.schem`);

    if (!fs.existsSync(jsonPath)) {
        console.warn(`Projekt-Konfiguration fehlt für ${entry.id}: ${jsonPath}`);
        continue;
    }

    const config = JSON.parse(fs.readFileSync(jsonPath, 'utf8'));
    const dims = getSchematicDimensions(schemPath);

    const ox = config.origin ? config.origin.x : 0;
    const oy = config.origin ? config.origin.y : 64;
    const oz = config.origin ? config.origin.z : 0;
    const rot = ((config.rotation || 0) % 360 + 360) % 360;

    const effW = (rot === 90 || rot === 270) ? dims.length : dims.width;
    const effL = (rot === 90 || rot === 270) ? dims.width : dims.length;

    const centerX = ox + effW / 2;
    const centerZ = oz + effL / 2;
    const topY = oy + dims.height;

    const color = getStatusColor(config.status);
    const title = config.title || entry.title || config.id;
    const owner = config.owner || 'Gemeinschaft';
    const assigned = (config.assignedPlayers && config.assignedPlayers.length > 0)
        ? config.assignedPlayers.join(', ')
        : 'Alle (öffentlich)';

    const detailHtml = `<div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; min-width: 200px; padding: 4px;">
        <h3 style="margin: 0 0 6px 0; color: #f1f5f9; font-size: 16px; border-bottom: 2px solid ${color.hex}; padding-bottom: 4px;">${title}</h3>
        <p style="margin: 4px 0; font-size: 13px;"><b>Status:</b> <span style="color: ${color.hex}; font-weight: bold;">${color.badge}</span></p>
        <p style="margin: 4px 0; font-size: 13px;"><b>Bauleiter:</b> <span style="color: #38bdf8;">${owner}</span></p>
        <p style="margin: 4px 0; font-size: 13px;"><b>Zuständig:</b> <span style="color: #4ade80;">${assigned}</span></p>
        <p style="margin: 4px 0; font-size: 12px; color: #94a3b8;"><b>Position:</b> ${ox}, ${oy}, ${oz} (Rot: ${rot}°)</p>
        <p style="margin: 4px 0; font-size: 12px; color: #94a3b8;"><b>Maße:</b> ${dims.width} x ${dims.height} x ${dims.length} Blöcke</p>
    </div>`.replace(/"/g, '\\"').replace(/\n\s*/g, '');

    // 1. Extrude Marker (3D Prism Box)
    const boxId = `${config.id}_box`;
    markersHocon.push(`
            ${boxId}: {
                type: "extrude"
                label: "${title} [3D Zone]"
                position: { x: ${centerX}, y: ${oy}, z: ${centerZ} }
                shape-y: ${oy}
                shape: [
                    { x: ${ox}, z: ${oz} },
                    { x: ${ox + effW}, z: ${oz} },
                    { x: ${ox + effW}, z: ${oz + effL} },
                    { x: ${ox}, z: ${oz + effL} }
                ]
                height: ${dims.height}
                line-color: { r: ${color.rgb.r}, g: ${color.rgb.g}, b: ${color.rgb.b}, a: 1.0 }
                fill-color: { r: ${color.rgb.r}, g: ${color.rgb.g}, b: ${color.rgb.b}, a: 0.28 }
                line-width: 2
                detail: "${detailHtml}"
            }`);

    // 2. POI Marker (Stecknadel über dem Gebäude)
    const pinId = `${config.id}_pin`;
    markersHocon.push(`
            ${pinId}: {
                type: "poi"
                label: "${title} (${color.name})"
                position: { x: ${centerX}, y: ${topY + 1.5}, z: ${centerZ} }
                min-distance: 5
                max-distance: 25000
                detail: "${detailHtml}"
            }`);

    // JSON Marker Object
    markersJson[boxId] = {
        type: 'extrude',
        label: `${title} [3D Zone]`,
        position: { x: centerX, y: oy, z: centerZ },
        shapeY: oy,
        shape: [
            { x: ox, z: oz },
            { x: ox + effW, z: oz },
            { x: ox + effW, z: oz + effL },
            { x: ox, z: oz + effL }
        ],
        height: dims.height,
        lineColor: { r: color.rgb.r, g: color.rgb.g, b: color.rgb.b, a: 1.0 },
        fillColor: { r: color.rgb.r, g: color.rgb.g, b: color.rgb.b, a: 0.28 },
        lineWidth: 2,
        detail: detailHtml.replace(/\\"/g, '"')
    };

    markersJson[pinId] = {
        type: 'poi',
        label: `${title} (${color.name})`,
        position: { x: centerX, y: topY + 1.5, z: centerZ },
        minDistance: 5,
        maxDistance: 25000,
        detail: detailHtml.replace(/\\"/g, '"')
    };
}

// Full HOCON snippet for config/bluemap/maps/<mapId>.conf
const fullHocon = `# =====================================================================
# BlueMap Marker-Konfiguration fuer Bauplaene & Stadtplanung
# Diese Zeilen gehoeren in: config/bluemap/maps/${mapId}.conf
# Fuege diesen Block unter 'marker-sets: { ... }' ein!
# Danach auf dem Server einmalig: /bluemap reload
# =====================================================================

marker-sets: {
    schem-planner: {
        label: "📐 Baupläne & Stadtplanung"
        toggleable: true
        default-hidden: false
        sorting: 0

        markers: {${markersHocon.join('')}
        }
    }
}
`;

// Live JSON markers structure
const fullJson = {
    'schem-planner': {
        label: '📐 Baupläne & Stadtplanung',
        toggleable: true,
        defaultHidden: false,
        sorting: 0,
        markers: markersJson
    }
};

const outputDir = path.join(repoRoot, 'build', 'bluemap');
if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

const hoconFile = path.join(outputDir, `${mapId}_markers.conf`);
fs.writeFileSync(hoconFile, fullHocon, 'utf8');

const jsonFile = path.join(outputDir, 'markers.json');
fs.writeFileSync(jsonFile, JSON.stringify(fullJson, null, 2), 'utf8');

console.log('-------------------------------------------------------------');
console.log(`✔ Erfolgreich ${manifest.length} Bauten in BlueMap-Marker konvertiert!`);
console.log(`📄 HOCON Config: ${hoconFile}`);
console.log(`📄 Live JSON:    ${jsonFile}`);
console.log('-------------------------------------------------------------');
console.log('Anleitung:');
console.log(`1. Kopiere den Inhalt von '${path.basename(hoconFile)}' in deine 'config/bluemap/maps/${mapId}.conf'`);
console.log('2. Fuehre in der Server-Konsole oder ingame aus: /bluemap reload');
console.log('3. Fertig! Alle Bauplaene erscheinen sofort in 3D auf http://161.97.151.79:8100/#kekwelt');
