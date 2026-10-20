package dev.builddraft;

import dev.builddraft.model.MaterialCounts;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import java.util.*;

public final class MaterialReport {
    public record Report(List<MaterialCounts.Row> rows, Map<String, ItemStack> icons, Map<String, String> names, int unloaded, int manual) { }
    public static Report count(DraftSession draft) {
        var client = MinecraftClient.getInstance();
        List<MaterialCounts.Requirement> needs = new ArrayList<>();
        Map<String, ItemStack> icons = new HashMap<>(); Map<String, String> names = new HashMap<>();
        int unloaded = 0, manual = 0;
        for (var block : draft.plannedBlocks()) {
            var state = block.state(); var pos = draft.worldPos(block.relative());
            boolean loaded = client.world != null && client.world.isChunkLoaded(pos);
            var actual = loaded ? client.world.getBlockState(pos) : Blocks.AIR.getDefaultState();
            if (!loaded) unloaded++;
            int total = ConstructionState.units(state);
            if (total == 0) continue;
            Item item = state.isOf(Blocks.WATER) ? Items.WATER_BUCKET : state.isOf(Blocks.LAVA) ? Items.LAVA_BUCKET : state.getBlock().asItem();
            String id;
            if (item == Items.AIR) {
                id = "manual/" + Registries.BLOCK.getId(state.getBlock()); manual += total;
                icons.put(id, Items.BARRIER.getDefaultStack()); names.put(id, "手工：" + state.getBlock().getName().getString());
            } else {
                id = Registries.ITEM.getId(item).toString(); icons.put(id, item.getDefaultStack()); names.put(id, item.getName().getString());
                if (!(item instanceof BlockItem)) manual += total;
            }
            needs.add(new MaterialCounts.Requirement(id, total, loaded ? ConstructionState.placedUnits(state, actual) : 0));
            if (state.contains(Properties.WATERLOGGED) && state.get(Properties.WATERLOGGED)) {
                String water = Registries.ITEM.getId(Items.WATER_BUCKET).toString();
                icons.put(water, Items.WATER_BUCKET.getDefaultStack()); names.put(water, "水桶（含水，手工）"); manual++;
                needs.add(new MaterialCounts.Requirement(water, 1, loaded && actual.contains(Properties.WATERLOGGED) && actual.get(Properties.WATERLOGGED) ? 1 : 0));
            }
        }
        Map<String, Integer> inventory = new HashMap<>();
        if (client.player != null) {
            var items = new ArrayList<ItemStack>(client.player.getInventory().main); items.addAll(client.player.getInventory().offHand);
            for (ItemStack stack : items) if (!stack.isEmpty()) inventory.merge(Registries.ITEM.getId(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        var rows = new ArrayList<>(MaterialCounts.tally(needs, inventory));
        rows.sort(Comparator.comparing(row -> names.get(row.item())));
        return new Report(List.copyOf(rows), Map.copyOf(icons), Map.copyOf(names), unloaded, manual);
    }
}
