package dev.builddraft.screen;

import dev.builddraft.DraftSession;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Twelve compact material slots using the same container theme as the block library. */
public final class DraftHotbar {
    public static final int WIDTH = 4 + DraftSession.HOTBAR_SIZE * 20, HEIGHT = 24;
    private DraftHotbar() { }

    public static void render(DrawContext draw, DraftSession draft, int x, int y) {
        DraftUi.panel(draw, x, y, WIDTH, HEIGHT);
        for (int i = 0; i < DraftSession.HOTBAR_SIZE; i++) DraftUi.slot(draw, x + 3 + i * 20, y + 3, 18, 18);
        draw.drawBorder(x + 2 + draft.selectedSlot() * 20, y + 2, 20, 20, DraftUi.ACCENT);
    }
    public static boolean contains(double x, double y, int left, int top) {
        return x >= left && x < left + WIDTH && y >= top && y < top + HEIGHT;
    }
    public static List<ButtonWidget> slots(DraftSession draft, int x, int y, Runnable selected, Runnable replace) {
        List<ButtonWidget> widgets = new ArrayList<>();
        for (int i = 0; i < DraftSession.HOTBAR_SIZE; i++) widgets.add(new Slot(draft, i, x + 3 + i * 20, y + 3, selected, replace));
        return widgets;
    }
    public static int keySlot(int key) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) return key - GLFW.GLFW_KEY_1;
        return switch (key) { case GLFW.GLFW_KEY_0 -> 9; case GLFW.GLFW_KEY_MINUS -> 10; case GLFW.GLFW_KEY_EQUAL -> 11; default -> -1; };
    }
    public static String keyLabel(int slot) { return slot < 9 ? Integer.toString(slot + 1) : slot == 9 ? "0" : slot == 10 ? "-" : "="; }
    private static final class Slot extends ButtonWidget {
        private final DraftSession draft;
        private final int index;
        private final Runnable selected, replace;
        private net.minecraft.block.BlockState shownState;
        Slot(DraftSession draft, int index, int x, int y, Runnable selected, Runnable replace) {
            super(x, y, 18, 18, Text.literal("快捷栏 " + (index + 1)), b -> { draft.selectSlot(index); selected.run(); }, DEFAULT_NARRATION_SUPPLIER);
            this.draft = draft; this.index = index; this.selected = selected; this.replace = replace;
        }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button == 1 && active && visible && clicked(x, y)) {
                draft.selectSlot(index); selected.run();
                if (replace != null) replace.run();
                return true;
            }
            return super.mouseClicked(x, y, button);
        }
        @Override protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            var state = draft.hotbarState(index);
            String name = state.getBlock().getName().getString();
            if (state != shownState) {
                shownState = state;
                setMessage(Text.literal((index + 1) + " · " + name));
                setTooltip(Tooltip.of(Text.literal((index + 1) + " · " + name + "  [" + keyLabel(index) + "]\n左键选用 · 右键换方块\n在快捷栏上滚轮切换全部 12 格")));
            }
            if (isHovered() || isFocused()) draw.fill(getX() + 1, getY() + 1, getX() + 17, getY() + 17, 0x80ffffff);
            draw.drawItem(BlockPaletteScreen.icon(state.getBlock()), getX() + 1, getY() + 1);
        }
    }
}
