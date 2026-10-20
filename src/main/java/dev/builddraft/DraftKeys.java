package dev.builddraft;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** All bindings are saved by Minecraft in options.txt and editable in Controls. */
public final class DraftKeys {
    public static KeyBinding EDITOR, PREVIEW, ROTATE, SAMPLE, PALETTE, CENTER, LIBRARY, ASSIST;
    public static void register() {
        EDITOR = bind("editor", GLFW.GLFW_KEY_B);
        PREVIEW = bind("preview", GLFW.GLFW_KEY_V);
        ROTATE = bind("rotate", GLFW.GLFW_KEY_R);
        SAMPLE = bind("sample", GLFW.GLFW_KEY_M);
        PALETTE = bind("palette", GLFW.GLFW_KEY_F);
        CENTER = bind("center", GLFW.GLFW_KEY_HOME);
        LIBRARY = bind("library", GLFW.GLFW_KEY_L);
        ASSIST = bind("assist", GLFW.GLFW_KEY_LEFT_ALT);
    }
    private static KeyBinding bind(String action, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.builddraft." + action, InputUtil.Type.KEYSYM, key, "category.builddraft"));
    }
    public static String label(KeyBinding key) { return key.getBoundKeyLocalizedText().getString(); }
    public static void clearPending() {
        for (KeyBinding key : new KeyBinding[]{EDITOR, PREVIEW, ROTATE, SAMPLE, PALETTE, CENTER, LIBRARY, ASSIST}) {
            while (key.wasPressed()) { /* Screen events handle these keys directly. */ }
        }
    }
}
