package dev.builddraft.render;

import dev.builddraft.DraftSession;
import dev.builddraft.BlockStates;
import dev.builddraft.model.BlockSpec;
import dev.builddraft.model.GridPos;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import java.util.*;

/** Read-only overlay used by preview renderers; never calls World.setBlockState. */
public final class DraftWorldView implements BlockRenderView {
    private final DraftSession draft;
    private final ClientWorld world;
    private final Map<GridPos, BlockState> states = new HashMap<>();
    private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
    public DraftWorldView(DraftSession draft, ClientWorld world) {
        this(draft, world, Map.of());
    }
    public DraftWorldView(DraftSession draft, ClientWorld world, Map<GridPos, BlockSpec> additions) {
        this.draft = draft; this.world = world;
        draft.plannedBlocks().forEach(p -> states.put(p.relative(), p.state()));
        additions.forEach((p, spec) -> states.put(p, BlockStates.resolve(spec)));
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var key = new GridPos(pos.getX() - draft.origin.getX(), pos.getY() - draft.origin.getY(), pos.getZ() - draft.origin.getZ());
        return states.getOrDefault(key, world.getBlockState(pos));
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return entities.computeIfAbsent(pos.toImmutable(), key -> {
            var state = getBlockState(key);
            if (state.getBlock() instanceof BlockEntityProvider provider) {
                var entity = provider.createBlockEntity(key, state);
                if (entity != null) entity.setWorld(world);
                return entity;
            }
            return null;
        });
    }
    @Override public float getBrightness(Direction direction, boolean shaded) { return world.getBrightness(direction, shaded); }
    @Override public LightingProvider getLightingProvider() { return world.getLightingProvider(); }
    @Override public int getColor(BlockPos pos, ColorResolver resolver) { return world.getColor(pos, resolver); }
    @Override public int getHeight() { return world.getHeight(); }
    @Override public int getBottomY() { return world.getBottomY(); }
}
