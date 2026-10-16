package dev.builddraft.screen;

import dev.builddraft.*;
import dev.builddraft.io.BlueprintFiles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.List;

public final class LibraryScreen extends Screen implements DraftScreen {
    private final DraftEditorScreen parent;
    private final DraftSession draft;
    private TextFieldWidget name;
    private List<String> files = List.of();
    private int page;
    private String status = "命名保存蓝图，或载入已有设计；使用原点按键重新定位";
    public LibraryScreen(DraftEditorScreen parent, DraftSession draft) { super(Text.literal("蓝图库")); this.parent = parent; this.draft = draft; }

    @Override protected void init() {
        int x = width / 2 - 145;
        try { files = BuildDraftClient.LIBRARY.list(); }
        catch (Exception e) { status = e.getMessage(); }
        name = new DraftUi.Field(textRenderer, x, 39, 180, 20, Text.literal("蓝图名称"));
        name.setMaxLength(48); name.setText(draft.name);
        name.setChangedListener(value -> draft.name = value);
        addDrawableChild(name);
        button("保存", x + 186, 39, 50, () -> {
            try {
                String value = name.getText().trim();
                if (BuildDraftClient.LIBRARY.exists(value)) confirm("覆盖同名蓝图？", () -> save(value));
                else save(value);
            } catch (Exception e) { status = e.getMessage(); }
        });
        button("返回", x + 242, 39, 48, this::close);
        int count = pageSize();
        page = Math.min(page, Math.max(0, (files.size() - 1) / count));
        for (int i = 0; i < count && page * count + i < files.size(); i++) {
            String filename = files.get(page * count + i);
            button("载入 · " + filename, x, 73 + i * 23, 290, () -> requestLoad(BuildDraftClient.LIBRARY, filename));
        }
        button("上一页", x, height - 56, 70, () -> { page = Math.max(0, page - 1); clearAndInit(); });
        button("下一页", x + 76, height - 56, 70, () -> { if ((page + 1) * count < files.size()) page++; clearAndInit(); });
        button("恢复自动保存", x + 152, height - 56, 138, () -> requestLoad(BuildDraftClient.autosaves(), "recovery"));
    }

    private int pageSize() { return Math.max(1, (height - 140) / 23); }
    private void button(String text, int x, int y, int w, Runnable action) {
        addDrawableChild(DraftUi.button(x, y, w, text, action));
    }
    private void confirm(String title, Runnable action) {
        client.setScreen(new DraftConfirmation(yes -> { client.setScreen(this); if (yes) action.run(); }, Text.literal(title), Text.literal("需要保留旧内容时，请先使用另一个名称保存。")));
    }
    private void save(String value) {
        try { BuildDraftClient.LIBRARY.save(value, draft.blueprint); status = "已保存：" + value; clearAndInit(); }
        catch (Exception e) { status = "保存失败：" + e.getMessage(); }
    }
    private void requestLoad(BlueprintFiles source, String value) {
        if (source == null) { status = "没有可恢复的草稿"; return; }
        Runnable action = () -> {
            try {
                var loaded = source.load(value);
                draft.load(loaded);
                BuildDraftClient.markUnsaved();
                status = "已载入 " + loaded.blocks().size() + " 格：" + value;
                if (!"recovery".equals(value)) { draft.name = value; name.setText(value); }
            } catch (Exception e) { status = "载入失败：" + e.getMessage(); }
        };
        if (!draft.blueprint.blocks().isEmpty()) confirm("替换当前草稿？", action); else action.run();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        DraftUi.page(draw, "筑稿 · 蓝图库", "", width, height);
        draw.drawText(textRenderer, textRenderer.trimToWidth(status, width - 24), 12, height - 25, DraftUi.MUTED, false);
        if (files.isEmpty()) DraftUi.centered(draw, "还没有蓝图，输入名称后保存第一份草稿", width / 2, 90, DraftUi.MUTED);
        super.render(draw, mouseX, mouseY, delta);
    }

    private static final class DraftConfirmation extends ConfirmScreen implements DraftScreen {
        DraftConfirmation(it.unimi.dsi.fastutil.booleans.BooleanConsumer callback, Text title, Text message) { super(callback, title, message); }
        @Override public boolean shouldPause() { return false; }
    }
}
