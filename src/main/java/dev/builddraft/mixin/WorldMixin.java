package dev.builddraft.mixin;

import dev.builddraft.DraftPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void builddraft$placementState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        var state = DraftPlacement.overlay((World) (Object) this, pos);
        if (state != null) cir.setReturnValue(state);
    }
    @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
    private void builddraft$placementFluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        var state = DraftPlacement.overlay((World) (Object) this, pos);
        if (state != null) cir.setReturnValue(state.getFluidState());
    }
}
