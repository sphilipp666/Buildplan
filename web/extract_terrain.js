const fs = require('fs');
const zlib = require('zlib');
const path = require('path');

const mcaPath = 'C:/Users/phili/AppData/Roaming/ModrinthApp/profiles/Vanilla+ Game (1)/saves/New World (1)/dimensions/minecraft/overworld/region/r.0.-1.mca';
if (!fs.existsSync(mcaPath)) {
    console.error('Region file not found at:', mcaPath);
    process.exit(1);
}

const fd = fs.openSync(mcaPath, 'r');
const header = Buffer.alloc(4096);
fs.readSync(fd, header, 0, 4096, 0);

// We want chunks around chunk (14, -28).
// 5x5 chunk area: chunkX from 12 to 16, chunkZ from -30 to -26
const minChunkX = 12;
const maxChunkX = 16;
const minChunkZ = -30;
const maxChunkZ = -26;

const chunkWidth = (maxChunkX - minChunkX + 1);
const chunkLength = (maxChunkZ - minChunkZ + 1);
const totalW = chunkWidth * 16; // 80
const totalL = chunkLength * 16; // 80

const heightGrid = new Float32Array(totalW * totalL);
heightGrid.fill(-60);

for (let cz = minChunkZ; cz <= maxChunkZ; cz++) {
    for (let cx = minChunkX; cx <= maxChunkX; cx++) {
        // In region r.0.-1:
        const rx = cx % 32;
        const rz = ((cz % 32) + 32) % 32;
        const idx = 4 * (rz * 32 + rx);
        const offset = (header[idx] << 16 | header[idx + 1] << 8 | header[idx + 2]) * 4096;

        if (offset === 0) continue;

        const chunkHeader = Buffer.alloc(5);
        fs.readSync(fd, chunkHeader, 0, 5, offset);
        const len = chunkHeader.readUInt32BE(0) - 1;
        const chunkData = Buffer.alloc(len);
        fs.readSync(fd, chunkData, 0, len, offset + 5);

        try {
            const raw = zlib.inflateSync(chunkData);
            const tag = Buffer.from('WORLD_SURFACE');
            const p = raw.indexOf(tag);
            if (p === -1) continue;

            const longBytes = raw.subarray(p + tag.length + 4, p + tag.length + 4 + 37 * 8);
            const bitsPerVal = 9;
            const mask = (1 << bitsPerVal) - 1;
            const valsPerLong = Math.floor(64 / bitsPerVal);
            const chunkHeights = [];

            for (let i = 0; i < 37; i++) {
                const l = longBytes.readBigUInt64BE(i * 8);
                for (let j = 0; j < valsPerLong; j++) {
                    if (chunkHeights.length < 256) {
                        const h = Number((l >> BigInt(j * bitsPerVal)) & BigInt(mask)) - 64;
                        chunkHeights.push(h);
                    }
                }
            }

            // In Minecraft chunk heightmaps, order is Z then X: index = z * 16 + x
            const startX = (cx - minChunkX) * 16;
            const startZ = (cz - minChunkZ) * 16;

            for (let z = 0; z < 16; z++) {
                for (let x = 0; x < 16; x++) {
                    const h = chunkHeights[z * 16 + x];
                    const globalIdx = (startZ + z) * totalW + (startX + x);
                    heightGrid[globalIdx] = h;
                }
            }
        } catch (e) {
            console.warn('Failed parsing chunk', cx, cz, e.message);
        }
    }
}
fs.closeSync(fd);

const outData = {
    worldName: 'New World (1)',
    centerX: 237,
    centerY: -60,
    centerZ: -443,
    minX: minChunkX * 16,
    minZ: minChunkZ * 16,
    gridWidth: totalW,
    gridLength: totalL,
    heights: Array.from(heightGrid)
};

const outFile = path.join(__dirname, 'singleplayer_world.json');
fs.writeFileSync(outFile, JSON.stringify(outData));
console.log('Successfully exported singleplayer terrain to', outFile, 'Size:', (fs.statSync(outFile).size / 1024).toFixed(1), 'KB');
