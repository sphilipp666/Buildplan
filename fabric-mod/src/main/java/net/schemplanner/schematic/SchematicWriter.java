package net.schemplanner.schematic;

import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Creates standard Sponge Schematic v2 (.schem) files directly from in-game world selections.
 * Fully compatible with Sponge, WorldEdit, Axiom, Litematica, and SchemPlanner.
 */
public class SchematicWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger("SchemPlanner");

    public record SaveResult(int width, int height, int length, int totalBlocks, int nonAirBlocks, Path filePath) {}

    public static SaveResult saveFromWorld(Level level, BlockPos pos1, BlockPos pos2, Path destinationFile, boolean includeAir) throws IOException {
        if (pos1 == null || pos2 == null) {
            throw new IllegalArgumentException("Pos 1 und Pos 2 müssen gesetzt sein!");
        }

        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());

        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;

        int totalVolume = width * height * length;
        if (totalVolume <= 0 || width > 1024 || height > 512 || length > 1024) {
            throw new IllegalArgumentException(String.format("Ungültige Abmessungen: %dx%dx%d (%d Blöcke)", width, height, length, totalVolume));
        }

        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        // In Sponge schematic format, air is traditionally palette ID 0
        palette.put(Blocks.AIR.defaultBlockState(), 0);

        int[] blockPaletteIds = new int[totalVolume];
        int nonAirCount = 0;
        ListTag blockEntitiesList = new ListTag();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int worldX = minX + x;
                    int worldY = minY + y;
                    int worldZ = minZ + z;
                    BlockPos worldPos = new BlockPos(worldX, worldY, worldZ);

                    BlockState state = level.getBlockState(worldPos);
                    if (!includeAir && state.isAir()) {
                        state = Blocks.AIR.defaultBlockState();
                    }

                    if (!state.isAir()) {
                        nonAirCount++;
                    }

                    int id = palette.computeIfAbsent(state, k -> palette.size());
                    int index = (y * length + z) * width + x;
                    blockPaletteIds[index] = id;

                    // Block Entity handling (Chests, Signs, etc.)
                    BlockEntity be = level.getBlockEntity(worldPos);
                    if (be != null) {
                        try {
                            CompoundTag beTag = be.saveCustomOnly(level.registryAccess());
                            if (beTag != null) {
                                beTag.putIntArray("Pos", new int[]{x, y, z});
                                Identifier beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
                                if (beId != null) {
                                    beTag.putString("Id", beId.toString());
                                }
                                blockEntitiesList.add(beTag);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }

        // Encode BlockData with VarInt
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int id : blockPaletteIds) {
            int v = id;
            while ((v & -128) != 0) {
                baos.write((v & 0x7F) | 0x80);
                v >>>= 7;
            }
            baos.write(v & 0x7F);
        }
        byte[] blockData = baos.toByteArray();

        // Build Sponge v2 Root Compound
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        try {
            root.putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        } catch (Exception e) {
            root.putInt("DataVersion", 4082);
        }
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        root.putIntArray("Offset", new int[]{0, 0, 0});

        CompoundTag paletteTag = new CompoundTag();
        for (Map.Entry<BlockState, Integer> entry : palette.entrySet()) {
            String stateStr = BlockStateParser.serialize(entry.getKey());
            paletteTag.putInt(stateStr, entry.getValue());
        }
        root.put("Palette", paletteTag);
        root.putInt("PaletteMax", palette.size());
        root.putByteArray("BlockData", blockData);

        if (!blockEntitiesList.isEmpty()) {
            root.put("BlockEntities", blockEntitiesList);
        }

        if (destinationFile.getParent() != null && !Files.exists(destinationFile.getParent())) {
            Files.createDirectories(destinationFile.getParent());
        }

        NbtIo.writeCompressed(root, destinationFile);
        LOGGER.info("[SchemPlanner] Schematic erfolgreich exportiert: {} ({}x{}x{}, {} Blöcke, {} ohne Luft)",
                destinationFile.getFileName(), width, height, length, totalVolume, nonAirCount);

        return new SaveResult(width, height, length, totalVolume, nonAirCount, destinationFile);
    }
}
