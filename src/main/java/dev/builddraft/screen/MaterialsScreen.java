package dev.builddraft.screen;

import dev.builddraft.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

public final class MaterialsScreen extends Screen implements DraftScreen {
    private final DraftEditorScreen parent;
    private final DraftSession draft;
    private final Map<String, int[]> materials = new TreeMap<>();
    private List<String> rows = List.of();
    private int page, matched, conflict, unloaded, tick;
    public MaterialsScreen(DraftEditorScreen parent, DraftSession draft) { super(Text.literal("材料清单")); this.parent = parent; this.draft = draft; }

    private void refresh() {
        materials.clear(); matched = 0; conflict = 0; unloaded = 0;
        for (var planned : draft.plannedBlocks()) {
            var pos = draft.worldPos(planned.relative());
            boolean loaded = client.world.isChunkLoaded(pos);
            var actual = client.world.getBlockState(pos);
            boolean done = loaded && actual.equals(planned.state());
            String id = net.minecraft.registry.Registries.BLOCK.getId(planned.state().getBlock()).toString();
            String label = planned.state().getBlock().getName().getString() + " · " + id;
            int[] count = materials.computeIfAbsent(label, ignored -> new int[2]);
            count[0]++;
            if (!done) count[1]++;
            if (done) matched++;
            else if (!loaded) unloaded++;
            else if (!actual.isReplaceable()) conflict++;
        }
        rows = materials.entrySet().stream().map(e -> e.getKey() + "   总 " + e.getValue()[0] + " / 未完成 " + e.getValue()[1]).toList();
    }

    @Override protected void init() {
        refresh();
        addDrawableChild(DraftUi.button(width / 2 + 55, height - 30, 90, "返回", this::close));
        addDrawableChild(DraftUi.button(width / 2 - 145, height - 30, 70, "上一页", () -> page = Math.max(0, page - 1)));
        addDrawableChild(DraftUi.button(width / 2 - 70, height - 30, 70, "下一页", () -> { if ((page + 1) * pageSize() < rows.size()) page++; }));
    }
    private int pageSize() { return Math.max(1, (height - 118) / 16); }
    @Override public void tick() { if (++tick % 20 == 0) refresh(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        DraftUi.page(draw, "材料清单 · 按方块格数统计", "", width, height);
        DraftUi.centered(draw, "总计 " + draft.blueprint.blocks().size() + " | 完成 " + matched + " | 冲突 " + conflict + " | 未加载 " + unloaded, width / 2, 33, DraftUi.TEXT);
        DraftUi.centered(draw, "未完成包含朝向不符；门、床等多格结构不等同于物品消耗量", width / 2, 49, DraftUi.MUTED);
        for (int i = 0; i < pageSize() && page * pageSize() + i < rows.size(); i++) {
            draw.drawText(textRenderer, textRenderer.trimToWidth(rows.get(page * pageSize() + i), width - 40), 20, 72 + i * 16, DraftUi.TEXT, false);
        }
        super.render(draw, mouseX, mouseY, delta);
    }
}
