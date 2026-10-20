package dev.builddraft;

import dev.builddraft.model.MaterialFillPlan;
import net.minecraft.block.*;
import net.minecraft.block.enums.*;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import java.util.*;

/** Authoritative, synchronous whole-material fill. Called only on the integrated server thread. */
public final class ConstructionFill {
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;
    public record Target(BlockPos position, BlockState state) {
        public Target { position = position.toImmutable(); }
    }
    private record Group(List<Target> targets, Map<BlockPos, BlockState> before) { }

    public static boolean usable(ItemStack stack, Item material) {
        return !stack.isEmpty() && stack.getItem() == material && stack.getSubNbt(BlockItem.BLOCK_STATE_TAG_KEY) == null
            && stack.getSubNbt("BlockEntityTag") == null;
    }
    private static List<ItemStack> inventory(ServerPlayerEntity player, Item item) {
        List<ItemStack> stacks = new ArrayList<>();
        for (var stack : player.getInventory().main) if (usable(stack, item)) stacks.add(stack);
        for (var stack : player.getInventory().offHand) if (usable(stack, item)) stacks.add(stack);
        return stacks;
    }
    private static void consume(List<ItemStack> stacks, int amount) {
        for (var stack : stacks) {
            int taken = Math.min(amount, stack.getCount());
            stack.decrement(taken); amount -= taken;
            if (amount == 0) return;
        }
        if (amount != 0) throw new IllegalStateException("Material budget changed during server fill");
    }
    public static String apply(ServerPlayerEntity player, Item item, List<Target> snapshot) {
        var world = player.getServerWorld();
        if (!world.getServer().isOnThread()) throw new IllegalStateException("Fill must run on the server thread");
        if (!player.isAlive() || player.isSpectator() || !player.getAbilities().allowModifyWorld)
            return "当前玩家状态或游戏模式不能填充";
        if (!usable(player.getMainHandStack(), item)) return "主手材料已变化，未执行填充";
        boolean creative = player.isCreative();
        var stacks = inventory(player, item);
        int available = stacks.stream().mapToInt(ItemStack::getCount).sum();
        Map<BlockPos, BlockState> blueprint = new LinkedHashMap<>();
        for (var target : snapshot) if (target.state().getBlock().asItem() == item) blueprint.put(target.position(), target.state());
        List<MaterialFillPlan.Candidate<Group>> pending = new ArrayList<>();
        int skipped = 0;
        for (var entry : blueprint.entrySet()) {
            BlockPos pos = entry.getKey(); BlockState expected = entry.getValue();
            if (ConstructionState.units(expected) == 0) {
                if (!blueprint.containsKey(ConstructionState.primary(expected, pos))) skipped++;
                continue;
            }
            var targets = structure(pos, expected, blueprint);
            if (targets.isEmpty()) { skipped++; continue; }
            Map<BlockPos, BlockState> before = new LinkedHashMap<>();
            boolean valid = true, incomplete = false;
            for (var target : targets) {
                BlockPos at = target.position();
                if (at.getY() < world.getBottomY() || at.getY() >= world.getTopY() || !world.getWorldBorder().contains(at)
                    || !world.canPlayerModifyAt(player, at)) { valid = false; break; }
                // Load every needed chunk, including distant targets. No reach, raycast or per-tick queue.
                world.getChunk(at.getX() >> 4, at.getZ() >> 4);
                BlockState actual = world.getBlockState(at);
                before.put(at, actual);
                if (ConstructionState.structureMatches(target.state(), actual)) continue;
                incomplete = true;
                if (!ConstructionState.canFill(target.state(), actual) || world.getBlockEntity(at) != null
                    || !world.canPlace(target.state(), at, ShapeContext.absent())) { valid = false; break; }
            }
            if (!valid) { skipped++; continue; }
            if (!incomplete) continue;
            int cost = Math.max(0, ConstructionState.units(expected) - ConstructionState.placedUnits(expected, before.get(pos)));
            pending.add(new MaterialFillPlan.Candidate<>(new Group(List.copyOf(targets), Map.copyOf(before)), cost));
        }
        var plan = MaterialFillPlan.select(pending, available, creative);
        int filled = 0, used = 0;
        List<Target> changed = new ArrayList<>();
        try {
            for (var candidate : plan.selected()) {
                var group = candidate.target();
                // Block callbacks can affect neighbours even within a single server task.
                if (group.before().entrySet().stream().anyMatch(e -> world.getBlockState(e.getKey()) != e.getValue())) { skipped++; continue; }
                List<Target> installed = install(world, group);
                if (installed == null) { skipped++; continue; }
                if (!creative) { consume(stacks, candidate.cost()); used += candidate.cost(); }
                filled += installed.size(); changed.addAll(installed);
            }
        } finally {
            player.getInventory().markDirty();
            player.currentScreenHandler.sendContentUpdates();
            // All structures are already present before normal redstone / physics neighbours are notified.
            for (var target : changed) world.updateNeighbors(target.position(), target.state().getBlock());
        }
        int missing = Math.max(0, plan.missing() - (plan.used() - used));
        return "已填充 " + filled + " 格 · " + (creative ? "创造模式不扣料" : "消耗 " + used + " 个")
            + (missing > 0 ? " · 缺料 " + missing + " 个，补料后再按一次" : "")
            + (skipped > 0 ? " · 跳过 " + skipped + " 组冲突 / 占位 / 不完整结构" : "")
            + (filled == 0 && pending.isEmpty() && skipped == 0 ? " · 该材料已全部完成" : "");
    }

    /** Doors / beds are one atomic structure, consuming their item only once. */
    private static List<Target> structure(BlockPos pos, BlockState state, Map<BlockPos, BlockState> blueprint) {
        BlockPos otherPos = null; BlockState otherState = null;
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TallPlantBlock) {
            otherPos = pos.up(); otherState = state.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        } else if (state.getBlock() instanceof BedBlock) {
            otherPos = pos.offset(state.get(Properties.HORIZONTAL_FACING)); otherState = state.with(Properties.BED_PART, BedPart.HEAD);
        }
        if (otherPos == null) return List.of(new Target(pos, state));
        var designed = blueprint.get(otherPos);
        if (designed == null || !ConstructionState.structureMatches(otherState, designed)) return List.of();
        return List.of(new Target(pos, state), new Target(otherPos, designed));
    }
    private static List<Target> install(ServerWorld world, Group group) {
        List<Target> changed = new ArrayList<>();
        boolean success = false;
        try {
            for (var target : group.targets()) {
                var actual = group.before().get(target.position());
                if (ConstructionState.structureMatches(target.state(), actual)) continue;
                BlockState state = target.state();
                // Material fill never creates free water or removes water from an existing waterlogged block.
                if (state.contains(Properties.WATERLOGGED)) state = state.with(Properties.WATERLOGGED, actual.getFluidState().isOf(Fluids.WATER));
                if (!world.setBlockState(target.position(), state, FLAGS) || world.getBlockState(target.position()) != state) return null;
                changed.add(new Target(target.position(), state));
            }
            success = true;
            return changed;
        } finally {
            if (!success) for (var entry : group.before().entrySet()) world.setBlockState(entry.getKey(), entry.getValue(), FLAGS);
        }
    }
}
