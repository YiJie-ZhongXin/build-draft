package dev.builddraft.mixin;

import dev.builddraft.screen.DraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void builddraft$constructionHint(DrawContext context, float tickDelta, CallbackInfo ci) {
        dev.builddraft.ConstructionAssist.hud(context);
    }
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void builddraft$editorCanvas(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof DraftScreen) ci.cancel();
    }
}
