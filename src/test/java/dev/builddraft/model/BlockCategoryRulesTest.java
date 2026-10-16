package dev.builddraft.model;

import dev.builddraft.model.BlockCategoryRules.*;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class BlockCategoryRulesTest {
    @Test void unknownModBlocksRemainInAllAndOther() {
        var entry = BlockCategoryRules.classify(Set.of(), Set.of(), false, false);
        assertEquals(Set.of(Function.ALL, Function.OTHER), entry.functions());
        assertEquals(Set.of(Type.ALL, Type.OTHER), entry.types());
        assertTrue(BlockCategoryRules.matches("example:machine", "机器", "", Source.MODDED, entry, Function.ALL, Type.ALL));
    }
    @Test void overlappingCreativeGroupsAreRetained() {
        var entry = BlockCategoryRules.classify(Set.of(Function.BUILDING, Function.COLORED), Set.of(), false, false);
        assertTrue(entry.functions().containsAll(Set.of(Function.BUILDING, Function.COLORED)));
        assertFalse(entry.functions().contains(Function.OTHER));
    }
    @Test void customStairsInferBuildingWithoutVanillaCreativeTab() {
        var entry = BlockCategoryRules.classify(Set.of(), Set.of(Type.STAIRS), false, false);
        assertTrue(entry.functions().contains(Function.BUILDING));
        assertTrue(entry.types().contains(Type.STAIRS));
        assertFalse(entry.types().contains(Type.OTHER));
    }
    @Test void combinedTraitsAreNotForcedIntoOneCategory() {
        var entry = BlockCategoryRules.classify(Set.of(), Set.of(Type.CONTAINERS, Type.LIGHTS), false, true);
        assertTrue(entry.functions().containsAll(Set.of(Function.FUNCTIONAL, Function.REDSTONE)));
        assertTrue(entry.types().containsAll(Set.of(Type.CONTAINERS, Type.LIGHTS)));
    }
    @Test void liquidsAndPlantsInferNaturalUses() {
        for (Type type : Set.of(Type.PLANTS, Type.FLUIDS)) {
            var entry = BlockCategoryRules.classify(Set.of(), Set.of(type), false, false);
            assertTrue(entry.functions().contains(Function.NATURAL));
        }
    }
    @Test void searchSourceAndCategoryMustAllMatch() {
        var entry = BlockCategoryRules.classify(Set.of(Function.BUILDING), Set.of(Type.STAIRS), false, false);
        assertTrue(BlockCategoryRules.matches("example:marble_stairs", "大理石楼梯", " 大理石 ", Source.MODDED, entry, Function.BUILDING, Type.STAIRS));
        assertFalse(BlockCategoryRules.matches("example:marble_stairs", "大理石楼梯", "大理石", Source.VANILLA, entry, Function.BUILDING, Type.STAIRS));
        assertFalse(BlockCategoryRules.matches("example:marble_stairs", "大理石楼梯", "大理石", Source.MODDED, entry, Function.REDSTONE, Type.STAIRS));
        assertFalse(BlockCategoryRules.matches("example:marble_stairs", "大理石楼梯", "大理石", Source.MODDED, entry, Function.BUILDING, Type.SLABS));
    }
    @Test void searchesNamesAndIdsCaseInsensitivelyWithVanillaFilter() {
        var entry = BlockCategoryRules.classify(Set.of(Function.BUILDING), Set.of(Type.STAIRS), false, false);
        assertTrue(BlockCategoryRules.matches("minecraft:oak_stairs", "Oak Stairs", "OAK_", Source.VANILLA, entry, Function.ALL, Type.ALL));
        assertTrue(BlockCategoryRules.matches("minecraft:oak_stairs", "Oak Stairs", "STAIRS", Source.ALL, entry, Function.ALL, Type.ALL));
        assertFalse(BlockCategoryRules.matches("minecraft:oak_stairs", "Oak Stairs", "glass", Source.ALL, entry, Function.ALL, Type.ALL));
    }
    @Test void canonicalGroupsTakePrecedenceOverFallbackAndMembershipIsImmutable() {
        var entry = BlockCategoryRules.classify(Set.of(Function.REDSTONE), Set.of(Type.LIGHTS), false, false);
        assertFalse(entry.functions().contains(Function.FUNCTIONAL));
        assertThrows(UnsupportedOperationException.class, () -> entry.functions().add(Function.BUILDING));
    }
}
