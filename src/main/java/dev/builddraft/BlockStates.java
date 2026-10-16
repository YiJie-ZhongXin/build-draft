package dev.builddraft;

import dev.builddraft.model.BlockSpec;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import java.util.TreeMap;

public final class BlockStates {
    public static BlockSpec describe(BlockState state) {
        var properties = new TreeMap<String, String>();
        state.getEntries().forEach((key, value) -> properties.put(key.getName(), valueName(key, value)));
        return new BlockSpec(Registries.BLOCK.getId(state.getBlock()).toString(), properties);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(Property key, Comparable value) { return key.name(value); }

    public static BlockState resolve(BlockSpec spec) {
        Identifier id = new Identifier(spec.id());
        if (!Registries.BLOCK.containsId(id)) throw new IllegalArgumentException("缺少方块：" + id);
        var block = Registries.BLOCK.get(id);
        BlockState state = block.getDefaultState();
        if (state.isAir()) throw new IllegalArgumentException("蓝图不能包含空气方块");
        for (var entry : spec.properties().entrySet()) {
            Property<?> property = block.getStateManager().getProperty(entry.getKey());
            if (property == null) throw new IllegalArgumentException("未知属性：" + entry.getKey());
            state = with(state, property, entry.getValue());
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        T parsed = property.parse(value).orElseThrow(() -> new IllegalArgumentException("无效属性：" + property.getName() + "=" + value));
        return state.with(property, parsed);
    }
}
