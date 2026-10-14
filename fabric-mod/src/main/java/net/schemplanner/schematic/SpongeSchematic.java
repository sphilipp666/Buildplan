package net.schemplanner.schematic;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Sponge Schematic Parser (.schem) supporting Sponge format versions 1, 2, and 3 for Fabric 26.2.
 */
public class SpongeSchematic {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner");

    private final String id;
    private final int width;
    private final int height;
    private final int length;
    private final List<PlannedBlock> plannedBlocks = new ArrayList<>();
    private final Map<Item, MaterialRequirement> materialRequirements = new LinkedHashMap<>();

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

        NbtCompound root;
        try (InputStream is = Files.newInputStream(filePath)) {
            root = NbtIo.readCompressed(is, NbtSizeTracker.ofUnlimitedBytes());
        }

        if (root == null) {
            throw new IOException("NBT-Daten konnten nicht aus " + filePath + " gelesen werden.");
        }

        short width = root.getShort("Width");
        short height = root.getShort("Height");
        short length = root.getShort("Length");

        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IOException(String.format("Ungültige Abmessungen in %s: %dx%dx%d", filePath, width, height, length));
        }

        int totalBlocks = width * height * length;
        SpongeSchematic schematic = new SpongeSchematic(projectId, width, height, length);

        // Extract Palette & BlockData across v1/v2 and v3
        NbtCompound paletteCompound;
        byte[] blockDataBytes;

        if (root.contains("Palette", NbtElement.COMPOUND_TYPE) && root.contains("BlockData", NbtElement.BYTE_ARRAY_TYPE)) {
            // Sponge v1 & v2
            paletteCompound = root.getCompound("Palette");
            blockDataBytes = root.getByteArray("BlockData");
        } else if (root.contains("Blocks", NbtElement.COMPOUND_TYPE)) {
            // Sponge v3
            NbtCompound blocksCompound = root.getCompound("Blocks");
            paletteCompound = blocksCompound.getCompound("Palette");
            blockDataBytes = blocksCompound.getByteArray("Data");
        } else {
            throw new IOException("Weder Sponge v1/v2 noch v3 Palette & BlockData gefunden in " + filePath);
        }

        // Parse Palette: map ID -> BlockState
        Map<Integer, BlockState> paletteMap = parsePalette(paletteCompound);

        // Decode VarInts
        int[] blockIds = decodeVarInts(blockDataBytes, totalBlocks);

        // Determine rotation parameters
        BlockRotation blockRot = getBlockRotation(rotationDegrees);

        // Populate blocks and aggregate materials
        for (int index = 0; index < totalBlocks; index++) {
            int paletteId = blockIds[index];
            BlockState state = paletteMap.getOrDefault(paletteId, Blocks.AIR.getDefaultState());

            if (state.isAir()) {
                continue;
            }

            // Sponge index calculation: index = (y * length + z) * width + x
            int x = index % width;
            int z = (index / width) % length;
            int y = index / (width * length);

            // Compute rotated coordinates
            int rotatedX;
            int rotatedZ;

            switch (rotationDegrees % 360) {
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
                default -> { // 0
                    rotatedX = x;
                    rotatedZ = z;
                }
            }

            BlockPos worldPos = origin.add(rotatedX, y, rotatedZ);
            BlockState rotatedState = state.rotate(blockRot);

            schematic.plannedBlocks.add(new PlannedBlock(worldPos, rotatedState, rotatedX, y, rotatedZ));

            // Aggregate materials
            Item item = rotatedState.getBlock().asItem();
            if (item != Items.AIR) {
                schematic.materialRequirements
                        .computeIfAbsent(item, k -> new MaterialRequirement(item, 0))
                        .increment(1);
            }
        }

        LOGGER.info("[SchemPlanner] Geladen: '{}' ({} Blöcke, {} Material-Typen)",
                projectId, schematic.plannedBlocks.size(), schematic.materialRequirements.size());

        return schematic;
    }

    private static Map<Integer, BlockState> parsePalette(NbtCompound paletteCompound) {
        Map<Integer, BlockState> map = new HashMap<>();

        for (String key : paletteCompound.getKeys()) {
            int id = paletteCompound.getInt(key);
            BlockState state = parseBlockStateString(key);
            map.put(id, state);
        }

        return map;
    }

    private static BlockState parseBlockStateString(String stateString) {
        try {
            // First attempt: parsing block state string using Minecraft's BlockArgumentParser
            var wrapper = Registries.BLOCK.getReadOnlyWrapper();
            return BlockArgumentParser.block(wrapper, stateString, true).blockState();
        } catch (Exception e) {
            // Fallback: parse clean block identifier without properties
            try {
                String cleanId = stateString.split("\\[")[0];
                Identifier id = Identifier.tryParse(cleanId);
                if (id != null && Registries.BLOCK.containsId(id)) {
                    return Registries.BLOCK.get(id).getDefaultState();
                }
            } catch (Exception ignored) {
            }
            return Blocks.AIR.getDefaultState();
        }
    }

    private static int[] decodeVarInts(byte[] bytes, int totalCount) {
        int[] result = new int[totalCount];
        int byteOffset = 0;
        int len = bytes.length;

        // Fast path for schematics where all palette IDs fit in 1 byte
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

    private static BlockRotation getBlockRotation(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return switch (normalized) {
            case 90 -> BlockRotation.CLOCKWISE_90;
            case 180 -> BlockRotation.CLOCKWISE_180;
            case 270 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
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

    public List<PlannedBlock> getPlannedBlocks() {
        return plannedBlocks;
    }

    public Map<Item, MaterialRequirement> getMaterialRequirements() {
        return materialRequirements;
    }
}
