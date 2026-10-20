package dev.builddraft.mixin;

import dev.builddraft.DraftMouseControl;
import dev.builddraft.screen.DraftEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.util.GlfwUtil;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin implements DraftMouseControl {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void builddraft$captureAssistedUse(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window == client.getWindow().getHandle()) dev.builddraft.ConstructionAssist.captureMouse(button, action);
    }
    @Shadow @Final private MinecraftClient client;
    @Shadow private boolean cursorLocked;
    @Shadow private boolean hasResolutionChanged;
    @Shadow private double x, y, cursorDeltaX, cursorDeltaY, lastMouseUpdateTime;

    @Override public void builddraft$setCaptured(boolean captured) {
        var window = client.getWindow();
        cursorLocked = captured;
        cursorDeltaX = cursorDeltaY = 0;
        x = window.getWidth() / 2.0; y = window.getHeight() / 2.0;
        hasResolutionChanged = true;
        InputUtil.setCursorParameters(window.getHandle(), captured ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL, x, y);
    }

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void builddraft$lookWithoutTurningPlayer(CallbackInfo ci) {
        if (!(client.currentScreen instanceof DraftEditorScreen editor)) return;
        lastMouseUpdateTime = GlfwUtil.getTime();
        if (editor.mouseLook() && cursorLocked && client.isWindowFocused()) {
            double sensitivity = client.options.getMouseSensitivity().getValue() * .6 + .2;
            double scale = sensitivity * sensitivity * sensitivity * 8 * .15;
            editor.look(cursorDeltaX * scale, cursorDeltaY * scale * (client.options.getInvertYMouse().getValue() ? -1 : 1));
        }
        cursorDeltaX = cursorDeltaY = 0;
        ci.cancel();
    }
}
