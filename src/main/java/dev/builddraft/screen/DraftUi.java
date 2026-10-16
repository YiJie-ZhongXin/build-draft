package dev.builddraft.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Warm drafting paper, graphite rules and blue-pencil accents shared by every work sheet. */
public final class DraftUi {
    public static final int BG = 0x88342b24, PANEL = 0xfff7f0de, BORDER = 0xffa18c6e;
    public static final int TEXT = 0xff514a3f, MUTED = 0xff7c705e, ACCENT = 0xff527a83;
    public static final int HUD_TEXT = 0xfffff3d7;
    private static final int GRID = 0xffe4e8de, MAJOR_GRID = 0xffd8e0d7, EDGE = 0xffded0b5;
    public static void panel(DrawContext draw, int x, int y, int w, int h) {
        draw.fill(x + 2, y + 3, x + w + 2, y + h + 3, 0x44382d20);
        draw.fill(x, y, x + w, y + h, PANEL);
        if (h >= 40) {
            for (int gx = 12; gx < w - 3; gx += 12) draw.fill(x + gx, y + 2, x + gx + 1, y + h - 2, gx % 60 == 0 ? MAJOR_GRID : GRID);
            for (int gy = 12; gy < h - 3; gy += 12) draw.fill(x + 2, y + gy, x + w - 2, y + gy + 1, gy % 60 == 0 ? MAJOR_GRID : GRID);
        }
        draw.drawBorder(x, y, w, h, BORDER);
        draw.fill(x + 1, y + 1, x + w - 1, y + 2, 0xfffff9ec);
        draw.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, EDGE);
        if (w > 80 && h > 60) {
            draw.drawBorder(x + 4, y + 4, w - 8, h - 8, EDGE);
            // Tiny drafting registration marks, kept inside the paper margin.
            draw.fill(x + 5, y + 5, x + 10, y + 6, ACCENT);
            draw.fill(x + 5, y + 5, x + 6, y + 10, ACCENT);
            draw.fill(x + w - 10, y + h - 6, x + w - 5, y + h - 5, ACCENT);
            draw.fill(x + w - 6, y + h - 10, x + w - 5, y + h - 5, ACCENT);
        }
        if (w > 200 && h > 100) {
            for (int n = 0; n < 7; n++) draw.fill(x + w - 8 + n, y + 1, x + w - 1, y + 2 + n, 0xffe8dac0);
            draw.fill(x + w - 8, y + 8, x + w - 1, y + 9, BORDER);
        }
    }
    public static void slot(DrawContext draw, int x, int y, int w, int h) {
        draw.fill(x, y, x + w, y + h, 0xffeee4cf);
        draw.drawBorder(x, y, w, h, 0xffb4a48a);
        draw.fill(x + 1, y + 1, x + w - 1, y + 2, 0xffdfd1b8);
        draw.fill(x + 1, y + h - 1, x + w, y + h, 0xfffff9ec);
    }
    public static void page(DrawContext draw, String title, String subtitle, int width, int height) {
        var font = MinecraftClient.getInstance().textRenderer;
        draw.fill(0, 0, width, height, BG);
        panel(draw, 4, 4, width - 8, height - 8);
        centered(draw, title, width / 2, 16, TEXT);
        draw.fill(width / 2 - 66, 29, width / 2 + 66, 30, EDGE);
        if (width >= 520) draw.drawText(font, "BUILD DRAFT / 建筑手札", width - 156, height - 15, MUTED, false);
        if (!subtitle.isEmpty()) centered(draw, font.trimToWidth(subtitle, width - 32), width / 2, 31, MUTED);
    }
    public static void centered(DrawContext draw, String text, int centerX, int y, int color) {
        var font = MinecraftClient.getInstance().textRenderer;
        draw.drawText(font, text, centerX - font.getWidth(text) / 2, y, color, false);
    }
    public static Button button(int x, int y, int w, String title, Runnable action) {
        return new Button(x, y, w, 20, () -> title, () -> false, action);
    }
    public static final class Field extends TextFieldWidget {
        public Field(TextRenderer font, int x, int y, int w, int h, Text label) {
            super(font, x, y, w, h, label);
            setEditableColor(TEXT); setUneditableColor(MUTED);
        }
        @Override public int getInnerWidth() { return Math.max(0, getWidth() - 8); }
        @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            draw.fill(getX(), getY(), getX() + width, getY() + height, 0xfffffaed);
            draw.drawBorder(getX(), getY(), width, height, isFocused() ? ACCENT : BORDER);
            draw.fill(getX() + 2, getY() + height - 3, getX() + width - 2, getY() + height - 2, EDGE);
            draw.getMatrices().push();
            draw.getMatrices().translate(4, (height - 8) / 2f, 0);
            setDrawsBackground(false);
            try { super.renderButton(draw, mouseX, mouseY, delta); }
            finally { setDrawsBackground(true); draw.getMatrices().pop(); }
        }
    }
    public abstract static class Slider extends SliderWidget {
        public Slider(int x, int y, int w, int h, Text label, double value) { super(x, y, w, h, label, value); }
        @Override public void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            draw.fill(getX(), getY(), getX() + width, getY() + height, 0xffeee4cf);
            draw.drawBorder(getX(), getY(), width, height, isHovered() || isFocused() ? ACCENT : BORDER);
            int knob = getX() + 4 + (int) ((width - 8) * value);
            draw.fill(getX() + 4, getY() + height - 5, getX() + width - 4, getY() + height - 4, BORDER);
            draw.fill(knob - 3, getY() + height - 7, knob + 3, getY() + height - 2, ACCENT);
            centered(draw, getMessage().getString(), getX() + width / 2, getY() + 3, TEXT);
        }
    }
    public static final class Button extends ButtonWidget {
        private final Supplier<String> label;
        private final BooleanSupplier selected;
        public Button(int x, int y, int w, int h, Supplier<String> label, BooleanSupplier selected, Runnable action) {
            super(x, y, w, h, Text.literal(label.get()), b -> action.run(), DEFAULT_NARRATION_SUPPLIER);
            this.label = label; this.selected = selected;
        }
        public Button hint(String description) { setTooltip(Tooltip.of(Text.literal(description))); return this; }
        @Override protected void renderButton(DrawContext draw, int mouseX, int mouseY, float delta) {
            setMessage(Text.literal(label.get()));
            boolean on = selected.getAsBoolean();
            int background = !active ? 0xffe5ddcc : on ? 0xffd6e5de : isHovered() || isFocused() ? 0xfff1dfb5 : 0xffeee4cd;
            draw.fill(getX(), getY(), getX() + width, getY() + height, background);
            draw.drawBorder(getX(), getY(), width, height, on ? ACCENT : BORDER);
            draw.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + 2, 0xfffff8e6);
            draw.fill(getX() + 1, getY() + height - 2, getX() + width - 1, getY() + height - 1, EDGE);
            if (on) draw.fill(getX() + 2, getY() + 3, getX() + 4, getY() + height - 3, ACCENT);
            var font = MinecraftClient.getInstance().textRenderer;
            String text = font.trimToWidth(getMessage().getString(), width - 8);
            centered(draw, text, getX() + width / 2, getY() + (height - 8) / 2, !active ? MUTED : on ? ACCENT : TEXT);
        }
    }
}
