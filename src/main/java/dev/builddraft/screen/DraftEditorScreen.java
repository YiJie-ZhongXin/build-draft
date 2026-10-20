package dev.builddraft.screen;

import dev.builddraft.*;
import dev.builddraft.model.FreeCameraMath;
import dev.builddraft.model.GridPos;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import org.lwjgl.glfw.GLFW;

/** First-person free flight and crosshair editing; Tab releases the cursor for material selection. */
public final class DraftEditorScreen extends Screen implements DraftScreen {
    private final DraftSession draft;
    private boolean mouseLook = true;
    private final java.util.Set<Integer> movementTaps = new java.util.HashSet<>();
    private int paintButton = -1;
    private long lastPaint;
    private GridPos lastPainted;
    private String status = "右键添加 · 左键删除 · 中键吸取";
    private boolean confirmClear;
    private net.minecraft.client.gui.widget.ButtonWidget undoButton, clearButton, cancelClear;

    public DraftEditorScreen(DraftSession draft) {
        super(Text.literal("筑稿 · 自由贴面建造"));
        this.draft = draft;
    }
    @Override protected void init() {
        if (!draft.cameraReady) draft.returnToPlayer();
        mouseLook = true;
        movementTaps.clear();
        stopPainting();
        KeyBinding.unpressAll();
        confirmClear = false;
        DraftHotbar.slots(draft, hotbarX(), hotbarY(), this::selectedMaterial, this::palette).forEach(this::addDrawableChild);
        addDrawableChild(DraftUi.button(hotbarX() + DraftHotbar.WIDTH + 6, hotbarY() + 2, 20, "+", this::palette));
        addDrawableChild(DraftUi.button(width - 62, 26, 54, "草稿", this::library));
        undoButton = addDrawableChild(DraftUi.button(width - 76, 50, 68, "撤销上一步", this::undo));
        clearButton = addDrawableChild(new DraftUi.Button(width - 76, 74, 68, 20,
            () -> confirmClear ? "确认全部删除" : "全部删除", () -> confirmClear, this::clear));
        cancelClear = addDrawableChild(DraftUi.button(width - 76, 98, 68, "取消删除", () -> confirmClear = false));
        cancelClear.visible = false;
    }
    public boolean mouseLook() { return mouseLook; }
    public void look(double dx, double dy) {
        draft.yaw = FreeCameraMath.wrapYaw(draft.yaw + dx);
        draft.pitch = FreeCameraMath.clampPitch(draft.pitch + dy);
    }
    private void setMouseLook(boolean value) {
        mouseLook = value;
        confirmClear = false;
        movementTaps.clear();
        stopPainting();
        KeyBinding.unpressAll();
        ((DraftMouseControl) client.mouse).builddraft$setCaptured(value);
        setFocused(null);
    }
    private void stopPainting() { paintButton = -1; lastPainted = null; }
    private void undo() {
        stopPainting(); confirmClear = false;
        status = draft.blueprint.undo() ? "已撤销上一步" : "没有可撤销的操作";
    }
    private void clear() {
        stopPainting();
        if (!confirmClear) { confirmClear = true; status = "再次点击确认全部删除；删除后可撤销恢复"; return; }
        draft.blueprint.clear(); confirmClear = false;
        status = "已全部删除 · 撤销上一步或 Ctrl+Z 可恢复";
    }
    private boolean down(int key) { return movementTaps.contains(key) || InputUtil.isKeyPressed(client.getWindow().getHandle(), key); }
    private boolean movementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D
            || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
            || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL;
    }
    @Override public void tick() {
        if (!client.isWindowFocused() || client.getOverlay() != null) {
            if (mouseLook) setMouseLook(false);
            stopPainting();
        }
        if (mouseLook && !client.mouse.isCursorLocked()) ((DraftMouseControl) client.mouse).builddraft$setCaptured(true);
        double right = mouseLook ? (down(GLFW.GLFW_KEY_D) ? 1 : 0) - (down(GLFW.GLFW_KEY_A) ? 1 : 0) : 0;
        double forward = mouseLook ? (down(GLFW.GLFW_KEY_W) ? 1 : 0) - (down(GLFW.GLFW_KEY_S) ? 1 : 0) : 0;
        double up = mouseLook ? (down(GLFW.GLFW_KEY_SPACE) ? 1 : 0)
            - (down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT) ? 1 : 0) : 0;
        draft.fly(right, forward, up, mouseLook && (down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL)));
        movementTaps.clear();
        if (mouseLook && paintButton >= 0 && System.currentTimeMillis() - lastPaint >= 180) { lastPainted = null; paint(paintButton); }
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() {
        ((DraftMouseControl) client.mouse).builddraft$setCaptured(false);
        KeyBinding.unpressAll();
        movementTaps.clear();
        stopPainting();
        draft.hovered = null; draft.selection = null; draft.placementPreview = null; draft.placementState = null;
        draft.placementValid = false; draft.bypassDraft = false;
        draft.stopCamera();
        BuildDraftClient.autosave();
        DraftKeys.clearPending();
    }
    @Override public void close() { client.setScreen(null); }
    private void palette() { client.setScreen(new BlockPaletteScreen(this, draft)); }
    private void library() { client.setScreen(new DraftLibraryScreen(this, draft, BuildDraftClient.library())); }
    private int hotbarX() { return width / 2 - DraftHotbar.WIDTH / 2; }
    private int hotbarY() { return height - 28; }
    void selectedMaterial() {
        stopPainting();
        draft.rememberMaterial(draft.brush);
        status = "选材 " + (draft.selectedSlot() + 1) + " · " + draft.brush.getBlock().getName().getString();
    }
    private void refreshHover() {
        draft.hovered = null; draft.selection = null; draft.placementPreview = null; draft.placementState = null; draft.placementValid = false;
        draft.bypassDraft = mouseLook && hasAltDown();
        draft.erasingPreview = paintButton == 0;
        if (!mouseLook || !client.isWindowFocused()) return;
        draft.selection = DraftPicker.pick(draft, DraftPicker.ray(draft, width / 2.0, height / 2.0, width, height), draft.bypassDraft);
        if (draft.selection == null) return;
        draft.hovered = draft.selection.block();
        if (draft.erasingPreview) {
            draft.placementPreview = draft.selection.virtual() ? draft.selection.block() : null;
            draft.placementValid = draft.validPosition(draft.placementPreview) && draft.blueprint.blocks().containsKey(draft.placementPreview);
            return;
        }
        var placement = DraftPlacement.resolve(draft, draft.selection);
        draft.placementPreview = placement == null ? draft.selection.placement() : placement.position();
        draft.placementState = placement == null ? null : placement.state();
        draft.placementValid = placement != null;
    }
    private void paint(int button) {
        refreshHover();
        GridPos pos = draft.placementPreview;
        if (draft.placementValid && pos != null && !pos.equals(lastPainted)) {
            try { draft.paint(pos, button == 0); }
            catch (IllegalArgumentException e) { status = e.getMessage(); }
            lastPainted = pos;
        }
        lastPaint = System.currentTimeMillis();
    }
    private void sample() {
        refreshHover();
        if (draft.hovered == null) return;
        var spec = draft.blueprint.blocks().get(draft.hovered);
        var state = spec == null ? client.world.getBlockState(draft.worldPos(draft.hovered)) : BlockStates.resolve(spec);
        if (!state.isAir()) { draft.brush = state; draft.automaticPlacement = false; selectedMaterial(); }
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (!client.isWindowFocused()) return true;
        if (!mouseLook) {
            if (super.mouseClicked(x, y, button)) return true;
            if (dispatch(binding -> binding.matchesMouse(button))) return true;
            setMouseLook(true); // The click only resumes flight; it must not also place a block.
            return true;
        }
        if (dispatch(binding -> binding.matchesMouse(button))) return true;
        if (button == 2) { sample(); return true; }
        if (button == 0 || button == 1) {
            paintButton = button; lastPainted = null; paint(button); return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return mouseLook || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (paintButton == button) stopPainting();
        return mouseLook || super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double amount) {
        if (mouseLook || DraftHotbar.contains(x, y, hotbarX(), hotbarY())) {
            if (amount != 0) { draft.cycleSlot(amount > 0 ? -1 : 1); selectedMaterial(); }
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_TAB) { setMouseLook(!mouseLook); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { if (confirmClear) confirmClear = false; else close(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Z) { undo(); return true; }
        if (hasControlDown() && key == GLFW.GLFW_KEY_Y) { stopPainting(); draft.blueprint.redo(); return true; }
        if (dispatch(binding -> binding.matchesKey(key, scan))) return true;
        if (DraftHotbar.keySlot(key) >= 0 && !hasControlDown() && !hasAltDown() && !hasShiftDown()) {
            draft.selectSlot(DraftHotbar.keySlot(key)); selectedMaterial(); return true;
        }
        if (movementKey(key)) {
            // Keep a very short tap until one tick consumes it; held movement still polls GLFW.
            if (mouseLook) movementTaps.add(key);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
    private boolean dispatch(java.util.function.Predicate<KeyBinding> matches) {
        if (matches.test(DraftKeys.EDITOR)) close();
        else if (matches.test(DraftKeys.PALETTE)) palette();
        else if (matches.test(DraftKeys.LIBRARY)) library();
        else if (matches.test(DraftKeys.CENTER)) { stopPainting(); draft.returnToPlayer(); }
        else if (matches.test(DraftKeys.PREVIEW)) draft.solidPreview = !draft.solidPreview;
        else if (matches.test(DraftKeys.ROTATE)) {
            if (hasShiftDown()) draft.automaticPlacement = true;
            else { draft.brush = draft.brush.rotate(BlockRotation.CLOCKWISE_90); draft.automaticPlacement = false; }
            status = draft.automaticPlacement ? "自动朝向" : "手动朝向 · Shift+R 恢复自动";
        } else if (matches.test(DraftKeys.SAMPLE)) sample();
        else return false;
        return true;
    }
    @Override public void render(DrawContext draw, int mouseX, int mouseY, float delta) {
        undoButton.active = draft.blueprint.canUndo();
        clearButton.active = !draft.blueprint.blocks().isEmpty(); cancelClear.visible = confirmClear;
        refreshHover();
        if (mouseLook) {
            int cx = width / 2, cy = height / 2;
            draw.fill(cx - 4, cy, cx + 5, cy + 1, 0xfffaf3df);
            draw.fill(cx, cy - 4, cx + 1, cy + 5, 0xfffaf3df);
        }
        String info = (mouseLook ? "自由飞行" : "选材 · Tab 返回飞行") + " · " + (draft.solidPreview ? "实景" : "虚影")
            + " · " + String.format(java.util.Locale.ROOT, "%.1f", draft.flySpeed) + " 格/秒 · " + draft.blueprint.blocks().size() + " 格";
        draw.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(info, width - 16), 8, 8, DraftUi.HUD_TEXT);
        String hint = mouseLook ? "WASD 飞行 · 空格/Shift 升降 · Ctrl 加速 · 滚轮选块 · Tab 鼠标 · " + DraftKeys.label(DraftKeys.PALETTE) + " 选块"
            : "点击快捷槽选材 · 右键换材 · 草稿管理 " + DraftKeys.label(DraftKeys.LIBRARY) + " · Tab 返回飞行";
        draw.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(hint, width - 16), width / 2, height - 54, DraftUi.HUD_TEXT);
        String material = (draft.selectedSlot() + 1) + " · " + draft.brush.getBlock().getName().getString() + " · " + status;
        draw.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(material, width - 16), width / 2, height - 40, DraftUi.HUD_TEXT);
        DraftHotbar.render(draw, draft, hotbarX(), hotbarY());
        super.render(draw, mouseLook ? -10000 : mouseX, mouseLook ? -10000 : mouseY, delta);
    }
}
