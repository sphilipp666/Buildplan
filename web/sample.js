/**
 * Sample Schematic Generator for Testing and Demo Preview
 * Creates a charming Minecraft cottage with stone base, oak frame, planks, glass windows, and roof.
 */

export function createSampleCottage() {
    const width = 7;
    const height = 6;
    const length = 7;

    const voxels = [];
    const materialCounts = new Map();

    function addBlock(x, y, z, block) {
        voxels.push({
            x,
            y,
            z,
            state: block,
            block: block.split('[')[0],
            paletteId: 1
        });
        const clean = block.split('[')[0];
        materialCounts.set(clean, (materialCounts.get(clean) || 0) + 1);
    }

    // Foundation (Stone Bricks)
    for (let x = 0; x < width; x++) {
        for (let z = 0; z < length; z++) {
            addBlock(x, 0, z, 'minecraft:stone_bricks');
        }
    }

    // Wooden Corner Pillars (Oak Logs)
    const corners = [[0, 0], [0, length - 1], [width - 1, 0], [width - 1, length - 1]];
    for (let y = 1; y <= 3; y++) {
        for (const [cx, cz] of corners) {
            addBlock(cx, y, cz, 'minecraft:oak_log');
        }
    }

    // Walls (Oak Planks & Glass)
    for (let y = 1; y <= 3; y++) {
        for (let x = 1; x < width - 1; x++) {
            // Front & Back walls
            const isWindowFront = (x === 2 || x === 4) && y === 2;
            const isDoor = x === 3 && (y === 1 || y === 2);

            if (!isDoor) {
                addBlock(x, y, 0, isWindowFront ? 'minecraft:glass' : 'minecraft:oak_planks');
            }
            addBlock(x, y, length - 1, (x === 3 && y === 2) ? 'minecraft:glass' : 'minecraft:oak_planks');
        }

        for (let z = 1; z < length - 1; z++) {
            // Side walls
            const isWindow = (z === 3 && y === 2);
            addBlock(0, y, z, isWindow ? 'minecraft:glass' : 'minecraft:oak_planks');
            addBlock(width - 1, y, z, isWindow ? 'minecraft:glass' : 'minecraft:oak_planks');
        }
    }

    // Decorative Interior (Crafting Table, Chest, Lantern)
    addBlock(1, 1, 5, 'minecraft:crafting_table');
    addBlock(5, 1, 5, 'minecraft:chest');
    addBlock(3, 3, 3, 'minecraft:lantern');

    // Roof (Cobblestone)
    // Layer 4 (Lower Roof)
    for (let x = 0; x < width; x++) {
        for (let z = 0; z < length; z++) {
            if (x === 0 || x === width - 1 || z === 0 || z === length - 1) {
                addBlock(x, 4, z, 'minecraft:cobblestone');
            }
        }
    }

    // Layer 5 (Roof Peak)
    for (let x = 1; x < width - 1; x++) {
        for (let z = 1; z < length - 1; z++) {
            addBlock(x, 5, z, 'minecraft:spruce_planks');
        }
    }

    return {
        width,
        height,
        length,
        totalBlocks: width * height * length,
        activeBlockCount: voxels.length,
        offset: [0, 0, 0],
        voxels,
        materialCounts: Array.from(materialCounts.entries()).map(([block, count]) => ({
            block,
            name: block.replace('minecraft:', '').split('_').map(w => w.charAt(0).toUpperCase() + w.slice(1)).join(' '),
            count,
            stacks: Math.floor(count / 64),
            remainder: count % 64
        })).sort((a, b) => b.count - a.count)
    };
}
