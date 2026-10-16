package dev.builddraft.screen;

import dev.builddraft.*;
import dev.builddraft.model.*;
import dev.builddraft.model.BuildGeometry.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class DraftEditorScreen extends Screen implements DraftScreen {
    private static final int DOCK_HEIGHT = 60;
    private final DraftSession draft;
    private Perspective previousPerspective;
    private GridPos lastPainted;
    private String status = "右键贴面放置；选择“高度”可在空中起建";
    private int sidebar, scroll;
    private boolean collapsed, appearance;
    private boolean gridMoveTool, gridDragging, gridVertical;
    private GridPos gridDragStart, gridPointerStart;
    private Plane gridDragPlane;
    private Plane gridPlaneBeforeDrag;
    private double gridMouseStartY, gridUnitsPerPixel;
    private TextFieldWidget heightField;
    private long lastAction;
    private double pointerX, pointerY;
    private int canvasButton = -1;
    private DraftPicker.Selection lastSelection;
    private double extrusionStartX, extrusionStartY, axisX, axisY, pixelsPerStep;
    private int extrusionSteps, scrollSteps;
    private final List<SideWidget> sideWidgets = new ArrayList<>();
    private record SideWidget(ClickableWidget widget, int y) { }

    public DraftEditorScreen(DraftSession draft) {
        super(Text.literal("筑稿 · 建筑工作台")); this.draft = draft;
        collapsed = draft.controlsCollapsed;
    }

    @Override protected void init() {
        if (previousPerspective == null) previousPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        sidebar = collapsed ? 0 : Math.min(148, width / 2 - 8);
        draft.viewportOffset = (double) sidebar / height;
        sideWidgets.clear(); heightField = null;
        if (collapsed) {
            addDrawableChild(DraftUi.button(6, 6, 82, "工具 [Tab] >", this::toggleControls).hint("展开建造 / 显示设置；H 移动网格，Q/E 升降，Esc 完成"));
        } else {
            int bx = Math.max(88, width - 260);
            addDrawableChild(DraftUi.button(bx, 9, 64, "收起 [Tab]", this::toggleControls).hint("收起全部工具面板，保留方块快捷栏"));
            addDrawableChild(DraftUi.button(bx + 68, 9, 46, "蓝图库", () -> client.setScreen(new LibraryScreen(this, draft))));
            addDrawableChild(DraftUi.button(bx + 118, 9, 40, "材料", () -> client.setScreen(new MaterialsScreen(this, draft))));
            addDrawableChild(DraftUi.button(bx + 162, 9, 40, "按键", () -> client.setScreen(new DraftKeybindsScreen(this))));
            addDrawableChild(DraftUi.button(bx + 206, 9, 46, "完成", this::close));
        }
        DraftHotbar.slots(draft, hotbarX(), hotbarY(), this::selectedMaterial, this::palette).forEach(this::addDrawableChild);
        addDrawableChild(DraftUi.button(hotbarX() + DraftHotbar.WIDTH + 6, hotbarY() + 2, 20, "+", this::palette).hint("打开方块库，为当前快捷槽换方块"));
        if (collapsed) return;
        int half = (sidebar - 24) / 2;
        addDrawableChild(new DraftUi.Button(8, 48, half, 20, () -> "建造", () -> !appearance, () -> { appearance = false; scroll = 0; clearAndInit(); }));
        addDrawableChild(new DraftUi.Button(12 + half, 48, half, 20, () -> "显示", () -> appearance, () -> { appearance = true; scroll = 0; clearAndInit(); }));
        if (!appearance) {
            side(DraftUi.button(8, 118, sidebar - 16, "选择方块  [" + DraftKeys.label(DraftKeys.PALETTE) + "]", this::palette));
            side(new DraftUi.Button(8, 144, sidebar - 16, 20, () -> draft.automaticPlacement ? "朝向：自动" : "朝向：手动属性", () -> draft.automaticPlacement, () -> draft.automaticPlacement = !draft.automaticPlacement).hint("自动按点击面、点击位置和相机方向放置；手动保留指定属性"));
            side(DraftUi.button(8, 170, sidebar - 16, "方块属性…", () -> client.setScreen(new BrushScreen(this, draft))));
            side(new DraftUi.Button(8, 210, half, 20, () -> "贴面", () -> draft.lockedPlane == null, () -> { gridMoveTool = false; gridDragging = false; canvasButton = -1; draft.lockedPlane = null; draft.cancelGesture(); }));
            side(new DraftUi.Button(12 + half, 210, half, 20, () -> "高度", () -> draft.lockedPlane != null, () -> draft.setWorldY(draft.worldY())).hint("独立水平网格，不受已有方块遮挡；输入世界 Y 后回车"));
            heightField = new DraftUi.Field(textRenderer, 29, 237, sidebar - 37, 20, Text.literal("世界 Y 坐标"));
            heightField.setMaxLength(7); heightField.setText(Integer.toString(draft.worldY())); side(heightField);
            side(DraftUi.button(8, 263, half, "− 下移", () -> changeHeight(-1)));
            side(DraftUi.button(12 + half, 263, half, "+ 上移", () -> changeHeight(1)));
            side(new DraftUi.Button(8, 289, sidebar - 16, 20, () -> "移动网格 [" + DraftKeys.label(DraftKeys.MOVE_GRID) + "]", () -> gridMoveTool, this::toggleGridMove).hint("左键拖动网格；Shift+左键上下拖。方向键微调；Q/E 调高度；Home 居中。已有方块留原位"));
            side(new DraftUi.Button(8, 329, half, 20, () -> "单格", () -> !draft.rectangleTool && !draft.extrusionTool && !gridMoveTool, () -> setTool(false)));
            side(new DraftUi.Button(12 + half, 329, half, 20, () -> "矩形", () -> draft.rectangleTool, () -> setTool(true)));
            side(new DraftUi.Button(8, 355, sidebar - 16, 20, () -> "选面拉伸  [" + DraftKeys.label(DraftKeys.EXTRUDE) + "]", () -> draft.extrusionTool, this::toggleExtrusion).hint("右键按住一个草稿面向外拖动；滚轮微调，Esc 取消"));
            side(DraftUi.button(8, 381, half, "撤销", () -> draft.blueprint.undo()).hint("Ctrl+Z"));
            side(DraftUi.button(12 + half, 381, half, "重做", () -> draft.blueprint.redo()).hint("Ctrl+Y"));
        } else {
            side(new DraftUi.Button(8, 96, half, 20, () -> "实景", () -> draft.solidPreview, () -> draft.solidPreview = true));
            side(new DraftUi.Button(12 + half, 96, half, 20, () -> "虚影", () -> !draft.solidPreview, () -> draft.solidPreview = false));
            side(new DraftUi.Slider(8, 122, sidebar - 16, 20, Text.empty(), (draft.ghostAlpha - .15) / .65) {
                { updateMessage(); }
                @Override protected void updateMessage() { setMessage(Text.literal("透明度 " + Math.round((.15 + value * .65) * 100) + "%")); }
                @Override protected void applyValue() { draft.ghostAlpha = (float) (.15 + value * .65); }
            });
            side(new DraftUi.Button(8, 162, sidebar - 16, 20, () -> "辅助网格", () -> draft.showGrid, () -> draft.showGrid = !draft.showGrid));
            side(new DraftUi.Button(8, 188, sidebar - 16, 20, () -> "退出后显示投影", () -> draft.projection, () -> draft.projection = !draft.projection));
            side(DraftUi.button(8, 228, sidebar - 16, "居中到工作面", draft::center));
            side(DraftUi.button(8, 254, sidebar - 16, "重置视角", () -> { draft.center(); draft.yaw = 0; draft.pitch = 55; draft.zoom = 32; }));
            side(DraftUi.button(8, 280, sidebar - 16, "网格回原点", () -> { draft.moveGrid(0, draft.origin.getY(), 0); draft.center(); gridStatus(); }).hint("只把辅助网格移回蓝图原点，不移动已有草稿"));
        }
        updateScroll();
    }
    private void side(ClickableWidget widget) { addDrawableChild(widget); sideWidgets.add(new SideWidget(widget, widget.getY())); }
    private void toggleControls() {
        draft.cancelGesture(); canvasButton = -1; gridDragging = false; lastPainted = null;
        setFocused(null);
        collapsed = !collapsed; draft.controlsCollapsed = collapsed;
        clearAndInit();
    }
    private int contentBottom() { return appearance ? 312 : 414; }
    private void updateScroll() {
        scroll = MathHelper.clamp(scroll, 0, Math.max(0, contentBottom() - (height - DOCK_HEIGHT - 4)));
        for (var entry : sideWidgets) {
            entry.widget().setY(entry.y() - scroll);
            entry.widget().visible = entry.widget().getY() >= 76 && entry.widget().getY() + entry.widget().getHeight() <= height - DOCK_HEIGHT - 4;
            if (!entry.widget().visible) entry.widget().setFocused(false);
        }
    }
    private void changeHeight(int delta) {
        draft.changeLayer(delta);
        if (heightField != null) heightField.setText(Integer.toString(draft.worldY()));
        status = "工作面 Y=" + draft.worldY() + " · 右键可直接悬空建造";
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() {
        if (client != null && previousPerspective != null) client.options.setPerspective(previousPerspective);
        previousPerspective = null; draft.hovered = null; draft.selection = null; draft.placementPreview = null; draft.bypassDraft = false;
        draft.cancelGesture(); canvasButton = -1; gridDragging = false;
        BuildDraftClient.autosave(); DraftKeys.clearPending();
    }
    @Override public void close() { client.setScreen(null); }
    private void palette() { client.setScreen(new BlockPaletteScreen(this, draft)); }
    private int hotbarX() { return width / 2 - DraftHotbar.WIDTH / 2; }
    private int hotbarY() { return height - 28; }
    void selectedMaterial() {
        draft.cancelGesture(); canvasButton = -1; lastPainted = null; gridDragging = false;
        status = "快捷栏 " + (draft.selectedSlot() + 1) + " · " + draft.brush.getBlock().getName().getString() + " · 右键槽位换方块";
    }
    private boolean canvas(double x, double y) {
        if (x <= 0 || x >= width || y <= 0 || y >= height) return false;
        if (!collapsed) return x > sidebar + 4 && y > 64 && y < height - DOCK_HEIGHT;
        // Collapsing must also return the empty header/footer area to the canvas.
        if (x >= hotbarX() - 1 && x < hotbarX() + DraftHotbar.WIDTH + 27 && y >= hotbarY() - 1) return false;
        return !(x >= 6 && x < 88 && y >= 6 && y < 26);
    }

    private Ray ray(double x, double y) { return DraftPicker.ray(draft, x, y, width, height); }
    private Plane activePlane() { return draft.rectangleStart != null ? draft.rectanglePlane : draft.lockedPlane; }
    private void refreshHover(double x, double y) {
        draft.placementPreview = null; draft.placementState = null; draft.hovered = null; draft.selection = null;
        draft.bypassDraft = hasAltDown() && draft.extrusionSelection.isEmpty();
        draft.erasingPreview = canvasButton == 0;
        if (!canvas(x, y) || !draft.extrusionSelection.isEmpty() || gridDragging) return;
        Ray ray = ray(x, y);
        Plane plane = activePlane();
        draft.selection = DraftPicker.pick(draft, ray, hasAltDown());
        if (draft.selection != null) lastSelection = draft.selection;
        if (draft.selection != null) draft.hovered = draft.selection.block();
        if (draft.extrusionTool) return;
        GridPos forced = plane != null ? plane.intersect(ray, 256) : draft.selection == null ? new Plane(1, draft.layer).intersect(ray, 256) : null;
        if (plane != null) {
            draft.selection = null; draft.hovered = forced;
            if (forced == null) return;
        }
        if (draft.erasingPreview) {
            draft.placementPreview = plane != null ? forced : draft.selection != null && draft.selection.virtual() ? draft.selection.block() : null;
            draft.placementValid = draft.validPosition(draft.placementPreview) && draft.blueprint.blocks().containsKey(draft.placementPreview);
            return;
        }
        var placement = DraftPlacement.resolve(draft, draft.selection, forced);
        draft.placementPreview = placement == null ? forced : placement.position();
        draft.placementState = placement == null ? null : placement.state();
        draft.placementValid = placement != null;
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        pointerX = mouseX; pointerY = mouseY;
        refreshHover(mouseX, mouseY);
        if (!collapsed) {
            DraftUi.panel(draw, 4, 4, width - 8, 32);
            draw.drawText(textRenderer, "筑稿手札", 13, 10, DraftUi.ACCENT, false);
            draw.drawText(textRenderer, "建筑草图", 13, 23, DraftUi.MUTED, false);
            DraftUi.panel(draw, 4, 42, sidebar - 4, height - DOCK_HEIGHT - 46);
            draw.enableScissor(5, 76, sidebar - 2, height - DOCK_HEIGHT - 4);
            if (!appearance) {
                DraftUi.slot(draw, 10, 80 - scroll, 30, 30);
                draw.drawItem(BlockPaletteScreen.icon(draft.brush.getBlock()), 17, 87 - scroll);
                draw.drawText(textRenderer, textRenderer.trimToWidth(draft.brush.getBlock().getName().getString(), sidebar - 52), 46, 83 - scroll, DraftUi.TEXT, false);
                draw.drawText(textRenderer, "快捷栏 " + (draft.selectedSlot() + 1), 46, 98 - scroll, DraftUi.MUTED, false);
                section(draw, "建造位置", 196); section(draw, "绘制工具", 315);
                draw.drawText(textRenderer, "Y", 12, 243 - scroll, DraftUi.ACCENT, false);
            } else section(draw, "预览材质", 82);
            draw.disableScissor();
            int max = Math.max(0, contentBottom() - (height - DOCK_HEIGHT - 4));
            if (max > 0) {
                int track = Math.max(20, height - DOCK_HEIGHT - 84), thumb = Math.max(14, track * track / (track + max));
                int top = 78 + scroll * (track - thumb) / max;
                draw.fill(sidebar - 3, 78, sidebar - 1, height - DOCK_HEIGHT - 6, DraftUi.BORDER);
                draw.fill(sidebar - 3, top, sidebar - 1, top + thumb, DraftUi.ACCENT);
            }
        }
        String mode = draft.lockedPlane == null ? "贴面搭建" : "工作面 " + planeLabel(draft.lockedPlane);
        String info = (gridMoveTool ? "移动网格" : mode) + " · " + draft.gridOrigin().toShortString() + " · " + draft.blueprint.blocks().size() + " 格";
        if (collapsed) {
            String compact = (gridMoveTool ? "移动网格 · " : "") + "Y " + draft.worldY() + " · " + draft.blueprint.blocks().size() + " 格";
            draw.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(compact, width - 106), 96, 12, DraftUi.HUD_TEXT);
        } else {
            int ix = sidebar + 10;
            String shownInfo = textRenderer.trimToWidth(info, width - ix - 12);
            DraftUi.panel(draw, ix - 4, 44, textRenderer.getWidth(shownInfo) + 10, 18);
            draw.drawText(textRenderer, shownInfo, ix, 49, DraftUi.ACCENT, false);
            draw.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(status, width - 20), 10, height - 53, DraftUi.HUD_TEXT);
        }
        String material = (draft.selectedSlot() + 1) + " · " + draft.brush.getBlock().getName().getString();
        if (!collapsed || DraftHotbar.contains(mouseX, mouseY, hotbarX(), hotbarY()))
            draw.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(material, width - 24), width / 2, height - 39, DraftUi.HUD_TEXT);
        DraftHotbar.render(draw, draft, hotbarX(), hotbarY());
        super.render(draw, mouseX, mouseY, delta);
    }
    private void section(DrawContext draw, String label, int y) {
        draw.fill(10, y + 10 - scroll, sidebar - 10, y + 11 - scroll, DraftUi.BORDER);
        draw.drawText(textRenderer, label, 10, y - scroll, DraftUi.MUTED, false);
    }
    private void paint(double x, double y, int button) {
        refreshHover(x, y);
        GridPos p = draft.placementPreview;
        if (!draft.placementValid || p == null || p.equals(lastPainted)) return;
        Face face = draft.selection == null ? Face.UP : draft.selection.face();
        if (draft.rectangleTool && draft.rectangleStart == null) {
            draft.rectanglePlane = activePlane() != null ? activePlane() : button == 0 ? Plane.onFace(p, face) : Plane.through(p, face);
        }
        try { draft.paint(p, button == 0); status = draft.blueprint.blocks().size() + " 个草稿方块 · " + (draft.lockedPlane == null ? "贴面搭建" : planeLabel(draft.lockedPlane)); }
        catch (IllegalArgumentException e) { status = e.getMessage(); }
        lastPainted = p; lastAction = System.currentTimeMillis();
    }
    @Override public void tick() {
        if (heightField != null) heightField.tick();
        if (canvasButton >= 0 && canvasButton < 2 && !gridDragging && !draft.rectangleTool && !draft.extrusionTool && System.currentTimeMillis() - lastAction >= 180) {
            lastPainted = null;
            paint(pointerX, pointerY, canvasButton);
            lastAction = System.currentTimeMillis();
        }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (super.mouseClicked(x, y, button)) return true;
        if (dispatch(binding -> binding.matchesMouse(button))) return true;
        if (canvas(x, y) && button >= 0 && button <= 2) {
            setFocused(null);
            canvasButton = button; lastPainted = null;
            if ((button == 0 && gridMoveTool) || (button == 2 && hasControlDown())) beginGridDrag(x, y);
            else if (button == 1 && draft.extrusionTool) beginExtrusion(x, y);
            else if (button != 2) paint(x, y, button);
            return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (canvasButton != button) return super.mouseDragged(x, y, button, dx, dy);
        if (gridDragging) { dragGrid(x, y); return true; }
        if (button == 2) {
            if (hasShiftDown()) {
                double scale = 2 * draft.zoom * Math.tan(Math.toRadians(30)) / height;
                draft.pan(-dx * scale, -dy * scale * Math.sin(Math.toRadians(draft.pitch)));
                draft.focusY = MathHelper.clamp(draft.focusY + dy * scale * Math.cos(Math.toRadians(draft.pitch)), client.world.getBottomY(), client.world.getTopY() + 64);
            } else {
                draft.yaw = OrbitMath.wrapYaw(draft.yaw + dx * .55);
                draft.pitch = OrbitMath.clampPitch(draft.pitch + dy * .35);
            }
            return true;
        }
        if (!draft.extrusionSelection.isEmpty()) { updateExtrusion(x, y); return true; }
        pointerX = x; pointerY = y;
        if (!draft.rectangleTool && !draft.extrusionTool && canvas(x, y) && System.currentTimeMillis() - lastAction >= 180) paint(x, y, button);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 1 && !draft.extrusionSelection.isEmpty()) {
            try { int count = draft.extrusionPreview.size(); draft.blueprint.edit(draft.extrusionPreview); status = "拉伸新增 " + count + " 格 · Ctrl+Z 整步撤销"; }
            catch (IllegalArgumentException e) { status = e.getMessage(); }
            draft.cancelGesture();
        }
        canvasButton = -1; lastPainted = null; gridDragging = false;
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (DraftHotbar.contains(x, y, hotbarX(), hotbarY())) { draft.cycleSlot(amount > 0 ? -1 : 1); selectedMaterial(); return true; }
        if (!collapsed && x < sidebar && y >= 76 && y < height - DOCK_HEIGHT) { scroll -= (int) (amount * 32); updateScroll(); return true; }
        if (!canvas(x, y)) return super.mouseScrolled(x, y, amount);
        if (gridDragging) return true;
        if (!draft.extrusionSelection.isEmpty()) { scrollSteps += amount > 0 ? 1 : -1; updateExtrusion(x, y); return true; }
        if (hasShiftDown() || gridMoveTool) changeHeight(amount > 0 ? 1 : -1);
        else draft.zoom = MathHelper.clamp(draft.zoom * Math.pow(.88, amount), 8, 96);
        return true;
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (heightField != null && heightField.isFocused() && heightField.visible) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                try { draft.setWorldY(Integer.parseInt(heightField.getText())); heightField.setText(Integer.toString(draft.worldY())); status = "已切换至工作面 Y=" + draft.worldY(); }
                catch (NumberFormatException e) { status = "请输入整数 Y 坐标"; }
                setFocused(null); return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) { setFocused(null); return true; }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_TAB) { toggleControls(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (gridDragging) {
                draft.moveGrid(gridDragStart.x(), draft.origin.getY() + gridDragStart.y(), gridDragStart.z());
                draft.lockedPlane = gridPlaneBeforeDrag;
                gridDragging = false; canvasButton = -1; status = "已取消网格移动";
            } else if (!draft.extrusionSelection.isEmpty() || draft.rectangleStart != null) { draft.cancelGesture(); canvasButton = -1; status = "已取消本次操作"; }
            else close();
            return true;
        }
        if (!draft.extrusionSelection.isEmpty()) return true;
        if (gridDragging) return true;
        if (gridMoveTool && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            int step = hasShiftDown() ? 8 : 1;
            var delta = OrbitMath.pan(key == GLFW.GLFW_KEY_LEFT ? -step : key == GLFW.GLFW_KEY_RIGHT ? step : 0,
                key == GLFW.GLFW_KEY_UP ? step : key == GLFW.GLFW_KEY_DOWN ? -step : 0, draft.yaw);
            draft.moveGrid(draft.gridX + (int) Math.round(delta.x()), draft.worldY(), draft.gridZ + (int) Math.round(delta.z()));
            gridStatus(); return true;
        }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) { draft.blueprint.undo(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) { draft.blueprint.redo(); return true; }
        if (dispatch(binding -> binding.matchesKey(key, scan))) return true;
        if (key == GLFW.GLFW_KEY_W) draft.pan(0, 2);
        else if (key == GLFW.GLFW_KEY_S) draft.pan(0, -2);
        else if (key == GLFW.GLFW_KEY_A) draft.pan(-2, 0);
        else if (key == GLFW.GLFW_KEY_D) draft.pan(2, 0);
        else if (DraftHotbar.keySlot(key) >= 0 && !hasControlDown() && !hasAltDown() && !hasShiftDown()) {
            draft.selectSlot(DraftHotbar.keySlot(key)); selectedMaterial();
        } else return super.keyPressed(key, scan, mods);
        return true;
    }
    private boolean dispatch(java.util.function.Predicate<KeyBinding> matches) {
        if (matches.test(DraftKeys.EDITOR)) close();
        else if (matches.test(DraftKeys.DOWN)) changeHeight(-1);
        else if (matches.test(DraftKeys.UP)) changeHeight(1);
        else if (matches.test(DraftKeys.PALETTE)) palette();
        else if (matches.test(DraftKeys.CENTER)) draft.center();
        else if (matches.test(DraftKeys.PREVIEW)) draft.solidPreview = !draft.solidPreview;
        else if (matches.test(DraftKeys.PROJECTION)) draft.projection = !draft.projection;
        else if (matches.test(DraftKeys.TOOL)) setTool(!draft.rectangleTool);
        else if (matches.test(DraftKeys.PLANE)) togglePlane();
        else if (matches.test(DraftKeys.EXTRUDE)) toggleExtrusion();
        else if (matches.test(DraftKeys.MOVE_GRID)) toggleGridMove();
        else if (matches.test(DraftKeys.ROTATE)) { draft.brush = draft.brush.rotate(BlockRotation.CLOCKWISE_90); draft.automaticPlacement = false; }
        else if (matches.test(DraftKeys.SAMPLE)) {
            if (draft.hovered != null) {
                var spec = draft.blueprint.blocks().get(draft.hovered);
                var state = spec == null ? client.world.getBlockState(draft.worldPos(draft.hovered)) : BlockStates.resolve(spec);
                if (!state.isAir()) { draft.brush = state; draft.automaticPlacement = false; }
            }
        } else return false;
        return true;
    }
    private void setTool(boolean rectangle) {
        draft.cancelGesture(); draft.extrusionTool = false; draft.rectangleTool = rectangle; gridMoveTool = false;
        status = rectangle ? "矩形：点一个面确定平面，再点两个角填充；Esc 取消" : "单格：右键放置，左键删除；按住可连续搭建";
    }
    private void gridStatus() {
        if (heightField != null) heightField.setText(Integer.toString(draft.worldY()));
        status = "网格 " + draft.gridOrigin().toShortString() + " · 已有方块不移动 · " + DraftKeys.label(DraftKeys.CENTER) + " 居中";
    }
    private void toggleGridMove() {
        gridMoveTool = !gridMoveTool; gridDragging = false; canvasButton = -1;
        draft.cancelGesture(); draft.rectangleTool = false; draft.extrusionTool = false;
        if (gridMoveTool) draft.moveGrid(draft.gridX, draft.worldY(), draft.gridZ);
        status = gridMoveTool ? "移动网格：左键拖动 / Shift+左键升降 / 方向键微调 / Home居中" : "已返回建造；网格保留当前位置，右键可继续放置";
    }
    private void beginGridDrag(double x, double y) {
        draft.cancelGesture();
        gridPlaneBeforeDrag = draft.lockedPlane;
        gridDragPlane = new Plane(1, draft.layer);
        gridPointerStart = gridDragPlane.intersect(ray(x, y), 256);
        if (gridPointerStart == null) { canvasButton = -1; status = "当前视角看不到网格，请按 " + DraftKeys.label(DraftKeys.CENTER) + " 居中"; return; }
        gridDragStart = new GridPos(draft.gridX, draft.layer, draft.gridZ);
        gridVertical = hasShiftDown(); gridMouseStartY = y;
        gridUnitsPerPixel = 2 * draft.zoom * Math.tan(Math.toRadians(30)) / height / Math.max(.25, Math.cos(Math.toRadians(draft.pitch)));
        gridDragging = true;
        draft.moveGrid(draft.gridX, draft.worldY(), draft.gridZ);
        gridStatus();
    }
    private void dragGrid(double x, double y) {
        if (gridVertical) {
            int yOffset = (int) Math.round((gridMouseStartY - y) * gridUnitsPerPixel);
            draft.moveGrid(gridDragStart.x(), draft.origin.getY() + gridDragStart.y() + yOffset, gridDragStart.z());
        } else {
            GridPos pointer = gridDragPlane.intersect(ray(x, y), 256);
            if (pointer == null) return;
            GridPos moved = GridWindow.drag(gridDragStart, gridPointerStart, pointer);
            draft.moveGrid(moved.x(), draft.origin.getY() + moved.y(), moved.z());
        }
        gridStatus();
    }
    private String planeLabel(Plane plane) {
        int coordinate = plane.coordinate() + (plane.axis() == 0 ? draft.origin.getX() : plane.axis() == 1 ? draft.origin.getY() : draft.origin.getZ());
        return (plane.axis() == 0 ? "X=" : plane.axis() == 1 ? "Y=" : "Z=") + coordinate;
    }
    private void togglePlane() {
        draft.cancelGesture();
        if (draft.lockedPlane != null) { draft.lockedPlane = null; status = "已释放平面，恢复自由贴面搭建"; return; }
        var hit = draft.selection != null ? draft.selection : lastSelection;
        draft.lockedPlane = hit != null && draft.validPosition(hit.placement()) ? Plane.through(hit.placement(), hit.face()) : new Plane(1, draft.layer);
        status = "已锁定 " + planeLabel(draft.lockedPlane) + "，可连续画地板或墙面；再按键释放";
    }
    private void toggleExtrusion() {
        draft.cancelGesture(); draft.extrusionTool = !draft.extrusionTool; draft.rectangleTool = false; gridMoveTool = false;
        status = draft.extrusionTool ? "拉伸：右键按住一个草稿面向外拖；默认连接同材质面，Shift 只选单格" : "已切回贴面放置";
    }
    private void beginExtrusion(double x, double y) {
        refreshHover(x, y);
        var hit = draft.selection;
        if (hit == null || !hit.virtual()) { status = "请在草稿方块的外露表面开始拉伸"; return; }
        var selected = SurfaceExtrusion.select(draft.blueprint.blocks(), hit.block(), hit.face(), hasShiftDown());
        if (selected.isEmpty()) { status = "这个面被草稿遮挡，无法拉伸"; return; }
        draft.extrusionSelection = selected; draft.extrusionFace = hit.face();
        draft.placementPreview = null; extrusionStartX = x; extrusionStartY = y; extrusionSteps = 0; scrollSteps = 0;
        var camera = client.gameRenderer.getCamera();
        Vec3d base = hit.point().subtract(camera.getPos());
        var rotation = camera.getRotation().conjugate(new org.joml.Quaternionf());
        var local = new Vector3f((float) base.x, (float) base.y, (float) base.z).rotate(rotation);
        var normal = new Vector3f(hit.face().x, hit.face().y, hit.face().z).rotate(rotation);
        double focal = height / (2 * Math.tan(Math.toRadians(30)));
        double z = Math.max(1, local.z), endZ = Math.max(.1, local.z + normal.z);
        double sx = focal * (-((local.x + normal.x) / endZ) + local.x / z);
        double sy = focal * (-((local.y + normal.y) / endZ) + local.y / z);
        double length = Math.hypot(sx, sy);
        axisX = length > .5 ? sx / length : 0; axisY = length > .5 ? sy / length : -1;
        pixelsPerStep = MathHelper.clamp(length, 8, 40);
        status = "已选 " + selected.size() + " 个面 · 沿表面向外拖动，滚轮微调，Esc 取消";
    }
    private void updateExtrusion(double x, double y) {
        if (draft.extrusionSelection.isEmpty()) return;
        int steps = (int) Math.round(((x - extrusionStartX) * axisX + (y - extrusionStartY) * axisY) / pixelsPerStep) + scrollSteps;
        int max = SurfaceExtrusion.maxSteps(draft.extrusionSelection, draft.extrusionFace);
        if (draft.extrusionFace.y > 0) for (GridPos p : draft.extrusionSelection) max = Math.min(max, client.world.getTopY() - 1 - draft.worldPos(p).getY());
        if (draft.extrusionFace.y < 0) for (GridPos p : draft.extrusionSelection) max = Math.min(max, draft.worldPos(p).getY() - client.world.getBottomY());
        for (GridPos p : draft.extrusionSelection) {
            var world = draft.worldPos(p);
            if (draft.extrusionFace.x > 0) max = Math.min(max, GridWindow.WORLD_LIMIT - 1 - world.getX());
            if (draft.extrusionFace.x < 0) max = Math.min(max, world.getX() + GridWindow.WORLD_LIMIT);
            if (draft.extrusionFace.z > 0) max = Math.min(max, GridWindow.WORLD_LIMIT - 1 - world.getZ());
            if (draft.extrusionFace.z < 0) max = Math.min(max, world.getZ() + GridWindow.WORLD_LIMIT);
        }
        steps = MathHelper.clamp(steps, 0, Math.max(0, max));
        if (steps == extrusionSteps) return;
        try {
            draft.extrusionPreview = SurfaceExtrusion.plan(draft.blueprint.blocks(), draft.extrusionSelection, draft.extrusionFace, steps);
            extrusionSteps = steps;
            status = "拉伸 " + steps + " 格 / 新增 " + draft.extrusionPreview.size() + " 个方块 · 松开确认，Esc 取消";
        } catch (IllegalArgumentException e) { status = e.getMessage(); }
    }
}
