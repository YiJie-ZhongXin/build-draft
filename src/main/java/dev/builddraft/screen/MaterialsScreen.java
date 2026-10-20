package dev.builddraft.screen;

import dev.builddraft.DraftSession;
import dev.builddraft.MaterialReport;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Live counts of saved item requirements, real placed blocks and the player's usable inventory. */
public final class MaterialsScreen extends Screen implements DraftScreen {
    private final Screen parent;
    private final String name;
    private final DraftSession snapshot;
    private MaterialReport.Report report;
    private int left, top, panelWidth, panelHeight, page, pageSize, ticks;
    private net.minecraft.client.gui.widget.ButtonWidget previous, next;
    public MaterialsScreen(Screen parent, String name, DraftSession snapshot) {
        super(Text.literal("材料统计")); this.parent = parent; this.name = name; this.snapshot = snapshot;
    }
    @Override protected void init() {
        panelWidth = Math.min(450, width - 12); panelHeight = Math.min(330, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        pageSize = Math.max(1, (panelHeight - 116) / 22);
        previous = addDrawableChild(DraftUi.button(left + 12, top + panelHeight - 32, 60, "上一页", () -> { page--; refresh(); }));
        next = addDrawableChild(DraftUi.button(left + 76, top + panelHeight - 32, 60, "下一页", () -> { page++; refresh(); }));
        addDrawableChild(DraftUi.button(left + panelWidth - 72, top + panelHeight - 32, 60, "返回", this::close));
        refresh();
    }
    private void refresh() {
        report = MaterialReport.count(snapshot);
        page = Math.max(0, Math.min(page, Math.max(0, (report.rows().size() - 1) / pageSize)));
        previous.active = page > 0; next.active = (page + 1) * pageSize < report.rows().size();
    }
    @Override public void tick() { if (++ticks % 10 == 0) refresh(); }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (amount != 0) { page += amount > 0 ? -1 : 1; refresh(); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    private void number(DrawContext draw, String text, int column, int y, int color) {
        int right = left + panelWidth - 12 - (4 - column) * 35;
        draw.drawText(textRenderer, text, right - textRenderer.getWidth(text), y, color, false);
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, DraftUi.BG); DraftUi.panel(draw, left, top, panelWidth, panelHeight);
        draw.drawText(textRenderer, textRenderer.trimToWidth("材料 · " + name, panelWidth - 24), left + 12, top + 12, DraftUi.TEXT, false);
        draw.drawText(textRenderer, "方块 / 物品", left + 12, top + 36, DraftUi.MUTED, false);
        String[] headings = {"总需", "完成", "待放", "背包", "缺口"};
        for (int i = 0; i < headings.length; i++) number(draw, headings[i], i, top + 36, DraftUi.MUTED);
        for (int i = 0; i < pageSize && page * pageSize + i < report.rows().size(); i++) {
            var row = report.rows().get(page * pageSize + i); int y = top + 53 + i * 22;
            if (i % 2 == 0) draw.fill(left + 10, y - 2, left + panelWidth - 10, y + 18, 0x55e8ddc4);
            draw.drawItem(report.icons().get(row.item()), left + 12, y - 1);
            draw.drawText(textRenderer, textRenderer.trimToWidth(report.names().get(row.item()), panelWidth - 218), left + 32, y + 3, DraftUi.TEXT, false);
            int[] counts = {row.total(), row.completed(), row.remaining(), row.available(), row.missing()};
            for (int c = 0; c < counts.length; c++) number(draw, Integer.toString(counts[c]), c, y + 3, c == 4 && counts[c] > 0 ? 0xffa04732 : DraftUi.TEXT);
        }
        if (report.rows().isEmpty()) draw.drawCenteredTextWithShadow(textRenderer, "该草稿没有材料需求", width / 2, top + 62, DraftUi.MUTED);
        String note = report.unloaded() > 0 ? "未加载 " + report.unloaded() + " 格，完成数按未建计；" : "按实际物品合并统计；";
        note += report.manual() > 0 ? "含水 / 特殊材料需手工处理" : "背包含主物品栏及副手";
        draw.drawText(textRenderer, textRenderer.trimToWidth(note, panelWidth - 24), left + 12, top + panelHeight - 49, DraftUi.MUTED, false);
        draw.drawText(textRenderer, (page + 1) + "/" + Math.max(1, (report.rows().size() + pageSize - 1) / pageSize), left + 144, top + panelHeight - 26, DraftUi.TEXT, false);
        super.render(draw, mouseX, mouseY, delta);
    }
}
