package dev.builddraft;

import dev.builddraft.model.BlockCategoryRules;
import dev.builddraft.model.BlockCategoryRules.Trait;
import net.minecraft.block.*;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import java.util.*;

/** Reads structural tags, block classes and sound materials; names are only a fallback in the policy. */
public final class BlockCategories {
    private static final Set<String> WOOD_TAGS = Set.of("logs", "planks", "wooden_stairs", "wooden_slabs", "wooden_fences", "wooden_doors", "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates");
    private static final Set<String> NATURAL_TAGS = Set.of("leaves", "flowers", "saplings", "dirt", "sand", "coal_ores", "iron_ores", "copper_ores", "gold_ores", "redstone_ores", "lapis_ores", "emerald_ores", "diamond_ores");
    private static final Set<String> COLORED_TAGS = Set.of("wool", "wool_carpets", "terracotta");
    private static final Set<BlockSoundGroup> WOOD_SOUNDS = Set.of(BlockSoundGroup.WOOD, BlockSoundGroup.NETHER_WOOD, BlockSoundGroup.BAMBOO_WOOD, BlockSoundGroup.CHERRY_WOOD);
    private static final Set<BlockSoundGroup> STONE_SOUNDS = Set.of(BlockSoundGroup.STONE, BlockSoundGroup.DEEPSLATE, BlockSoundGroup.DEEPSLATE_BRICKS, BlockSoundGroup.DEEPSLATE_TILES, BlockSoundGroup.POLISHED_DEEPSLATE, BlockSoundGroup.BASALT, BlockSoundGroup.TUFF, BlockSoundGroup.CALCITE, BlockSoundGroup.GILDED_BLACKSTONE, BlockSoundGroup.LODESTONE);

    public static Map<Block, BlockCategoryRules.Membership> index(List<Block> blocks) {
        Map<Block, BlockCategoryRules.Membership> result = new HashMap<>();
        Map<String, Set<String>> woodFamilies = new HashMap<>();
        for (Block block : blocks) {
            var id = Registries.BLOCK.getId(block);
            if (id.getPath().endsWith("_planks")) woodFamilies.computeIfAbsent(id.getNamespace(), key -> new HashSet<>())
                .add(id.getPath().substring(0, id.getPath().length() - "_planks".length()));
        }
        for (Block block : blocks) {
            var state = block.getDefaultState();
            var traits = EnumSet.noneOf(Trait.class);
            Set<String> tags = new HashSet<>();
            state.streamTags().forEach(tag -> { tags.add(tag.id().getPath()); });
            var sound = state.getSoundGroup();
            var id = Registries.BLOCK.getId(block);
            String path = id.getPath().replaceFirst("^stripped_", "");
            boolean woodFamily = woodFamilies.getOrDefault(id.getNamespace(), Set.of()).stream().anyMatch(root -> path.equals(root) || path.startsWith(root + "_"));
            if (!Collections.disjoint(tags, WOOD_TAGS) || WOOD_SOUNDS.contains(sound) || woodFamily) traits.add(Trait.WOOD);
            if (STONE_SOUNDS.contains(sound)) traits.add(Trait.STONE);
            if (!Collections.disjoint(tags, COLORED_TAGS)) traits.add(Trait.COLORED);
            if (block instanceof AbstractGlassBlock || block instanceof PaneBlock && sound == BlockSoundGroup.GLASS) traits.add(Trait.GLASS);
            if (!Collections.disjoint(tags, NATURAL_TAGS) || block instanceof PlantBlock || block instanceof LeavesBlock || block instanceof VineBlock || block instanceof FluidBlock) traits.add(Trait.NATURAL);
            if (block instanceof BlockEntityProvider || block instanceof TorchBlock || block instanceof LanternBlock || block instanceof LeverBlock || block instanceof AbstractPressurePlateBlock || block instanceof ButtonBlock || block instanceof RedstoneWireBlock) traits.add(Trait.DECOR);
            if (block instanceof StairsBlock || tags.contains("stairs")) traits.add(Trait.STAIRS);
            if (block instanceof SlabBlock || tags.contains("slabs")) traits.add(Trait.SLABS);
            if (block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof WallBlock || tags.contains("fences") || tags.contains("walls")) traits.add(Trait.FENCES);
            if (block instanceof DoorBlock || block instanceof TrapdoorBlock || tags.contains("doors") || tags.contains("trapdoors")) traits.add(Trait.DOORS);
            try { if (state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) traits.add(Trait.FULL); }
            catch (RuntimeException ignored) { /* Unknown mod shapes remain available under all shapes. */ }
            var item = block.asItem();
            if (item == Items.AIR || item instanceof BlockItem blockItem && blockItem.getBlock() != block) traits.add(Trait.SPECIAL);
            result.put(block, BlockCategoryRules.classify(Registries.BLOCK.getId(block).toString(), traits));
        }
        return result;
    }
}
