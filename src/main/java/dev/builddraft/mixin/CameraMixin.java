package dev.builddraft.mixin;

import dev.builddraft.BuildDraftClient;
import dev.builddraft.model.OrbitMath;
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
    private void builddraft$overhead(BlockView area, Entity entity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        var draft = BuildDraftClient.session();
        if (!BuildDraftClient.editing() || draft == null) return;
        this.thirdPerson = true;
        setRotation(draft.yaw, draft.pitch);
        var eye = OrbitMath.eye(draft.focusX, draft.focusY, draft.focusZ, draft.zoom, draft.yaw, draft.pitch);
        double shift = draft.viewportOffset * draft.zoom * Math.tan(Math.toRadians(30));
        setPos(eye.x() + Math.cos(Math.toRadians(draft.yaw)) * shift, eye.y(), eye.z() + Math.sin(Math.toRadians(draft.yaw)) * shift);
    }
}
