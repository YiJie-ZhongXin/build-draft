package dev.builddraft.mixin;

import dev.builddraft.BuildDraftClient;
import dev.builddraft.DraftRenderer;
import dev.builddraft.screen.DraftScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void builddraft$noPlayerHandInFreeCamera(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof DraftScreen) ci.cancel();
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V", shift = At.Shift.AFTER))
    private void builddraft$guidesAfterWorld(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        DraftRenderer.renderGuides();
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void builddraft$fixedFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (BuildDraftClient.editing()) cir.setReturnValue(60.0);
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void builddraft$stableCamera(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (BuildDraftClient.editing()) ci.cancel();
    }

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void builddraft$stablePicking(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (BuildDraftClient.editing()) ci.cancel();
    }
}
