package net.schemplanner.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for layer navigation, reload, material list UI, and Free-Walk placement mode.
 */
public class KeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.parse("schemplanner:general"));

    public static KeyMapping keyLayerUp;
    public static KeyMapping keyLayerDown;
    public static KeyMapping keyLayerToggle;
    public static KeyMapping keyMaterialList;
    public static KeyMapping keyPlacement;
    public static KeyMapping keyReload;

    // Free-Walk Placement Hotkeys
    public static KeyMapping keyRotate;
    public static KeyMapping keySnapTarget;
    public static KeyMapping keyConfirmPlacement;

    // Area Selection / Copy Hotkeys
    public static KeyMapping keySelectPos1;
    public static KeyMapping keySelectPos2;

    public static void register() {
        keyLayerUp = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.layer_up",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                CATEGORY
        ));

        keyLayerDown = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.layer_down",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                CATEGORY
        ));

        keyLayerToggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.layer_toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_HOME,
                CATEGORY
        ));

        keyMaterialList = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.material_list",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                CATEGORY
        ));

        keyPlacement = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.placement",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));

        keyReload = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.reload",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                CATEGORY
        ));

        keyRotate = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.rotate",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R,
                CATEGORY
        ));

        keySnapTarget = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.snap_target",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                CATEGORY
        ));

        keyConfirmPlacement = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.confirm",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_ENTER,
                CATEGORY
        ));

        keySelectPos1 = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.select_pos1",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        keySelectPos2 = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.schemplanner.select_pos2",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CATEGORY
        ));
    }
}
