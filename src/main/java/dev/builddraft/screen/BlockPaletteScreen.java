package dev.builddraft.screen;

import dev.builddraft.DraftSession;
import dev.builddraft.BlockCategories;
import dev.builddraft.model.BlockCategoryRules;
import dev.builddraft.model.BlockCategoryRules.Material;
import dev.builddraft.model.BlockCategoryRules.Shape;
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

/** A single material-and-shape browser with a filtered recent-material strip. */
public final class BlockPaletteScreen extends Screen implements DraftScreen {
    private final DraftEditorScreen parent;
    private final DraftSession draft;
    private final List<Block> all = Registries.BLOCK.stream().filter(b -> !b.getDefaultState().isAir()).toList();
    private final List<BlockTile> tiles = new ArrayList<>(), recentTiles = new ArrayList<>();
    private Map<Block, BlockCategoryRules.Membership> memberships;
    private List<Block> ordered = List.of(), filtered = List.of();
    private int page, columns, rows, gridX, gridTop, recentY, recentLabelY;
    private int panelX, panelY, panelWidth, panelHeight;
    private String status = "选材 · 材质与形状可组合筛选";
    private TextFieldWidget search;
    private boolean readyForTyping, showRecent;
    private ButtonWidget prev, next;

    public BlockPaletteScreen(DraftEditorScreen parent, DraftSession draft) {
        super(Text.literal("方块库")); this.parent = parent; this.draft = draft;
    }
    @Override protected void init() {
        readyForTyping = false;
        tiles.clear(); recentTiles.clear();
        if (memberships == null) {
            memberships = BlockCategories.index(all);
            Map<String, BlockCategoryRules.Membership> byId = new HashMap<>();
            all.forEach(block -> byId.put(Registries.BLOCK.getId(block).toString(), memberships.get(block)));
            var order = BlockCategoryRules.order(byId);
            ordered = all.stream().sorted(Comparator.comparing(block -> Registries.BLOCK.getId(block).toString(), order)).toList();
        }
        panelWidth = Math.min(412, width - 12); panelHeight = Math.min(346, height - 12);
        panelX = (width - panelWidth) / 2; panelY = (height - panelHeight) / 2;
        columns = Math.max(1, Math.min(18, (panelWidth - 20) / 18));
        gridX = width / 2 - columns * 18 / 2;
        search = new DraftUi.Field(textRenderer, panelX + 10, panelY + 24, panelWidth - 78, 18, Text.literal("搜索名称 / ID，可用空格组合关键词"));
        search.setMaxLength(128); search.setText(draft.paletteQuery);
        search.setSuggestion(draft.paletteQuery.isEmpty() ? "搜索方块名称或 ID…" : "");
        search.setChangedListener(value -> {
            draft.paletteQuery = value; search.setSuggestion(value.isEmpty() ? "搜索方块名称或 ID…" : ""); page = 0; rebuild();
        });
        addDrawableChild(search);
        addDrawableChild(DraftUi.button(panelX + panelWidth - 62, panelY + 23, 52, "完成", this::close));
        int utilityY;
        if (panelWidth < 360 || panelHeight < 280) {
            int half = (panelWidth - 24) / 2;
            addDrawableChild(new DraftUi.Button(panelX + 10, panelY + 48, half, 18, () -> "材质：" + draft.paletteMaterial.label,
                () -> draft.paletteMaterial != Material.ALL, () -> { draft.paletteMaterial = Material.values()[(draft.paletteMaterial.ordinal() + 1) % Material.values().length]; changedFilter(); }).hint("点击循环材质分类"));
            addDrawableChild(new DraftUi.Button(panelX + 14 + half, panelY + 48, half, 18, () -> "形状：" + draft.paletteShape.label,
                () -> draft.paletteShape != Shape.ALL, () -> { draft.paletteShape = Shape.values()[(draft.paletteShape.ordinal() + 1) % Shape.values().length]; changedFilter(); }).hint("点击循环形状，可与材质组合"));
            utilityY = panelY + 70;
        } else {
            int materialRows = chips(Material.values().length, panelY + 48, index -> Material.values()[index].label,
                index -> draft.paletteMaterial == Material.values()[index], index -> { draft.paletteMaterial = Material.values()[index]; changedFilter(); });
            int shapeTop = panelY + 48 + materialRows * 22;
            int shapeRows = chips(Shape.values().length, shapeTop, index -> Shape.values()[index].label,
                index -> draft.paletteShape == Shape.values()[index], index -> { draft.paletteShape = Shape.values()[index]; changedFilter(); });
            utilityY = shapeTop + shapeRows * 22;
        }
        int utilityWidth = (panelWidth - 24) / 2;
        addDrawableChild(new DraftUi.Button(panelX + 10, utilityY, utilityWidth, 18,
            () -> "来源：" + switch (draft.paletteSource) { case ALL -> "全部"; case VANILLA -> "原版"; case MODDED -> "模组"; },
            () -> draft.paletteSource != Source.ALL, () -> { draft.paletteSource = Source.values()[(draft.paletteSource.ordinal() + 1) % Source.values().length]; changedFilter(); })
            .hint("原版 / 模组来源与材质、形状、搜索一起筛选"));
        addDrawableChild(new DraftUi.Button(panelX + 14 + utilityWidth, utilityY, utilityWidth, 18,
            () -> draft.paletteSpecial ? "特殊方块：显示" : "特殊方块：隐藏", () -> draft.paletteSpecial,
            () -> { draft.paletteSpecial = !draft.paletteSpecial; changedFilter(); })
            .hint("显示没有物品图标的技术方块、墙上火把、盆栽等特殊或重复形态"));
        recentLabelY = utilityY + 24;
        recentY = utilityY + 36;
        showRecent = panelHeight >= 220;
        gridTop = showRecent ? recentY + 28 : utilityY + 26;
        rows = Math.max(1, Math.min(8, (hotbarY() - 34 - gridTop) / 18));
        prev = addDrawableChild(new DraftUi.Button(panelX + panelWidth - 110, hotbarY() - 26, 48, 18, () -> "上一页", () -> false, () -> { page--; rebuild(); }));
        next = addDrawableChild(new DraftUi.Button(panelX + panelWidth - 58, hotbarY() - 26, 48, 18, () -> "下一页", () -> false, () -> { page++; rebuild(); }));
        DraftHotbar.slots(draft, hotbarX(), hotbarY(), () -> {
            setFocused(null); status = "正在配置槽位 " + (draft.selectedSlot() + 1); rebuildRecent();
        }, null).forEach(this::addDrawableChild);
        rebuild();
        setInitialFocus(search);
    }
    private int chips(int count, int top, java.util.function.IntFunction<String> label,
                      java.util.function.IntPredicate selected, java.util.function.IntConsumer action) {
        int perRow = Math.max(1, Math.min(count, (panelWidth - 16) / 46));
        int chipWidth = (panelWidth - 20 - (perRow - 1) * 4) / perRow;
        for (int i = 0; i < count; i++) {
            int index = i;
            addDrawableChild(new DraftUi.Button(panelX + 10 + i % perRow * (chipWidth + 4), top + i / perRow * 22,
                chipWidth, 18, () -> label.apply(index), () -> selected.test(index), () -> action.accept(index)));
        }
        return (count + perRow - 1) / perRow;
    }
    private void changedFilter() { page = 0; rebuild(); }
    private boolean matches(Block block) {
        return BlockCategoryRules.matches(Registries.BLOCK.getId(block).toString(), block.getName().getString(), draft.paletteQuery,
            draft.paletteSource, memberships.get(block), draft.paletteMaterial, draft.paletteShape, draft.paletteSpecial);
    }
    private void rebuild() {
        filtered = ordered.stream().filter(this::matches).toList();
        int size = columns * rows;
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / size)));
        tiles.forEach(this::remove); tiles.clear();
        for (int i = 0; i < size && page * size + i < filtered.size(); i++) {
            var tile = new BlockTile(gridX + i % columns * 18, gridTop + i / columns * 18, filtered.get(page * size + i));
            tiles.add(tile); addDrawableChild(tile);
        }
        if (prev != null) prev.active = page > 0;
        if (next != null) next.active = (page + 1) * size < filtered.size();
        rebuildRecent();
    }
    private void rebuildRecent() {
        recentTiles.forEach(this::remove); recentTiles.clear();
        if (!showRecent) return;
        for (String id : draft.recentMaterials.ids()) {
            Block block = Registries.BLOCK.get(new net.minecraft.util.Identifier(id));
            if (!memberships.containsKey(block) || !matches(block)) continue;
            var tile = new BlockTile(gridX + recentTiles.size() * 18, recentY, block);
            recentTiles.add(tile); addDrawableChild(tile);
            if (recentTiles.size() >= columns) break;
        }
    }
    private void choose(Block block, int slot, boolean advance) {
        draft.setSlot(slot, block.getDefaultState());
        status = block.getName().getString() + " → 槽位 " + (slot + 1);
        if (advance) draft.advanceSlot();
        rebuildRecent();
    }
    public static ItemStack icon(Block block) {
        if (block == Blocks.WATER) return Items.WATER_BUCKET.getDefaultStack();
        if (block == Blocks.LAVA) return Items.LAVA_BUCKET.getDefaultStack();
        var item = block.asItem();
        return item == Items.AIR ? Items.BARRIER.getDefaultStack() : item.getDefaultStack();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void tick() { readyForTyping = true; search.tick(); }
    @Override public boolean charTyped(char character, int modifiers) { return !readyForTyping || super.charTyped(character, modifiers); }
    @Override public void close() { parent.selectedMaterial(); client.setScreen(parent); }
    private int hotbarX() { return width / 2 - DraftHotbar.WIDTH / 2; }
    private int hotbarY() { return panelY + panelHeight - 28; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        int slot = DraftHotbar.keySlot(key);
        boolean numeric = key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9;
        if (slot >= 0 && !hasControlDown() && !hasAltDown() && !hasShiftDown() && (numeric || !search.isFocused())) {
            List<BlockTile> allTiles = new ArrayList<>(tiles); allTiles.addAll(recentTiles);
            for (var tile : allTiles) if (tile.isHovered()) { choose(tile.block, slot, false); return true; }
            if (!search.isFocused()) { draft.selectSlot(slot); rebuildRecent(); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (DraftHotbar.contains(x, y, hotbarX(), hotbarY())) { draft.cycleSlot(amount > 0 ? -1 : 1); rebuildRecent(); return true; }
        if (x >= gridX && x < gridX + columns * 18 && y >= gridTop && y < gridTop + rows * 18) {
            page += amount < 0 ? 1 : -1; rebuild(); return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public void render(DrawContext draw, int x, int y, float delta) {
        draw.fill(0, 0, width, height, DraftUi.BG);
        DraftUi.panel(draw, panelX, panelY, panelWidth, panelHeight);
        draw.drawText(textRenderer, textRenderer.trimToWidth(status, panelWidth - 20), panelX + 10, panelY + 9, DraftUi.TEXT, false);
        if (showRecent) {
            draw.drawText(textRenderer, "最近使用", gridX, recentLabelY, DraftUi.ACCENT, false);
            if (recentTiles.isEmpty()) draw.drawText(textRenderer, "此筛选暂无最近使用", gridX + 54, recentLabelY, DraftUi.MUTED, false);
            for (int i = 0; i < columns; i++) DraftUi.slot(draw, gridX + i * 18, recentY, 18, 18);
        }
        for (int i = 0; i < columns * rows; i++) DraftUi.slot(draw, gridX + i % columns * 18, gridTop + i / columns * 18, 18, 18);
        int pages = Math.max(1, (filtered.size() + columns * rows - 1) / (columns * rows));
        draw.drawText(textRenderer, filtered.size() + " 种 · " + (page + 1) + "/" + pages, panelX + 10, hotbarY() - 21, DraftUi.TEXT, false);
        if (filtered.isEmpty()) draw.drawCenteredTextWithShadow(textRenderer, "没有匹配方块，可调整筛选或显示特殊方块", width / 2, gridTop + 8, DraftUi.MUTED);
        DraftHotbar.render(draw, draft, hotbarX(), hotbarY());
        super.render(draw, x, y, delta);
    }
    private final class BlockTile extends ButtonWidget {
        private final Block block;
        BlockTile(int x, int y, Block block) {
            super(x, y, 18, 18, block.getName(), b -> choose(block, draft.selectedSlot(), Screen.hasShiftDown()), DEFAULT_NARRATION_SUPPLIER);
            this.block = block;
            var group = memberships.get(block);
            setTooltip(Tooltip.of(Text.literal(block.getName().getString() + "\n" + group.material().label + " · " + group.shape().label
                + (group.special() ? " · 特殊形态" : "") + "\n" + Registries.BLOCK.getId(block) + "\n点击装入当前槽 · Shift 连续装填 · 数字指定槽")));
        }
        @Override protected void renderButton(DrawContext draw, int x, int y, float delta) {
            if (isHovered() || isFocused()) draw.fill(getX() + 1, getY() + 1, getX() + 17, getY() + 17, 0x80ffffff);
            if (draft.brush.isOf(block)) draw.drawBorder(getX(), getY(), 18, 18, DraftUi.ACCENT);
            draw.drawItem(icon(block), getX() + 1, getY() + 1);
        }
    }
}
