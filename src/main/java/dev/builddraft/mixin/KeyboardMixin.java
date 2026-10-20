package dev.builddraft.mixin;

import dev.builddraft.*;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void builddraft$normalPlayControls(long window, int key, int scan, int action, int mods, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (window != client.getWindow().getHandle()) return;
        ConstructionAssist.captureKey(key, scan, action);
        // L is also vanilla's advancements key. Handle the explicit library binding before that queue.
        if (action == GLFW.GLFW_PRESS && client.currentScreen == null && client.world != null && client.player != null
            && (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0 && DraftKeys.LIBRARY.matchesKey(key, scan)) {
            BuildDraftClient.openLibrary(); ci.cancel();
        }
    }
}
