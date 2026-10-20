package dev.builddraft.model;

import dev.builddraft.model.BlockCategoryRules.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlockCategoryRulesTest {
    private static Membership classify(String id, Trait... traits) { return BlockCategoryRules.classify(id, Set.of(traits)); }
    private static boolean matches(String id, String name, String query, Source source, Membership entry, Material material, Shape shape, boolean special) {
        return BlockCategoryRules.matches(id, name, query, source, entry, material, shape, special);
    }
    @Test void woodAndStairFacetsCombineRatherThanSwitchingModes() {
        var entry = classify("minecraft:oak_stairs", Trait.WOOD, Trait.STAIRS);
        assertEquals(Material.WOOD, entry.material()); assertEquals(Shape.STAIRS, entry.shape());
        assertTrue(matches("minecraft:oak_stairs", "橡木楼梯", "", Source.ALL, entry, Material.WOOD, Shape.STAIRS, false));
        assertFalse(matches("minecraft:oak_stairs", "橡木楼梯", "", Source.ALL, entry, Material.STONE, Shape.STAIRS, false));
        assertFalse(matches("minecraft:oak_stairs", "橡木楼梯", "", Source.ALL, entry, Material.WOOD, Shape.SLABS, false));
    }
    @Test void allMaterialAndShapeSelectionsKeepUnrecognizedModBlocksAccessible() {
        var entry = classify("example:unrecognized_machine");
        assertEquals(Material.OTHER, entry.material()); assertEquals(Shape.OTHER, entry.shape());
        assertTrue(matches("example:unrecognized_machine", "未知方块", "", Source.ALL, entry, Material.ALL, Shape.ALL, false));
    }
    @Test void glassIsSeparatedFromColoredBuildingBlocksAndDoors() {
        var glass = classify("minecraft:red_stained_glass", Trait.GLASS, Trait.COLORED, Trait.FULL);
        assertEquals(Material.GLASS, glass.material());
        assertEquals(Shape.FULL, glass.shape());
        assertEquals(Material.COLORED, classify("minecraft:red_concrete", Trait.FULL).material());
        assertEquals(Material.DECOR, classify("minecraft:iron_door", Trait.DOORS).material());
    }
    @Test void glowingFurnacesAndNaturalIceAreNotGlassOrASeparateLightCategory() {
        assertEquals(Material.DECOR, classify("minecraft:furnace", Trait.DECOR, Trait.STONE, Trait.FULL).material());
        assertEquals(Material.NATURAL, classify("minecraft:ice", Trait.FULL).material());
        assertEquals(Material.DECOR, classify("minecraft:glowstone", Trait.FULL).material());
    }
    @Test void naturalLeavesAndOresDoNotBecomeWoodOrStoneFromTheirNames() {
        assertEquals(Material.NATURAL, classify("minecraft:oak_leaves", Trait.NATURAL).material());
        assertEquals(Material.NATURAL, classify("minecraft:deepslate_iron_ore", Trait.NATURAL, Trait.STONE).material());
        assertEquals(Material.WOOD, classify("minecraft:oak_log", Trait.WOOD).material());
    }
    @Test void tagsAndMaterialHintsSupportUnknownModNames() {
        var entry = classify("example:aurora_stairs", Trait.WOOD, Trait.STONE);
        assertEquals(Material.WOOD, entry.material()); assertEquals(Shape.STAIRS, entry.shape());
        assertEquals(Material.STONE, classify("example:marble_stairs").material());
        assertEquals(Material.WOOD, classify("example:redwood_planks", Trait.STONE).material());
    }
    @Test void woodFamiliesKeepAllStructuralVariantsTogether() {
        for (String variant : List.of("log", "wood", "planks", "stairs", "slab", "fence", "fence_gate", "door", "trapdoor")) {
            assertEquals("oak", classify("minecraft:oak_" + variant, Trait.WOOD).family());
            assertEquals("redwood", classify("example:redwood_" + variant, Trait.WOOD).family());
        }
        assertEquals("redwood", classify("example:stripped_redwood_log", Trait.WOOD).family());
        assertEquals("dark_oak", classify("minecraft:dark_oak_planks", Trait.WOOD).family());
    }
    @Test void stoneFamiliesIncludePolishedBrickAndMossyVariants() {
        assertEquals("deepslate", classify("minecraft:polished_deepslate_stairs", Trait.STONE).family());
        assertEquals("deepslate", classify("minecraft:deepslate_brick_slab", Trait.STONE).family());
        assertEquals("stone", classify("minecraft:mossy_stone_bricks", Trait.STONE).family());
        assertEquals("red_sandstone", classify("minecraft:red_sandstone_stairs", Trait.STONE).family());
        assertEquals("nether_brick", classify("minecraft:nether_bricks", Trait.STONE).family());
    }
    @Test void familySortPlacesOakPlanksAndShapesNextToItsLogs() {
        Map<String, Membership> entries = new HashMap<>();
        for (String id : List.of("minecraft:oak_slab", "minecraft:spruce_planks", "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:oak_log")) entries.put(id, classify(id, Trait.WOOD, Trait.FULL));
        assertEquals(List.of("minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:oak_slab", "minecraft:spruce_planks"), entries.keySet().stream().sorted(BlockCategoryRules.order(entries)).toList());
    }
    @Test void coloredFamiliesUseAConsistentColorSequenceAndKeepGlassPanesTogether() {
        Map<String, Membership> entries = new HashMap<>();
        for (String id : List.of("minecraft:black_stained_glass", "minecraft:white_stained_glass_pane", "minecraft:orange_stained_glass", "minecraft:white_stained_glass")) entries.put(id, classify(id, Trait.GLASS, Trait.FULL));
        assertEquals(List.of("minecraft:white_stained_glass", "minecraft:white_stained_glass_pane", "minecraft:orange_stained_glass", "minecraft:black_stained_glass"), entries.keySet().stream().sorted(BlockCategoryRules.order(entries)).toList());
        assertEquals("wool", classify("minecraft:white_carpet").family());
        assertEquals("terracotta", classify("minecraft:blue_glazed_terracotta").family());
    }
    @Test void specialBlocksAreHiddenByDefaultAndCanBeExplicitlyShown() {
        for (String id : List.of("minecraft:barrier", "minecraft:light", "minecraft:potted_poppy", "minecraft:wall_torch")) {
            var entry = classify(id, Trait.SPECIAL);
            assertFalse(matches(id, "特殊方块", "", Source.ALL, entry, Material.ALL, Shape.ALL, false));
            assertTrue(matches(id, "特殊方块", "", Source.ALL, entry, Material.ALL, Shape.ALL, true));
        }
        assertTrue(classify("minecraft:structure_block").special());
        assertFalse(classify("example:light", Trait.FULL).special());
    }
    @Test void searchSourceAndBothFacetsAllApplyAtTheSameTime() {
        var entry = classify("example:marble_slab", Trait.STONE, Trait.SLABS);
        assertTrue(matches("example:marble_slab", "白色大理石台阶", " 大理石 slab ", Source.MODDED, entry, Material.STONE, Shape.SLABS, false));
        assertFalse(matches("example:marble_slab", "白色大理石台阶", "大理石", Source.VANILLA, entry, Material.STONE, Shape.SLABS, false));
        assertFalse(matches("example:marble_slab", "白色大理石台阶", "大理石 oak", Source.MODDED, entry, Material.STONE, Shape.SLABS, false));
        assertTrue(matches("example:marble_slab", "Marble Slab", "MARBLE", Source.MODDED, entry, Material.ALL, Shape.ALL, false));
    }
    @Test void structuralShapesTakePrecedenceOverFullCubeHints() {
        assertEquals(Shape.STAIRS, classify("example:stairs", Trait.STAIRS, Trait.FULL).shape());
        assertEquals(Shape.FENCES, classify("minecraft:oak_fence_gate", Trait.WOOD).shape());
        assertEquals(Shape.DOORS, classify("minecraft:oak_trapdoor", Trait.WOOD).shape());
        assertEquals(Shape.FULL, classify("example:solid", Trait.FULL).shape());
    }
    @Test void membershipDoesNotDependOnLaterChangesToTraitCollections() {
        var traits = EnumSet.of(Trait.WOOD, Trait.STAIRS);
        var entry = BlockCategoryRules.classify("example:custom", traits);
        traits.clear();
        assertEquals(Material.WOOD, entry.material()); assertEquals(Shape.STAIRS, entry.shape());
    }
}
