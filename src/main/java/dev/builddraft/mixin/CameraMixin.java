package dev.builddraft.mixin;

import dev.builddraft.BuildDraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPos(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At("TAIL"))
    private void builddraft$freeCamera(BlockView area, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        var draft = BuildDraftClient.session();
        if (!BuildDraftClient.editing() || draft == null) return;
        this.thirdPerson = false;
        setRotation(draft.yaw, draft.pitch);
        setPos(MathHelper.lerp(tickDelta, draft.previousCameraX, draft.cameraX),
            MathHelper.lerp(tickDelta, draft.previousCameraY, draft.cameraY),
            MathHelper.lerp(tickDelta, draft.previousCameraZ, draft.cameraZ));
    }
}
