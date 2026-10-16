package dev.builddraft.screen;

import dev.builddraft.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;

public final class BrushScreen extends Screen implements DraftScreen {
    private final DraftEditorScreen parent;
    private final DraftSession draft;
    private String status = "修改当前快捷槽的方块与属性；1–9、0、-、= 选择 12 格建材";
    private int page;
    public BrushScreen(DraftEditorScreen parent, DraftSession draft) { super(Text.literal("选择方块")); this.parent = parent; this.draft = draft; }
    @Override protected void init() {
        int x = width / 2 - 145;
        var field = new DraftUi.Field(textRenderer, x, 40, 225, 20, Text.literal("方块 ID"));
        field.setMaxLength(128);
        field.setText(Registries.BLOCK.getId(draft.brush.getBlock()).toString());
        addDrawableChild(field);
        button("选择", x + 230, 40, 60, () -> {
            try {
                Identifier id = new Identifier(field.getText().trim());
                if (!Registries.BLOCK.containsId(id) || Registries.BLOCK.get(id).getDefaultState().isAir()) throw new IllegalArgumentException("找不到此方块");
                draft.brush = Registries.BLOCK.get(id).getDefaultState();
                page = 0; clearAndInit();
            } catch (Exception e) { status = "选择失败：" + e.getMessage(); }
        });
        var properties = new ArrayList<>(draft.brush.getProperties());
        properties.sort(java.util.Comparator.comparing(Property::getName));
        int count = Math.max(1, (height - 140) / 24);
        for (int i = 0; i < count && i + page * count < properties.size(); i++) {
            var prop = properties.get(i + page * count);
            String value = BlockStates.describe(draft.brush).properties().get(prop.getName());
            button(prop.getName() + " = " + value, x, 73 + i * 24, 290, () -> { draft.brush = draft.brush.cycle(prop); draft.automaticPlacement = false; clearAndInit(); });
        }
        button("上一页", x, height - 53, 80, () -> { page = Math.max(0, page - 1); clearAndInit(); });
        button("下一页", x + 85, height - 53, 80, () -> { if ((page + 1) * count < properties.size()) page++; clearAndInit(); });
        button("完成", x + 210, height - 53, 80, this::close);
    }
    private void button(String text, int x, int y, int w, Runnable action) {
        addDrawableChild(DraftUi.button(x, y, w, text, action));
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext draw, int x, int y, float delta) {
        DraftUi.page(draw, "方块与属性 · " + draft.brush.getBlock().getName().getString(), "", width, height);
        draw.drawText(textRenderer, textRenderer.trimToWidth(status, width - 24), 12, height - 24, DraftUi.MUTED, false);
        super.render(draw, x, y, delta);
    }
}
