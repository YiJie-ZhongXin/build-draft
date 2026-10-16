package dev.builddraft;

import dev.builddraft.model.BlockCategoryRules;
import dev.builddraft.model.BlockCategoryRules.Function;
import dev.builddraft.model.BlockCategoryRules.Type;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import java.util.*;

/** Uses the current creative tabs, tags and block classes; no translated-name guessing. */
public final class BlockCategories {
    public static Map<Block, BlockCategoryRules.Membership> index(List<Block> blocks) {
        var client = MinecraftClient.getInstance();
        Map<Block, Set<Function>> creative = new HashMap<>();
        try {
            if (client.world != null) {
                boolean operator = client.player != null && client.player.hasPermissionLevel(2);
                ItemGroups.updateDisplayContext(client.world.getEnabledFeatures(), operator, client.world.getRegistryManager());
                Map<Function, String> groups = Map.of(Function.BUILDING, "building_blocks", Function.COLORED, "colored_blocks",
                    Function.NATURAL, "natural_blocks", Function.FUNCTIONAL, "functional_blocks", Function.REDSTONE, "redstone_blocks");
                groups.forEach((category, path) -> {
                    var group = Registries.ITEM_GROUP.get(new Identifier("minecraft", path));
                    if (group != null) for (var stack : group.getDisplayStacks()) {
                        if (stack.getItem() instanceof BlockItem item) creative.computeIfAbsent(item.getBlock(), b -> EnumSet.noneOf(Function.class)).add(category);
                    }
                });
            }
        } catch (RuntimeException e) {
            BuildDraftClient.LOGGER.warn("Could not read creative groups; using block traits for palette categories", e);
        }
        Map<Block, BlockCategoryRules.Membership> result = new HashMap<>();
        for (Block block : blocks) {
            var state = block.getDefaultState();
            var types = EnumSet.noneOf(Type.class);
            if (block instanceof StairsBlock || state.isIn(BlockTags.STAIRS)) types.add(Type.STAIRS);
            if (block instanceof SlabBlock || state.isIn(BlockTags.SLABS)) types.add(Type.SLABS);
            if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof WallBlock || state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) types.add(Type.FENCES);
            if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof AbstractGlassBlock || block instanceof PaneBlock || state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS)) types.add(Type.DOORS_GLASS);
            // Check every state: unlit lamps, furnaces and candles must still be discoverable as lights.
            if (block.getStateManager().getStates().stream().anyMatch(s -> s.getLuminance() > 0)) types.add(Type.LIGHTS);
            if (block instanceof AbstractChestBlock<?> || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock || block instanceof HopperBlock || block instanceof AbstractFurnaceBlock || block instanceof DispenserBlock || block instanceof BrewingStandBlock || block instanceof ChiseledBookshelfBlock || block instanceof DecoratedPotBlock) types.add(Type.CONTAINERS);
            if (block instanceof PlantBlock || block instanceof LeavesBlock || block instanceof VineBlock || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SAPLINGS) || state.isIn(BlockTags.LEAVES)) types.add(Type.PLANTS);
            if (block instanceof FluidBlock) types.add(Type.FLUIDS);
            boolean natural = state.isIn(BlockTags.LOGS) || state.isIn(BlockTags.DIRT) || state.isIn(BlockTags.SAND);
            boolean redstone = state.emitsRedstonePower();
            result.put(block, BlockCategoryRules.classify(creative.getOrDefault(block, Set.of()), types, natural, redstone));
        }
        return result;
    }
}
