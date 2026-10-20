package dev.builddraft.screen;

import dev.builddraft.BuildDraftClient;
import dev.builddraft.DraftSession;
import dev.builddraft.ConstructionProjection;
import dev.builddraft.io.ProjectionFiles.Mode;
import dev.builddraft.io.DraftLibrary;
import dev.builddraft.model.Blueprint;
import dev.builddraft.model.GridPos;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Named drafts for the current world; destructive actions are confirmed inside the page. */
public final class DraftLibraryScreen extends Screen implements DraftScreen {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private final Screen parent;
    private final DraftSession draft;
    private final DraftLibrary library;
    private final List<Row> rows = new ArrayList<>();
    private List<DraftLibrary.Entry> entries = List.of();
    private String selected = "", pending = "", status = "命名保存多个草稿；切换前自动备份未保存内容";
    private String typedName = "草稿 " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    private int left, top, panelWidth, panelHeight, page, pageSize, listTop;
    private TextFieldWidget name;
    private ButtonWidget previous, next, load, update, delete, back;
    private ButtonWidget projection, materials, stopProjection;
    private boolean readyForTyping;

    public DraftLibraryScreen(Screen parent, DraftSession draft, DraftLibrary library) {
        super(Text.literal("草稿管理")); this.parent = parent; this.draft = draft; this.library = library;
    }
    @Override protected void init() {
        readyForTyping = false; rows.clear();
        panelWidth = Math.min(420, width - 12); panelHeight = Math.min(330, height - 12);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        name = new DraftUi.Field(textRenderer, left + 12, top + 49, panelWidth - 120, 20, Text.literal("新草稿名称"));
        name.setMaxLength(48); name.setText(typedName);
        name.setChangedListener(value -> typedName = value);
        addDrawableChild(name);
        addDrawableChild(DraftUi.button(left + panelWidth - 102, top + 49, 90, "保存为新草稿", this::saveNew));
        listTop = top + 78;
        pageSize = Math.max(1, (panelHeight - 200) / 34);
        int projectY = top + panelHeight - 112;
        int projectWidth = (panelWidth - 32) / 3;
        projection = addDrawableChild(new DraftUi.Button(left + 12, projectY, projectWidth, 20,
            () -> "投影：" + ConstructionProjection.mode(selected).label, () -> ConstructionProjection.selected(selected), this::toggleProjection));
        materials = addDrawableChild(DraftUi.button(left + 16 + projectWidth, projectY, projectWidth, "材料统计", this::materials));
        stopProjection = addDrawableChild(DraftUi.button(left + 20 + projectWidth * 2, projectY, projectWidth, "关闭施工投影", this::stopProjection));
        int toolsY = top + panelHeight - 84;
        previous = addDrawableChild(DraftUi.button(left + 12, toolsY, 54, "上一页", () -> turnPage(-1)));
        next = addDrawableChild(DraftUi.button(left + 70, toolsY, 54, "下一页", () -> turnPage(1)));
        addDrawableChild(DraftUi.button(left + panelWidth - 140, toolsY, 62, "刷新", this::refresh));
        addDrawableChild(DraftUi.button(left + panelWidth - 74, toolsY, 62, "新建空白", this::newBlank));
        int buttonWidth = (panelWidth - 36) / 4, actionsY = top + panelHeight - 55;
        load = addDrawableChild(DraftUi.button(left + 12, actionsY, buttonWidth, "载入编辑", this::loadSelected));
        update = addDrawableChild(new DraftUi.Button(left + 16 + buttonWidth, actionsY, buttonWidth, 20,
            () -> pending.equals("update") ? "确认覆盖" : "更新选中", () -> pending.equals("update"), () -> confirm("update")));
        delete = addDrawableChild(new DraftUi.Button(left + 20 + buttonWidth * 2, actionsY, buttonWidth, 20,
            () -> pending.equals("delete") ? "确认删除" : "删除选中", () -> pending.equals("delete"), () -> confirm("delete")));
        back = addDrawableChild(new DraftUi.Button(left + 24 + buttonWidth * 3, actionsY, buttonWidth, 20,
            () -> pending.isEmpty() ? "返回" : "取消", () -> false, () -> { if (pending.isEmpty()) close(); else cancel(); }));
        refresh(); setInitialFocus(name);
    }
    private GridPos origin() { return new GridPos(draft.origin.getX(), draft.origin.getY(), draft.origin.getZ()); }
    private DraftLibrary.Entry selectedEntry() { return entries.stream().filter(e -> e.name().equals(selected)).findFirst().orElse(null); }
    private void failed(Exception e) { status = "操作失败：" + e.getMessage(); BuildDraftClient.LOGGER.warn("Draft manager operation failed", e); }
    private void refresh() {
        pending = "";
        try { entries = library.list(); }
        catch (Exception e) { failed(e); entries = List.of(); }
        if (selectedEntry() == null) selected = "";
        rebuild();
    }
    private void rebuild() {
        page = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / pageSize)));
        rows.forEach(this::remove); rows.clear();
        for (int i = 0; i < pageSize && page * pageSize + i < entries.size(); i++) {
            var row = new Row(entries.get(page * pageSize + i), left + 12, listTop + i * 34, panelWidth - 24);
            rows.add(row); addDrawableChild(row);
        }
        previous.active = page > 0; next.active = (page + 1) * pageSize < entries.size();
        var entry = selectedEntry();
        load.active = pending.isEmpty() && entry != null && entry.error().isEmpty() && entry.origin() != null;
        projection.active = materials.active = load.active;
        stopProjection.active = ConstructionProjection.active();
        update.active = entry != null && !pending.equals("delete");
        delete.active = entry != null && !pending.equals("update");
    }
    private void saveNew() {
        try {
            library.create(typedName, draft.blueprint, origin());
            selected = typedName; status = "已保存：" + selected; refresh();
            page = entries.stream().map(DraftLibrary.Entry::name).toList().indexOf(selected) / pageSize; rebuild();
        } catch (Exception e) { failed(e); }
    }
    private void preserve() throws java.io.IOException { library.preserve(draft.blueprint, origin()); }
    private void loadSelected() {
        if (selectedEntry() == null) return;
        try {
            var saved = library.load(selected);
            // Validate required blocks before backing up or changing the active draft.
            saved.blueprint().blocks().values().stream().distinct().forEach(dev.builddraft.BlockStates::resolve);
            preserve(); draft.load(saved.blueprint());
            var anchor = saved.origin(); draft.origin = new BlockPos(anchor.x(), anchor.y(), anchor.z());
            draft.focusDraft();
            BuildDraftClient.draftChanged(); client.setScreen(parent == null ? new DraftEditorScreen(draft) : parent);
        } catch (Exception e) { failed(e); }
    }
    private void newBlank() {
        try {
            preserve(); draft.load(new Blueprint());
            BuildDraftClient.draftChanged(); selected = ""; status = "已新建空白；可返回建造后命名保存"; refresh();
        } catch (Exception e) { failed(e); }
    }
    private void confirm(String action) {
        if (selectedEntry() == null) return;
        if (!pending.equals(action)) {
            pending = action;
            status = (action.equals("delete") ? "再次点击确认删除：" : "用当前内容覆盖：") + selected;
            rebuild(); return;
        }
        try {
            if (action.equals("delete")) {
                ConstructionProjection.deleting(selected);
                library.delete(selected); status = "已删除存档；当前编辑内容仍保留"; selected = "";
            } else { library.update(selected, draft.blueprint, origin()); ConstructionProjection.updated(selected); status = "已更新：" + selected; }
            refresh();
        } catch (Exception e) { failed(e); pending = ""; rebuild(); }
    }
    private void cancel() { pending = ""; status = "已取消"; rebuild(); }
    private void toggleProjection() {
        try {
            Mode mode = ConstructionProjection.mode(selected).next();
            ConstructionProjection.set(selected, mode);
            status = mode == Mode.OFF ? "施工投影已关闭" : "施工投影：" + selected + " · " + mode.label + " · 返回普通游戏后显示";
            rebuild();
        } catch (Exception e) { failed(e); }
    }
    private void stopProjection() {
        try { ConstructionProjection.stop(); status = "施工投影已关闭"; rebuild(); }
        catch (Exception e) { failed(e); }
    }
    private void materials() {
        try { client.setScreen(new MaterialsScreen(this, selected, ConstructionProjection.snapshot(selected))); }
        catch (Exception e) { failed(e); }
    }
    private void turnPage(int delta) {
        page += delta; selected = ""; pending = ""; status = "请选择草稿"; rebuild();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { client.setScreen(parent); }
    @Override public void tick() { readyForTyping = true; name.tick(); }
    @Override public boolean charTyped(char c, int mods) { return !readyForTyping || super.charTyped(c, mods); }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE && !pending.isEmpty()) { cancel(); return true; }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && name.isFocused() && pending.isEmpty()) { saveNew(); return true; }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (amount != 0 && x >= left + 12 && x < left + panelWidth - 12 && y >= listTop && y < listTop + pageSize * 34) {
            turnPage(amount > 0 ? -1 : 1); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        draw.fill(0, 0, width, height, DraftUi.BG);
        DraftUi.panel(draw, left, top, panelWidth, panelHeight);
        draw.drawText(textRenderer, "草稿管理", left + 12, top + 12, DraftUi.TEXT, false);
        draw.drawText(textRenderer, "当前世界 / 维度 · 编辑中 " + draft.blueprint.blocks().size() + " 格 · 已存 " + entries.size() + " 份", left + 12, top + 30, DraftUi.MUTED, false);
        if (entries.isEmpty()) draw.drawCenteredTextWithShadow(textRenderer, "还没有草稿，输入名称保存第一份", width / 2, listTop + 10, DraftUi.MUTED);
        String pages = (page + 1) + "/" + Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        draw.drawText(textRenderer, pages, left + 130, top + panelHeight - 78, DraftUi.MUTED, false);
        draw.drawText(textRenderer, textRenderer.trimToWidth(status, panelWidth - 24), left + 12, top + panelHeight - 23, DraftUi.TEXT, false);
        super.render(draw, mouseX, mouseY, delta);
    }
    private final class Row extends ButtonWidget {
        private final DraftLibrary.Entry entry;
        Row(DraftLibrary.Entry entry, int x, int y, int w) {
            super(x, y, w, 30, Text.literal(entry.name()), button -> { }, DEFAULT_NARRATION_SUPPLIER);
            this.entry = entry;
            String detail = entry.error().isEmpty() ? "世界原点：" + entry.origin() : entry.error();
            setTooltip(Tooltip.of(Text.literal(entry.name() + "\n" + detail)));
        }
        @Override public void onPress() {
            selected = entry.name(); pending = "";
            status = entry.error().isEmpty() ? "已选：" + selected : "无法载入：" + entry.error(); rebuild();
        }
        @Override protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            boolean chosen = selected.equals(entry.name());
            draw.fill(getX(), getY(), getX() + width, getY() + height, chosen ? 0xffd6e5de : isHovered() || isFocused() ? 0xfff1dfb5 : 0xffeee4cd);
            draw.drawBorder(getX(), getY(), width, height, chosen ? DraftUi.ACCENT : DraftUi.BORDER);
            draw.drawText(textRenderer, textRenderer.trimToWidth(entry.name(), width - 12), getX() + 6, getY() + 4, DraftUi.TEXT, false);
            String detail = entry.error().isEmpty() ? entry.blocks() + " 格 · " + TIME.format(Instant.ofEpochMilli(entry.modified()).atZone(ZoneId.systemDefault()))
                + (ConstructionProjection.selected(entry.name()) ? " · 投影" + ConstructionProjection.mode(entry.name()).label : "") : "文件损坏或不可读";
            draw.drawText(textRenderer, textRenderer.trimToWidth(detail, width - 12), getX() + 6, getY() + 17, DraftUi.MUTED, false);
        }
    }
}
