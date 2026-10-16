package net.schemplanner.schematic;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Multi-format Schematic Parser supporting:
 * - Sponge Schematic (.schem / .schematic) v1, v2, v3
 * - Litematica (.litematic) single & multi-region
 * - Vanilla Structure Block (.nbt)
 */
public class SpongeSchematic {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner");

    public record RawBlock(int x, int y, int z, BlockState state) {}

    private final String id;
    private final int width;
    private final int height;
    private final int length;
    private final List<RawBlock> rawBlocks = new ArrayList<>();
    private final List<PlannedBlock> plannedBlocks = new ArrayList<>();
    private final Map<Item, MaterialRequirement> materialRequirements = new LinkedHashMap<>();

    private BlockPos currentOrigin = BlockPos.ZERO;
    private int currentRotation = 0;

    public SpongeSchematic(String id, int width, int height, int length) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.length = length;
    }

    public static SpongeSchematic load(String projectId, Path filePath, BlockPos origin, int rotationDegrees) throws IOException {
        if (!Files.exists(filePath)) {
            throw new IOException("Schematic-Datei nicht gefunden: " + filePath);
        }

        CompoundTag root;
        try (InputStream is = Files.newInputStream(filePath)) {
            root = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        }

        if (root == null) {
            throw new IOException("NBT-Daten konnten nicht aus " + filePath + " gelesen werden.");
        }

        // Handle wrapped "Schematic" root compound (some Sponge v3 / WorldEdit exports)
        if (root.contains("Schematic") && !root.contains("Palette") && !root.contains("Blocks") && !root.contains("Regions")) {
            root = root.getCompoundOrEmpty("Schematic");
        }

        SpongeSchematic schematic;
        if (root.contains("Regions")) {
            // Litematica (.litematic) format
            schematic = loadLitematic(projectId, root, filePath);
        } else if (root.contains("blocks") && root.contains("palette")) {
            // Vanilla Structure (.nbt) format
            schematic = loadVanillaStructure(projectId, root, filePath);
        } else {
            // Sponge Schematic (.schem) v1, v2, v3
            schematic = loadSponge(projectId, root, filePath);
        }

        schematic.rebuildMaterials();
        schematic.updateTransform(origin, rotationDegrees);

        LOGGER.info("[SchemPlanner] Geladen: '{}' ({}x{}x{}, {} Blöcke, {} Material-Typen)",
                projectId, schematic.width, schematic.height, schematic.length,
                schematic.plannedBlocks.size(), schematic.materialRequirements.size());

        return schematic;
    }

    /**
     * Loads Sponge v1, v2, or v3 schematic data.
     */
    private static SpongeSchematic loadSponge(String projectId, CompoundTag root, Path filePath) throws IOException {
        short width = root.getShortOr("Width", (short) 0);
        short height = root.getShortOr("Height", (short) 0);
        short length = root.getShortOr("Length", (short) 0);

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException(String.format("Ungültige Abmessungen in %s: %dx%dx%d", filePath, width, height, length));
        }

        CompoundTag paletteCompound;
        byte[] blockDataBytes;

        if (root.contains("Palette") && root.contains("BlockData")) {
            paletteCompound = root.getCompoundOrEmpty("Palette");
            blockDataBytes = root.getByteArray("BlockData").orElse(null);
        } else if (root.contains("Blocks")) {
            CompoundTag blocksCompound = root.getCompoundOrEmpty("Blocks");
            paletteCompound = blocksCompound.getCompoundOrEmpty("Palette");
            blockDataBytes = blocksCompound.getByteArray("Data").orElse(null);
        } else {
            throw new IOException("Unbekanntes Schematic-Format in " + filePath);
        }

        if (blockDataBytes == null) {
            throw new IOException("BlockData-Bytes fehlen in " + filePath);
        }

        int totalBlocks = width * height * length;
        SpongeSchematic schematic = new SpongeSchematic(projectId, width, height, length);
        Map<Integer, BlockState> paletteMap = parsePalette(paletteCompound);
        int[] blockIds = decodeVarInts(blockDataBytes, totalBlocks);

        for (int index = 0; index < totalBlocks; index++) {
            int paletteId = blockIds[index];
            BlockState state = paletteMap.getOrDefault(paletteId, Blocks.AIR.defaultBlockState());
            if (state.isAir()) continue;

            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);

            schematic.rawBlocks.add(new RawBlock(x, y, z, state));
        }

        return schematic;
    }

    /**
     * Loads Litematica (.litematic) format (supports single and multi-region files).
     */
    private static SpongeSchematic loadLitematic(String projectId, CompoundTag root, Path filePath) throws IOException {
        CompoundTag regions = root.getCompoundOrEmpty("Regions");
        if (regions.isEmpty()) {
            throw new IOException("Keine Regionen in Litematic-Datei gefunden: " + filePath);
        }

        List<RawBlock> unnormalizedBlocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        for (String regionName : regions.keySet()) {
            CompoundTag region = regions.getCompoundOrEmpty(regionName);
            CompoundTag posTag = region.getCompoundOrEmpty("Position");
            CompoundTag sizeTag = region.getCompoundOrEmpty("Size");

            int regPosX = posTag.getIntOr("x", 0);
            int regPosY = posTag.getIntOr("y", 0);
            int regPosZ = posTag.getIntOr("z", 0);

            int rawSizeX = sizeTag.getIntOr("x", 0);
            int rawSizeY = sizeTag.getIntOr("y", 0);
            int rawSizeZ = sizeTag.getIntOr("z", 0);

            int sizeX = Math.abs(rawSizeX);
            int sizeY = Math.abs(rawSizeY);
            int sizeZ = Math.abs(rawSizeZ);
            if (sizeX == 0 || sizeY == 0 || sizeZ == 0) continue;

            int offsetX = regPosX + (rawSizeX < 0 ? rawSizeX + 1 : 0);
            int offsetY = regPosY + (rawSizeY < 0 ? rawSizeY + 1 : 0);
            int offsetZ = regPosZ + (rawSizeZ < 0 ? rawSizeZ + 1 : 0);

            ListTag paletteList = region.getListOrEmpty("BlockStatePalette");
            long[] blockStates = region.getLongArray("BlockStates").orElse(null);
            if (blockStates == null || paletteList.isEmpty()) continue;

            int paletteSize = paletteList.size();
            BlockState[] palette = new BlockState[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = parsePaletteEntryCompound(paletteList.getCompoundOrEmpty(i));
            }

            int bitsPerBlock = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
            long maxEntryValue = (1L << bitsPerBlock) - 1L;
            int volume = sizeX * sizeY * sizeZ;

            for (int index = 0; index < volume; index++) {
                long startBit = (long) index * bitsPerBlock;
                int startArrIndex = (int) (startBit >>> 6);
                int endArrIndex = (int) ((startBit + bitsPerBlock - 1L) >>> 6);
                int startBitOffset = (int) (startBit & 63L);

                if (startArrIndex >= blockStates.length) break;

                int paletteId;
                if (startArrIndex == endArrIndex || endArrIndex >= blockStates.length) {
                    paletteId = (int) ((blockStates[startArrIndex] >>> startBitOffset) & maxEntryValue);
                } else {
                    int endOffset = 64 - startBitOffset;
                    paletteId = (int) (((blockStates[startArrIndex] >>> startBitOffset) | (blockStates[endArrIndex] << endOffset)) & maxEntryValue);
                }

                if (paletteId < 0 || paletteId >= paletteSize) continue;
                BlockState state = palette[paletteId];
                if (state == null || state.isAir()) continue;

                int lx = index % sizeX;
                int lz = (index / sizeX) % sizeZ;
                int ly = index / (sizeX * sizeZ);

                int gx = offsetX + lx;
                int gy = offsetY + ly;
                int gz = offsetZ + lz;

                minX = Math.min(minX, gx);
                minY = Math.min(minY, gy);
                minZ = Math.min(minZ, gz);
                maxX = Math.max(maxX, gx);
                maxY = Math.max(maxY, gy);
                maxZ = Math.max(maxZ, gz);

                unnormalizedBlocks.add(new RawBlock(gx, gy, gz, state));
            }
        }

        if (unnormalizedBlocks.isEmpty()) {
            return new SpongeSchematic(projectId, 1, 1, 1);
        }

        int totalWidth = (maxX - minX) + 1;
        int totalHeight = (maxY - minY) + 1;
        int totalLength = (maxZ - minZ) + 1;

        SpongeSchematic schematic = new SpongeSchematic(projectId, totalWidth, totalHeight, totalLength);
        for (RawBlock rb : unnormalizedBlocks) {
            schematic.rawBlocks.add(new RawBlock(rb.x() - minX, rb.y() - minY, rb.z() - minZ, rb.state()));
        }
        return schematic;
    }

    /**
     * Loads Vanilla Structure Block (.nbt) format.
     */
    private static SpongeSchematic loadVanillaStructure(String projectId, CompoundTag root, Path filePath) {
        ListTag sizeList = root.getListOrEmpty("size");
        int width = sizeList.size() >= 3 ? Math.max(1, sizeList.getIntOr(0, 1)) : 1;
        int height = sizeList.size() >= 3 ? Math.max(1, sizeList.getIntOr(1, 1)) : 1;
        int length = sizeList.size() >= 3 ? Math.max(1, sizeList.getIntOr(2, 1)) : 1;

        ListTag paletteList = root.getListOrEmpty("palette");
        BlockState[] palette = new BlockState[paletteList.size()];
        for (int i = 0; i < paletteList.size(); i++) {
            palette[i] = parsePaletteEntryCompound(paletteList.getCompoundOrEmpty(i));
        }

        SpongeSchematic schematic = new SpongeSchematic(projectId, width, height, length);
        ListTag blocksList = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocksList.size(); i++) {
            CompoundTag blockTag = blocksList.getCompoundOrEmpty(i);
            int stateId = blockTag.getIntOr("state", 0);
            if (stateId < 0 || stateId >= palette.length) continue;
            BlockState state = palette[stateId];
            if (state == null || state.isAir()) continue;

            ListTag posList = blockTag.getListOrEmpty("pos");
            if (posList.size() < 3) continue;
            int x = posList.getIntOr(0, 0);
            int y = posList.getIntOr(1, 0);
            int z = posList.getIntOr(2, 0);

            schematic.rawBlocks.add(new RawBlock(x, y, z, state));
        }
        return schematic;
    }

    private void rebuildMaterials() {
        materialRequirements.clear();
        for (RawBlock rb : rawBlocks) {
            Item item = rb.state().getBlock().asItem();
            if (item != Items.AIR) {
                materialRequirements
                        .computeIfAbsent(item, k -> new MaterialRequirement(item, 0))
                        .increment(1);
            }
        }
    }

    /**
     * Fast in-memory recalculation of world positions and block rotations without disk I/O.
     */
    public synchronized void updateTransform(BlockPos origin, int rotationDegrees) {
        this.currentOrigin = origin;
        this.currentRotation = ((rotationDegrees % 360) + 360) % 360;
        Rotation blockRot = getBlockRotation(this.currentRotation);

        plannedBlocks.clear();
        for (RawBlock rb : rawBlocks) {
            int x = rb.x();
            int y = rb.y();
            int z = rb.z();

            int rotatedX;
            int rotatedZ;
            switch (this.currentRotation) {
                case 90 -> {
                    rotatedX = (length - 1) - z;
                    rotatedZ = x;
                }
                case 180 -> {
                    rotatedX = (width - 1) - x;
                    rotatedZ = (length - 1) - z;
                }
                case 270 -> {
                    rotatedX = z;
                    rotatedZ = (width - 1) - x;
                }
                default -> {
                    rotatedX = x;
                    rotatedZ = z;
                }
            }

            BlockPos worldPos = origin.offset(rotatedX, y, rotatedZ);
            BlockState rotatedState = rb.state().rotate(blockRot);
            plannedBlocks.add(new PlannedBlock(worldPos, rotatedState, rotatedX, y, rotatedZ));
        }
    }

    private static BlockState parsePaletteEntryCompound(CompoundTag entry) {
        String name = entry.getStringOr("Name", "minecraft:air");
        if (!entry.contains("Properties")) {
            return parseBlockStateString(name);
        }
        CompoundTag props = entry.getCompoundOrEmpty("Properties");
        if (props.isEmpty()) {
            return parseBlockStateString(name);
        }
        StringBuilder sb = new StringBuilder(name).append('[');
        boolean first = true;
        for (String propKey : props.keySet()) {
            String propVal = props.getStringOr(propKey, "");
            if (!propVal.isEmpty()) {
                if (!first) sb.append(',');
                sb.append(propKey).append('=').append(propVal);
                first = false;
            }
        }
        sb.append(']');
        return parseBlockStateString(sb.toString());
    }

    private static Map<Integer, BlockState> parsePalette(CompoundTag paletteCompound) {
        Map<Integer, BlockState> map = new HashMap<>();

        for (String key : paletteCompound.keySet()) {
            int id = paletteCompound.getIntOr(key, 0);
            BlockState state = parseBlockStateString(key);
            map.put(id, state);
        }

        return map;
    }

    private static BlockState parseBlockStateString(String stateString) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, stateString, true).blockState();
        } catch (Exception e) {
            try {
                String cleanId = stateString.split("\\[")[0];
                Identifier id = Identifier.tryParse(cleanId);
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    return BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
                }
            } catch (Exception ignored) {
            }
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static int[] decodeVarInts(byte[] bytes, int totalCount) {
        int[] result = new int[totalCount];
        int byteOffset = 0;
        int len = bytes.length;

        if (len == totalCount) {
            for (int i = 0; i < totalCount; i++) {
                result[i] = bytes[i] & 0xFF;
            }
            return result;
        }

        for (int i = 0; i < totalCount; i++) {
            int value = 0;
            int shift = 0;
            while (byteOffset < len) {
                byte b = bytes[byteOffset++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            result[i] = value;
        }
        return result;
    }

    private static Rotation getBlockRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> Rotation.CLOCKWISE_90;
            case 180 -> Rotation.CLOCKWISE_180;
            case 270 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public String getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLength() {
        return length;
    }

    public int getRotatedWidth() {
        return (currentRotation == 90 || currentRotation == 270) ? length : width;
    }

    public int getRotatedLength() {
        return (currentRotation == 90 || currentRotation == 270) ? width : length;
    }

    public BlockPos getCurrentOrigin() {
        return currentOrigin;
    }

    public int getCurrentRotation() {
        return currentRotation;
    }

    public synchronized List<PlannedBlock> getPlannedBlocks() {
        return Collections.unmodifiableList(plannedBlocks);
    }

    public Map<Item, MaterialRequirement> getMaterialRequirements() {
        return materialRequirements;
    }
}
