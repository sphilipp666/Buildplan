package net.schemplanner.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Keybindings for layer navigation, reload and material list UI.
 */
public class KeyBindings {
    public static final String KEY_CATEGORY = "category.schemplanner";

    public static KeyBinding keyLayerUp;
    public static KeyBinding keyLayerDown;
    public static KeyBinding keyLayerToggle;
    public static KeyBinding keyMaterialList;
    public static KeyBinding keyReload;

    public static void register() {
        keyLayerUp = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schemplanner.layer_up",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_UP,
                KEY_CATEGORY
        ));

        keyLayerDown = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schemplanner.layer_down",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_PAGE_DOWN,
                KEY_CATEGORY
        ));

        keyLayerToggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schemplanner.layer_toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_HOME,
                KEY_CATEGORY
        ));

        keyMaterialList = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schemplanner.material_list",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                KEY_CATEGORY
        ));

        keyReload = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.schemplanner.reload",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10,
                KEY_CATEGORY
        ));
    }
}
