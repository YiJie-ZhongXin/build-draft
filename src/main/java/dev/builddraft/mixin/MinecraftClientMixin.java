package dev.builddraft.mixin;

import dev.builddraft.ConstructionAssist;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void builddraft$fillProjection(CallbackInfo ci) {
        if (ConstructionAssist.engaged()) { ConstructionAssist.fill(); ci.cancel(); }
    }
}
