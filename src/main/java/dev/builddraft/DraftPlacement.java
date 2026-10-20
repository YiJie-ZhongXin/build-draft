package dev.builddraft;

import dev.builddraft.model.GridPos;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import java.util.Arrays;

/** Runs vanilla state selection against a scoped, read-only blueprint overlay. */
public final class DraftPlacement {
    public record Result(GridPos position, BlockState state) { }
    private static final ThreadLocal<DraftSession> ACTIVE = new ThreadLocal<>();

    // Called only by World reads during getPlacementState. Never redirects writes or server reads.
    public static BlockState overlay(World world, BlockPos pos) {
        DraftSession draft = ACTIVE.get();
        if (draft == null || world != MinecraftClient.getInstance().world) return null;
        var spec = draft.blueprint.blocks().get(new GridPos(pos.getX() - draft.origin.getX(), pos.getY() - draft.origin.getY(), pos.getZ() - draft.origin.getZ()));
        return spec == null ? null : BlockStates.resolve(spec);
    }

    public static Result resolve(DraftSession draft, DraftPicker.Selection selection) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || selection == null) return null;
        BlockHitResult hit = new BlockHitResult(selection.point(), Direction.valueOf(selection.face().name()), draft.worldPos(selection.block()), false);
        ACTIVE.set(draft);
        try {
            var context = new Context(draft, hit);
            BlockPos pos = context.getBlockPos();
            GridPos relative = new GridPos(pos.getX() - draft.origin.getX(), pos.getY() - draft.origin.getY(), pos.getZ() - draft.origin.getZ());
            if (!draft.validPosition(relative)) return null;
            if (!context.canPlace()) return null;
            BlockState state = draft.automaticPlacement ? draft.brush.getBlock().getPlacementState(context) : draft.brush;
            return state == null ? null : new Result(relative, state);
        } catch (RuntimeException e) {
            // A modded block that cannot resolve a preview must not corrupt the current edit.
            return null;
        } finally { ACTIVE.remove(); }
    }

    private static final class Context extends ItemPlacementContext {
        private final DraftSession draft;
        Context(DraftSession draft, BlockHitResult hit) {
            super(MinecraftClient.getInstance().player, Hand.MAIN_HAND, new ItemStack(draft.brush.getBlock()), hit);
            this.draft = draft;
        }
        @Override public Direction getHorizontalPlayerFacing() { return Direction.fromRotation(draft.yaw); }
        @Override public float getPlayerYaw() { return draft.yaw; }
        @Override public Direction getPlayerLookDirection() { return lookDirections()[0]; }
        @Override public Direction getVerticalPlayerLookDirection() { return draft.pitch >= 0 ? Direction.DOWN : Direction.UP; }
        private Direction[] lookDirections() {
            Vec3d look = Vec3d.fromPolar(draft.pitch, draft.yaw);
            Direction[] directions = Direction.values().clone();
            Arrays.sort(directions, java.util.Comparator.comparingDouble((Direction d) -> -look.dotProduct(Vec3d.of(d.getVector()))));
            return directions;
        }
        @Override public Direction[] getPlacementDirections() {
            Direction[] directions = lookDirections();
            if (!canReplaceExisting()) {
                Direction first = getSide().getOpposite();
                int index = java.util.Arrays.asList(directions).indexOf(first);
                System.arraycopy(directions, 0, directions, 1, index);
                directions[0] = first;
            }
            return directions;
        }
    }
}
