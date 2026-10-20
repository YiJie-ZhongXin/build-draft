package dev.builddraft;

import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.state.property.*;
import net.minecraft.util.math.BlockPos;
import dev.builddraft.model.*;
import dev.builddraft.model.ConstructionRules.Kind;
import java.util.concurrent.ConcurrentHashMap;

/** Compare designed structure while allowing normal neighbour and powered-state updates. */
public final class ConstructionState {
    private static final ConcurrentHashMap<BlockState, BlockSpec> SPECS = new ConcurrentHashMap<>();
    private static BlockSpec spec(BlockState state) { return SPECS.computeIfAbsent(state, BlockStates::describe); }
    private static Kind kind(BlockState state) {
        var block = state.getBlock();
        if (block instanceof StairsBlock) return Kind.STAIRS;
        if (block instanceof FenceBlock || block instanceof WallBlock || block instanceof PaneBlock) return Kind.CONNECTIONS;
        if (block instanceof SlabBlock) return Kind.SLAB;
        if (block instanceof DoorBlock) return Kind.DOOR;
        if (block instanceof TallPlantBlock) return Kind.TALL_PLANT;
        if (block instanceof BedBlock) return Kind.BED;
        return Kind.OTHER;
    }
    public static boolean structureMatches(BlockState expected, BlockState actual) {
        return ConstructionRules.structureMatches(kind(expected), spec(expected), spec(actual));
    }
    public static boolean complete(BlockState expected, BlockState actual) {
        return ConstructionRules.complete(kind(expected), spec(expected), spec(actual));
    }
    public static int units(BlockState state) {
        return ConstructionRules.units(kind(state), spec(state));
    }
    public static int placedUnits(BlockState expected, BlockState actual) {
        return ConstructionRules.placedUnits(kind(expected), spec(expected), spec(actual));
    }
    public static boolean canFill(BlockState expected, BlockState actual) {
        return actual.isReplaceable() || placedUnits(expected, actual) > 0 && placedUnits(expected, actual) < units(expected);
    }
    public static boolean placementMatches(BlockState expected, BlockState placed) {
        return ConstructionRules.placementMatches(kind(expected), spec(expected), spec(placed));
    }
    public static BlockPos primary(BlockState state, BlockPos position) {
        if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TallPlantBlock)
            && state.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return position.down();
        if (state.getBlock() instanceof BedBlock && state.get(Properties.BED_PART) == BedPart.HEAD) return position.offset(state.get(Properties.HORIZONTAL_FACING).getOpposite());
        return position;
    }
}
