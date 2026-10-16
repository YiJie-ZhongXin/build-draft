package dev.builddraft.screen;

import dev.builddraft.DraftSession;
import dev.builddraft.BlockCategories;
import dev.builddraft.model.BlockCategoryRules;
import dev.builddraft.model.BlockCategoryRules.Function;
import dev.builddraft.model.BlockCategoryRules.Type;
import dev.builddraft.model.BlockCategoryRules.Source;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Enumerates the live block registry, including modded and non-inventory blocks. */
public final class BlockPaletteScreen extends Screen implements DraftScreen {
    private final DraftEditorScreen parent;
    private final DraftSession draft;
    private final List<Block> all = Registries.BLOCK.stream().filter(b -> !b.getDefaultState().isAir())
        .sorted(Comparator.comparing(b -> Registries.BLOCK.getId(b).toString())).toList();
    private final List<BlockTile> tiles = new ArrayList<>();
    private Map<Block, BlockCategoryRules.Membership> memberships;
    private final List<ButtonWidget> categoryButtons = new ArrayList<>();
    private List<Block> filtered = List.of();
    private String query = "";
    private int page, columns, rows, gridX, gridTop;
    private int panelX, panelY, panelWidth, panelHeight;
    private String status = "点下方槽位，再选方块；Shift 连续装填";
    private Source source = Source.ALL;
    private boolean byType;
    private Function function = Function.BUILDING;
    private Type type = Type.ALL;
    private TextFieldWidget search;
    private ButtonWidget prev, next;

    public BlockPaletteScreen(DraftEditorScreen parent, DraftSession draft) { super(Text.literal("方块库")); this.parent = parent; this.draft = draft; }
    @Override protected void init() {
        tiles.clear();
        categoryButtons.clear();
        if (memberships == null) memberships = BlockCategories.index(all);
        panelWidth = Math.min(310, width - 12); panelHeight = Math.min(300, height - 12);
        panelX = (width - panelWidth) / 2; panelY = (height - panelHeight) / 2;
        columns = 9;
        gridX = width / 2 - columns * 18 / 2;
        search = new DraftUi.Field(textRenderer, panelX + 10, panelY + 24, panelWidth - 78, 18, Text.literal("搜索名称 / 方块 ID"));
        search.setMaxLength(128); search.setText(query);
        search.setSuggestion(query.isEmpty() ? "搜索方块名称或 ID…" : "");
        search.setChangedListener(value -> { query = value; search.setSuggestion(value.isEmpty() ? "搜索方块名称或 ID…" : ""); page = 0; rebuild(); });
        addDrawableChild(search);
        addDrawableChild(DraftUi.button(panelX + panelWidth - 62, panelY + 23, 52, "完成", this::close));
        addDrawableChild(new DraftUi.Button(panelX + 10, panelY + 48, 62, 18, () -> "功能分类", () -> !byType, () -> switchMode(false)));
        addDrawableChild(new DraftUi.Button(panelX + 76, panelY + 48, 62, 18, () -> "类型分类", () -> byType, () -> switchMode(true)));
        addDrawableChild(new DraftUi.Button(panelX + panelWidth - 144, panelY + 48, 134, 18, () -> "来源：" + switch (source) { case ALL -> "全部"; case VANILLA -> "原版"; case MODDED -> "模组"; }, () -> source != Source.ALL,
            () -> { source = Source.values()[(source.ordinal() + 1) % Source.values().length]; page = 0; rebuild(); }).hint("点击切换全部 / 原版 / 模组；与当前分类和搜索一起筛选"));
        prev = addDrawableChild(new DraftUi.Button(panelX + panelWidth - 110, hotbarY() - 26, 48, 18, () -> "上一页", () -> false, () -> { page--; rebuild(); }));
        next = addDrawableChild(new DraftUi.Button(panelX + panelWidth - 58, hotbarY() - 26, 48, 18, () -> "下一页", () -> false, () -> { page++; rebuild(); }));
        DraftHotbar.slots(draft, hotbarX(), hotbarY(), () -> {
            setFocused(null); status = "正在配置快捷栏 " + (draft.selectedSlot() + 1) + " · 点击上方方块替换";
        }, null).forEach(this::addDrawableChild);
        rebuildCategories();
        rebuild();
        setInitialFocus(search);
    }
    private void switchMode(boolean useTypes) {
        byType = useTypes; page = 0; rebuildCategories(); rebuild();
    }
    private void rebuildCategories() {
        categoryButtons.forEach(this::remove); categoryButtons.clear();
        int count = byType ? Type.values().length : Function.values().length;
        int perRow = Math.max(1, Math.min(count, (panelWidth - 20) / 46));
        int chipWidth = Math.min(66, (panelWidth - 20 - (perRow - 1) * 4) / perRow);
        int categoryRows = (count + perRow - 1) / perRow;
        for (int i = 0; i < count; i++) {
            int index = i;
            var chip = new DraftUi.Button(panelX + 10 + i % perRow * (chipWidth + 4), panelY + 72 + i / perRow * 22, chipWidth, 18,
                () -> byType ? Type.values()[index].label : Function.values()[index].label,
                () -> byType ? type == Type.values()[index] : function == Function.values()[index],
                () -> { if (byType) type = Type.values()[index]; else function = Function.values()[index]; page = 0; rebuild(); });
            categoryButtons.add(chip); addDrawableChild(chip);
        }
        gridTop = panelY + 72 + categoryRows * 22 + 5;
        rows = Math.max(1, Math.min(6, (hotbarY() - 34 - gridTop) / 18));
    }
    private void rebuild() {
        filtered = all.stream().filter(block -> {
            var id = Registries.BLOCK.getId(block);
            return BlockCategoryRules.matches(id.toString(), block.getName().getString(), query, source, memberships.get(block),
                byType ? Function.ALL : function, byType ? type : Type.ALL);
        }).toList();
        int size = columns * rows;
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / size)));
        tiles.forEach(this::remove); tiles.clear();
        for (int i = 0; i < size && page * size + i < filtered.size(); i++) {
            Block block = filtered.get(page * size + i);
            BlockTile tile = new BlockTile(gridX + i % columns * 18, gridTop + i / columns * 18, block);
            tiles.add(tile); addDrawableChild(tile);
        }
        prev.active = page > 0; next.active = (page + 1) * size < filtered.size();
    }
    public static ItemStack icon(Block block) {
        if (block == Blocks.WATER) return Items.WATER_BUCKET.getDefaultStack();
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET.getDefaultStack();
        var item = block.asItem();
        return item == Items.AIR ? Items.BARRIER.getDefaultStack() : item.getDefaultStack();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void close() { parent.selectedMaterial(); client.setScreen(parent); }
    private int hotbarX() { return width / 2 - DraftHotbar.WIDTH / 2; }
    private int hotbarY() { return panelY + panelHeight - 28; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        int slot = DraftHotbar.keySlot(key);
        boolean numeric = key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9;
        if (slot >= 0 && !hasControlDown() && !hasAltDown() && !hasShiftDown() && (numeric || !search.isFocused())) {
            for (var tile : tiles) if (tile.isHovered()) {
                draft.setSlot(slot, tile.block.getDefaultState());
                status = tile.block.getName().getString() + " → 快捷栏 " + (slot + 1);
                return true;
            }
            if (!search.isFocused()) { draft.selectSlot(slot); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (DraftHotbar.contains(x, y, hotbarX(), hotbarY())) { draft.cycleSlot(amount > 0 ? -1 : 1); return true; }
        if (x >= gridX && x < gridX + columns * 18 && y >= gridTop && y < gridTop + rows * 18) { page += amount < 0 ? 1 : -1; rebuild(); return true; }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void render(DrawContext draw, int x, int y, float delta) {
        draw.fill(0, 0, width, height, DraftUi.BG);
        DraftUi.panel(draw, panelX, panelY, panelWidth, panelHeight);
        draw.drawText(textRenderer, textRenderer.trimToWidth(status, panelWidth - 20), panelX + 10, panelY + 9, DraftUi.TEXT, false);
        for (int i = 0; i < columns * rows; i++) DraftUi.slot(draw, gridX + i % columns * 18, gridTop + i / columns * 18, 18, 18);
        int pages = Math.max(1, (filtered.size() + columns * rows - 1) / (columns * rows));
        draw.drawText(textRenderer, filtered.size() + " 种 · " + (page + 1) + "/" + pages, panelX + 10, hotbarY() - 21, DraftUi.TEXT, false);
        if (filtered.isEmpty()) DraftUi.centered(draw, "没有匹配的方块", width / 2, gridTop + 8, DraftUi.MUTED);
        DraftHotbar.render(draw, draft, hotbarX(), hotbarY());
        super.render(draw, x, y, delta);
    }
    private final class BlockTile extends ButtonWidget {
        private final Block block;
        BlockTile(int x, int y, Block block) {
            super(x, y, 18, 18, block.getName(), b -> {
                int slot = draft.selectedSlot(); draft.setSlot(slot, block.getDefaultState());
                status = block.getName().getString() + " → 快捷栏 " + (slot + 1);
                if (Screen.hasShiftDown()) draft.cycleSlot(1);
            }, DEFAULT_NARRATION_SUPPLIER);
            this.block = block;
            setTooltip(Tooltip.of(Text.literal(block.getName().getString() + "\n" + Registries.BLOCK.getId(block) + "\n点击放入当前槽 · 1–9、0、-、= 指定槽")));
        }
        @Override protected void renderButton(DrawContext draw, int x, int y, float delta) {
            boolean selected = draft.brush.isOf(block);
            if (isHovered() || isFocused()) draw.fill(getX() + 1, getY() + 1, getX() + 17, getY() + 17, 0x80ffffff);
            if (selected) draw.drawBorder(getX(), getY(), 18, 18, DraftUi.ACCENT);
            draw.drawItem(icon(block), getX() + 1, getY() + 1);
        }
    }
}
