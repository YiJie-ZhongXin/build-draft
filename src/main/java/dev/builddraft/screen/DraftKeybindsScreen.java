package dev.builddraft.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;

public final class DraftKeybindsScreen extends KeybindsScreen implements DraftScreen {
    public DraftKeybindsScreen(Screen parent) { super(parent, MinecraftClient.getInstance().options); }
    @Override public boolean shouldPause() { return false; }
}
