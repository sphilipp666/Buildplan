/**
 * Minecraft Block Color Palette & Material Definitions
 * Maps Minecraft block identifiers to Three.js compatible hex colors and opacities.
 */

export const BLOCK_COLORS = {
    // Stone / Minerals
    'minecraft:stone': 0x828282,
    'minecraft:smooth_stone': 0x9b9b9b,
    'minecraft:cobblestone': 0x6e6e6e,
    'minecraft:mossy_cobblestone': 0x5a735a,
    'minecraft:stone_bricks': 0x757575,
    'minecraft:mossy_stone_bricks': 0x5e705e,
    'minecraft:cracked_stone_bricks': 0x636363,
    'minecraft:andesite': 0x8a8a8a,
    'minecraft:polished_andesite': 0x949494,
    'minecraft:granite': 0x996756,
    'minecraft:polished_granite': 0xa16f5d,
    'minecraft:diorite': 0xbfbfbf,
    'minecraft:polished_diorite': 0xcacaca,
    'minecraft:deepslate': 0x3d3d43,
    'minecraft:cobbled_deepslate': 0x333338,
    'minecraft:polished_deepslate': 0x3b3b40,
    'minecraft:deepslate_bricks': 0x2f2f33,
    'minecraft:bedrock': 0x2b2b2b,
    'minecraft:obsidian': 0x161224,
    'minecraft:crying_obsidian': 0x2c1445,

    // Dirt / Earth / Sand
    'minecraft:dirt': 0x866043,
    'minecraft:coarse_dirt': 0x77553b,
    'minecraft:rooted_dirt': 0x90674c,
    'minecraft:mud': 0x3c3938,
    'minecraft:grass_block': 0x5c8e32,
    'minecraft:podzol': 0x5c4226,
    'minecraft:mycelium': 0x6f6265,
    'minecraft:farmland': 0x593d25,
    'minecraft:sand': 0xd6cda2,
    'minecraft:red_sand': 0xb85b24,
    'minecraft:sandstone': 0xd9d0a6,
    'minecraft:red_sandstone': 0xb85c27,
    'minecraft:gravel': 0x7d7b7a,
    'minecraft:clay': 0xa0a7b4,

    // Wood Planks
    'minecraft:oak_planks': 0xa2824e,
    'minecraft:spruce_planks': 0x684e32,
    'minecraft:birch_planks': 0xc5b882,
    'minecraft:jungle_planks': 0x9e6d4d,
    'minecraft:acacia_planks': 0xa85a32,
    'minecraft:dark_oak_planks': 0x422a15,
    'minecraft:mangrove_planks': 0x753535,
    'minecraft:cherry_planks': 0xe3aab2,
    'minecraft:bamboo_planks': 0xc4b34b,
    'minecraft:crimson_planks': 0x652838,
    'minecraft:warped_planks': 0x2b6863,

    // Wood Logs / Stems
    'minecraft:oak_log': 0x675232,
    'minecraft:spruce_log': 0x3b2a1a,
    'minecraft:birch_log': 0xd5d4cb,
    'minecraft:jungle_log': 0x56431e,
    'minecraft:acacia_log': 0x676057,
    'minecraft:dark_oak_log': 0x36291a,
    'minecraft:mangrove_log': 0x532924,
    'minecraft:cherry_log': 0x372027,
    'minecraft:crimson_stem': 0x5c1825,
    'minecraft:warped_stem': 0x284a4b,

    // Leaves
    'minecraft:oak_leaves': 0x3a691e,
    'minecraft:spruce_leaves': 0x2e4a2d,
    'minecraft:birch_leaves': 0x56752b,
    'minecraft:jungle_leaves': 0x377519,
    'minecraft:acacia_leaves': 0x486f1e,
    'minecraft:dark_oak_leaves': 0x274914,
    'minecraft:mangrove_leaves': 0x466d21,
    'minecraft:cherry_leaves': 0xf2a7bb,
    'minecraft:azalea_leaves': 0x4d7524,
    'minecraft:flowering_azalea_leaves': 0x7e5f5f,

    // Bricks & Tiles
    'minecraft:bricks': 0x964c39,
    'minecraft:mud_bricks': 0x8a634a,
    'minecraft:nether_bricks': 0x2c151a,
    'minecraft:red_nether_bricks': 0x460c11,
    'minecraft:end_stone_bricks': 0xdbdc9e,
    'minecraft:prismarine': 0x5f998c,
    'minecraft:prismarine_bricks': 0x5fc4b1,
    'minecraft:dark_prismarine': 0x335648,
    'minecraft:sea_lantern': 0xabe1db,

    // Glass & Liquids (Transparent)
    'minecraft:glass': 0xddeef5,
    'minecraft:tinted_glass': 0x2a2530,
    'minecraft:white_stained_glass': 0xffffff,
    'minecraft:black_stained_glass': 0x191919,
    'minecraft:red_stained_glass': 0x992727,
    'minecraft:blue_stained_glass': 0x334cb2,
    'minecraft:green_stained_glass': 0x5e7c16,
    'minecraft:yellow_stained_glass': 0xe5e533,
    'minecraft:cyan_stained_glass': 0x1589c0,
    'minecraft:purple_stained_glass': 0x7b2fbe,
    'minecraft:orange_stained_glass': 0xd87f33,
    'minecraft:water': 0x2f5bb5,
    'minecraft:lava': 0xd85b14,

    // Concrete & Terracotta
    'minecraft:white_concrete': 0xcfd5d6,
    'minecraft:orange_concrete': 0xe06101,
    'minecraft:magenta_concrete': 0xa9309f,
    'minecraft:light_blue_concrete': 0x2389c7,
    'minecraft:yellow_concrete': 0xf1af15,
    'minecraft:lime_concrete': 0x5ea919,
    'minecraft:pink_concrete': 0xd5658e,
    'minecraft:gray_concrete': 0x373a3e,
    'minecraft:light_gray_concrete': 0x7d7d73,
    'minecraft:cyan_concrete': 0x157788,
    'minecraft:purple_concrete': 0x64209c,
    'minecraft:blue_concrete': 0x2c2e8f,
    'minecraft:brown_concrete': 0x603c20,
    'minecraft:green_concrete': 0x495b24,
    'minecraft:red_concrete': 0x8e2121,
    'minecraft:black_concrete': 0x080a0f,
    'minecraft:terracotta': 0x985e44,

    // Ores & Metals
    'minecraft:iron_block': 0xd8d8d8,
    'minecraft:gold_block': 0xf6d03d,
    'minecraft:diamond_block': 0x5decf5,
    'minecraft:emerald_block': 0x17dd62,
    'minecraft:lapis_block': 0x184293,
    'minecraft:copper_block': 0xc06c51,
    'minecraft:netherite_block': 0x443f41,
    'minecraft:coal_block': 0x121212,

    // Functional / Decorative
    'minecraft:glowstone': 0xfad67f,
    'minecraft:lantern': 0x2c2d2f,
    'minecraft:soul_lantern': 0x3a4b56,
    'minecraft:iron_bars': 0x85898d,
    'minecraft:chain': 0x32343c,
    'minecraft:bookshelf': 0x765934,
    'minecraft:crafting_table': 0x785635,
    'minecraft:furnace': 0x696868,
    'minecraft:chest': 0x88682a,
    'minecraft:trapped_chest': 0x88682a,
    'minecraft:barrel': 0x7c5e37,
    'minecraft:hay_block': 0xa68c17,
    'minecraft:scaffolding': 0xbda96d
};

/**
 * Checks if a block is transparent or translucent.
 */
export function isBlockTransparent(identifier) {
    const name = identifier.split('[')[0];
    return name.includes('glass') ||
           name.includes('water') ||
           name.includes('ice') ||
           name.includes('pane');
}

/**
 * Returns a stable hex color for any block identifier.
 * Uses exact match, substring match, or pseudo-random hash fallback.
 */
export function getBlockColor(identifier) {
    const clean = identifier.split('[')[0];

    // 1. Direct match
    if (BLOCK_COLORS[clean]) {
        return BLOCK_COLORS[clean];
    }

    // 2. Substring / Category rules
    if (clean.includes('oak_planks') || clean.includes('oak_slab') || clean.includes('oak_stairs') || clean.includes('oak_fence')) return BLOCK_COLORS['minecraft:oak_planks'];
    if (clean.includes('spruce_planks') || clean.includes('spruce_slab') || clean.includes('spruce_stairs')) return BLOCK_COLORS['minecraft:spruce_planks'];
    if (clean.includes('birch_planks') || clean.includes('birch_slab') || clean.includes('birch_stairs')) return BLOCK_COLORS['minecraft:birch_planks'];
    if (clean.includes('dark_oak')) return BLOCK_COLORS['minecraft:dark_oak_planks'];
    if (clean.includes('jungle')) return BLOCK_COLORS['minecraft:jungle_planks'];
    if (clean.includes('acacia')) return BLOCK_COLORS['minecraft:acacia_planks'];
    if (clean.includes('cherry')) return BLOCK_COLORS['minecraft:cherry_planks'];
    if (clean.includes('bamboo')) return BLOCK_COLORS['minecraft:bamboo_planks'];
    if (clean.includes('mangrove')) return BLOCK_COLORS['minecraft:mangrove_planks'];
    if (clean.includes('stone_brick') || clean.includes('stone_slab') || clean.includes('stone_stairs')) return BLOCK_COLORS['minecraft:stone_bricks'];
    if (clean.includes('cobblestone')) return BLOCK_COLORS['minecraft:cobblestone'];
    if (clean.includes('deepslate')) return BLOCK_COLORS['minecraft:deepslate'];
    if (clean.includes('andesite')) return BLOCK_COLORS['minecraft:andesite'];
    if (clean.includes('diorite')) return BLOCK_COLORS['minecraft:diorite'];
    if (clean.includes('granite')) return BLOCK_COLORS['minecraft:granite'];
    if (clean.includes('leaves')) return BLOCK_COLORS['minecraft:oak_leaves'];
    if (clean.includes('log') || clean.includes('wood')) return BLOCK_COLORS['minecraft:oak_log'];
    if (clean.includes('glass')) return BLOCK_COLORS['minecraft:glass'];
    if (clean.includes('wool') || clean.includes('carpet')) return 0xdedede;
    if (clean.includes('terracotta')) return BLOCK_COLORS['minecraft:terracotta'];
    if (clean.includes('concrete')) return BLOCK_COLORS['minecraft:light_gray_concrete'];
    if (clean.includes('brick')) return BLOCK_COLORS['minecraft:bricks'];
    if (clean.includes('sandstone')) return BLOCK_COLORS['minecraft:sandstone'];
    if (clean.includes('quartz')) return 0xeae6df;
    if (clean.includes('iron')) return BLOCK_COLORS['minecraft:iron_block'];
    if (clean.includes('gold')) return BLOCK_COLORS['minecraft:gold_block'];
    if (clean.includes('copper')) return BLOCK_COLORS['minecraft:copper_block'];

    // 3. Fallback: Generate deterministic vibrant pastel color from block name hash
    let hash = 0;
    for (let i = 0; i < clean.length; i++) {
        hash = clean.charCodeAt(i) + ((hash << 5) - hash);
    }
    const r = (hash & 0x7F) + 100;
    const g = ((hash >> 8) & 0x7F) + 100;
    const b = ((hash >> 16) & 0x7F) + 100;
    return (r << 16) | (g << 8) | b;
}
