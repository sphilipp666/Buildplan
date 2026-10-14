/**
 * NBT (Named Binary Tag) & Sponge Schematic Parser
 * Supports Sponge Schematic Format v1, v2 and v3
 */

// NBT Tag Identifiers
export const TAG_END = 0;
export const TAG_BYTE = 1;
export const TAG_SHORT = 2;
export const TAG_INT = 3;
export const TAG_LONG = 4;
export const TAG_FLOAT = 5;
export const TAG_DOUBLE = 6;
export const TAG_BYTE_ARRAY = 7;
export const TAG_STRING = 8;
export const TAG_LIST = 9;
export const TAG_COMPOUND = 10;
export const TAG_INT_ARRAY = 11;
export const TAG_LONG_ARRAY = 12;

export class NBTReader {
    constructor(buffer) {
        this.view = new DataView(buffer.buffer, buffer.byteOffset, buffer.byteLength);
        this.bytes = new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
        this.offset = 0;
        this.decoder = new TextDecoder('utf-8');
    }

    readByte() {
        return this.view.getInt8(this.offset++);
    }

    readUnsignedByte() {
        return this.bytes[this.offset++];
    }

    readShort() {
        const v = this.view.getInt16(this.offset, false);
        this.offset += 2;
        return v;
    }

    readInt() {
        const v = this.view.getInt32(this.offset, false);
        this.offset += 4;
        return v;
    }

    readLong() {
        const v = this.view.getBigInt64(this.offset, false);
        this.offset += 8;
        return v;
    }

    readFloat() {
        const v = this.view.getFloat32(this.offset, false);
        this.offset += 4;
        return v;
    }

    readDouble() {
        const v = this.view.getFloat64(this.offset, false);
        this.offset += 8;
        return v;
    }

    readByteArray() {
        const len = this.readInt();
        const arr = new Uint8Array(this.bytes.buffer, this.bytes.byteOffset + this.offset, len);
        this.offset += len;
        return arr;
    }

    readString() {
        const len = this.view.getUint16(this.offset, false);
        this.offset += 2;
        const strBytes = new Uint8Array(this.bytes.buffer, this.bytes.byteOffset + this.offset, len);
        this.offset += len;
        return this.decoder.decode(strBytes);
    }

    readList() {
        const tagType = this.readByte();
        const len = this.readInt();
        const list = [];
        for (let i = 0; i < len; i++) {
            list.push(this.readTagPayload(tagType));
        }
        return list;
    }

    readCompound() {
        const compound = {};
        while (true) {
            const tagType = this.readByte();
            if (tagType === TAG_END) break;
            const name = this.readString();
            compound[name] = this.readTagPayload(tagType);
        }
        return compound;
    }

    readIntArray() {
        const len = this.readInt();
        const arr = new Int32Array(len);
        for (let i = 0; i < len; i++) {
            arr[i] = this.readInt();
        }
        return arr;
    }

    readLongArray() {
        const len = this.readInt();
        const arr = new BigInt64Array(len);
        for (let i = 0; i < len; i++) {
            arr[i] = this.readLong();
        }
        return arr;
    }

    readTagPayload(tagType) {
        switch (tagType) {
            case TAG_BYTE: return this.readByte();
            case TAG_SHORT: return this.readShort();
            case TAG_INT: return this.readInt();
            case TAG_LONG: return this.readLong();
            case TAG_FLOAT: return this.readFloat();
            case TAG_DOUBLE: return this.readDouble();
            case TAG_BYTE_ARRAY: return this.readByteArray();
            case TAG_STRING: return this.readString();
            case TAG_LIST: return this.readList();
            case TAG_COMPOUND: return this.readCompound();
            case TAG_INT_ARRAY: return this.readIntArray();
            case TAG_LONG_ARRAY: return this.readLongArray();
            default: throw new Error(`Unknown NBT Tag: ${tagType} at offset ${this.offset}`);
        }
    }

    parse() {
        const rootTag = this.readByte();
        if (rootTag === TAG_END) return null;
        if (rootTag !== TAG_COMPOUND) throw new Error("Root tag must be compound");
        const rootName = this.readString();
        const content = this.readCompound();
        return { name: rootName, value: content };
    }
}

/**
 * Decompresses GZIP / DEFLATE ArrayBuffer using browser native DecompressionStream
 * with fallback to window.pako if available.
 */
export async function decompressGzip(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    // Check GZIP magic bytes: 0x1f, 0x8b
    const isGzip = bytes[0] === 0x1f && bytes[1] === 0x8b;

    if (typeof DecompressionStream !== 'undefined') {
        try {
            const format = isGzip ? 'gzip' : 'deflate';
            const stream = new Response(new Blob([arrayBuffer])).body.pipeThrough(new DecompressionStream(format));
            return await new Response(stream).arrayBuffer();
        } catch (e) {
            console.warn("Native DecompressionStream failed, trying fallback...", e);
        }
    }

    if (window.pako) {
        const uncompressed = window.pako.ungzip(bytes);
        return uncompressed.buffer;
    }

    // If neither gzip nor deflate, check if it's already uncompressed NBT (root compound tag = 0x0a)
    if (bytes[0] === TAG_COMPOUND) {
        return arrayBuffer;
    }

    throw new Error("Konnte Datei nicht dekomprimieren (GZIP/Deflate Format erwartet).");
}

/**
 * Decodes Sponge Schematic VarInt sequence into an array of Palette IDs.
 */
export function decodeVarIntArray(byteArray, totalCount) {
    const result = new Int32Array(totalCount);
    let byteOffset = 0;
    const len = byteArray.length;

    // Fast path: if 1 byte per block (palette <= 128)
    if (len === totalCount) {
        for (let i = 0; i < totalCount; i++) {
            result[i] = byteArray[i];
        }
        return result;
    }

    for (let i = 0; i < totalCount; i++) {
        let value = 0;
        let shift = 0;
        while (byteOffset < len) {
            const b = byteArray[byteOffset++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) === 0) break;
            shift += 7;
        }
        result[i] = value;
    }
    return result;
}

/**
 * Parses a Sponge Schematic file buffer.
 * Returns parsed dimensions, palette, block data and material statistics.
 */
export async function parseSpongeSchematic(arrayBuffer) {
    const decompressed = await decompressGzip(arrayBuffer);
    const reader = new NBTReader(new Uint8Array(decompressed));
    const nbt = reader.parse();

    if (!nbt || !nbt.value) {
        throw new Error("Ungültiges NBT-Root-Format in Schematic");
    }

    const data = nbt.value;

    // Extract dimensions
    const width = Number(data.Width || (data.Width === 0 ? 0 : 0));
    const height = Number(data.Height || (data.Height === 0 ? 0 : 0));
    const length = Number(data.Length || (data.Length === 0 ? 0 : 0));

    if (!width || !height || !length) {
        throw new Error(`Ungültige Schematic-Abmessungen: ${width}x${height}x${length}`);
    }

    const totalBlocks = width * height * length;

    // Handle v1/v2 vs v3 format
    let rawPalette = null;
    let rawBlockData = null;

    if (data.Palette && data.BlockData) {
        // Sponge v1 / v2
        rawPalette = data.Palette;
        rawBlockData = data.BlockData;
    } else if (data.Blocks && data.Blocks.Palette && data.Blocks.Data) {
        // Sponge v3
        rawPalette = data.Blocks.Palette;
        rawBlockData = data.Blocks.Data;
    } else {
        throw new Error("Weder Palette noch BlockData im Sponge-Schematic-Format gefunden.");
    }

    // Invert palette: ID -> BlockState String (e.g. 0 -> "minecraft:air")
    const paletteMap = new Map(); // id -> string
    const stringToId = new Map();
    for (const [stateString, id] of Object.entries(rawPalette)) {
        const numericId = Number(id);
        paletteMap.set(numericId, stateString);
        stringToId.set(stateString, numericId);
    }

    // Decode VarInts into palette IDs
    const blockIds = decodeVarIntArray(rawBlockData, totalBlocks);

    // Extract non-air voxels and aggregate material counts
    const voxels = [];
    const materialCounts = new Map(); // blockName -> count

    let activeBlockCount = 0;

    for (let index = 0; index < totalBlocks; index++) {
        const id = blockIds[index];
        const stateString = paletteMap.get(id) || 'minecraft:air';

        // Extract clean block ID (e.g., "minecraft:oak_stairs[facing=east]" -> "minecraft:oak_stairs")
        const cleanName = stateString.split('[')[0];

        // Skip air blocks
        if (cleanName === 'minecraft:air' || cleanName === 'minecraft:cave_air' || cleanName === 'minecraft:void_air') {
            continue;
        }

        // Calculate coordinates:
        // Sponge index = (y * length + z) * width + x
        const x = index % width;
        const z = Math.floor(index / width) % length;
        const y = Math.floor(index / (width * length));

        voxels.push({
            x,
            y,
            z,
            state: stateString,
            block: cleanName,
            paletteId: id
        });

        // Tally materials
        const currentCount = materialCounts.get(cleanName) || 0;
        materialCounts.set(cleanName, currentCount + 1);
        activeBlockCount++;
    }

    // Offset information (if present)
    let offset = [0, 0, 0];
    if (Array.isArray(data.Offset) || data.Offset instanceof Int32Array) {
        offset = [data.Offset[0] || 0, data.Offset[1] || 0, data.Offset[2] || 0];
    } else if (data.Offset && typeof data.Offset === 'object') {
        offset = [data.Offset.x || 0, data.Offset.y || 0, data.Offset.z || 0];
    }

    return {
        width,
        height,
        length,
        totalBlocks,
        activeBlockCount,
        offset,
        palette: paletteMap,
        voxels,
        materialCounts: Array.from(materialCounts.entries()).map(([block, count]) => ({
            block,
            name: formatBlockName(block),
            count,
            stacks: Math.floor(count / 64),
            remainder: count % 64
        })).sort((a, b) => b.count - a.count)
    };
}

/**
 * Converts a Minecraft block identifier into a readable human name
 * e.g. "minecraft:dark_oak_planks" -> "Dark Oak Planks"
 */
export function formatBlockName(identifier) {
    const raw = identifier.replace('minecraft:', '');
    return raw
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}
