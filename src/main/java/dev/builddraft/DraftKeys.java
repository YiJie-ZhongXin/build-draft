package dev.builddraft;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** All bindings are saved by Minecraft in options.txt and editable in Controls. */
public final class DraftKeys {
    public static KeyBinding EDITOR, PROJECTION, ANCHOR, PREVIEW, DOWN, UP, TOOL, ROTATE, SAMPLE, PALETTE, CENTER, PLANE, EXTRUDE, MOVE_GRID;
    public static void register() {
        EDITOR = bind("editor", GLFW.GLFW_KEY_B);
        PROJECTION = bind("projection", GLFW.GLFW_KEY_P);
        ANCHOR = bind("anchor", GLFW.GLFW_KEY_N);
        PREVIEW = bind("preview", GLFW.GLFW_KEY_V);
        DOWN = bind("down", GLFW.GLFW_KEY_Q);
        UP = bind("up", GLFW.GLFW_KEY_E);
        TOOL = bind("tool", GLFW.GLFW_KEY_G);
        ROTATE = bind("rotate", GLFW.GLFW_KEY_R);
        SAMPLE = bind("sample", GLFW.GLFW_KEY_M);
        PALETTE = bind("palette", GLFW.GLFW_KEY_F);
        CENTER = bind("center", GLFW.GLFW_KEY_HOME);
        PLANE = bind("plane", GLFW.GLFW_KEY_L);
        EXTRUDE = bind("extrude", GLFW.GLFW_KEY_X);
        MOVE_GRID = bind("move_grid", GLFW.GLFW_KEY_H);
    }
    private static KeyBinding bind(String action, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.builddraft." + action, InputUtil.Type.KEYSYM, key, "category.builddraft"));
    }
    public static String label(KeyBinding key) { return key.getBoundKeyLocalizedText().getString(); }
    public static void clearPending() {
        for (KeyBinding key : new KeyBinding[]{EDITOR, PROJECTION, ANCHOR, PREVIEW, DOWN, UP, TOOL, ROTATE, SAMPLE, PALETTE, CENTER, PLANE, EXTRUDE, MOVE_GRID}) {
            while (key.wasPressed()) { /* Screen events handle these keys directly. */ }
        }
    }
}
