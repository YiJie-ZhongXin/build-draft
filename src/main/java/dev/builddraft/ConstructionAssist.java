package dev.builddraft;

import dev.builddraft.model.UseGesture;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import org.lwjgl.glfw.GLFW;

/** One physical gesture submits one whole-draft material fill to the integrated server. */
public final class ConstructionAssist {
    private static final UseGesture GESTURE = new UseGesture();
    private static boolean inFlight, suppressHeldUse;
    private static long epoch, statusUntil;
    private static String status = "主手拿对应方块，辅助键+使用键一次填满整份草稿的同种材料";

    private static boolean eligible() {
        var client = MinecraftClient.getInstance();
        return ConstructionProjection.active() && client.world != null && client.currentScreen == null && client.isWindowFocused()
            && client.player != null && client.player.isAlive() && !client.player.isSpectator() && client.interactionManager != null;
    }
    private static boolean down(KeyBinding binding) {
        if (binding.isPressed()) return true;
        var key = KeyBindingHelper.getBoundKeyOf(binding);
        if (key.getCode() < 0) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        if (key.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
        return key.getCategory() == InputUtil.Type.KEYSYM && InputUtil.isKeyPressed(window, key.getCode());
    }
    public static boolean engaged() { return eligible() && (suppressHeldUse || down(DraftKeys.ASSIST) || GESTURE.pending(System.nanoTime())); }
    public static void captureKey(int key, int scan, int action) {
        if (!eligible() || action != GLFW.GLFW_PRESS) return;
        var client = MinecraftClient.getInstance();
        if (client.options.useKey.matchesKey(key, scan)) captureUse(down(DraftKeys.ASSIST));
        else if (DraftKeys.ASSIST.matchesKey(key, scan) && down(client.options.useKey)) captureUse(true);
    }
    public static void captureMouse(int button, int action) {
        if (!eligible() || action != GLFW.GLFW_PRESS) return;
        var client = MinecraftClient.getInstance();
        if (client.options.useKey.matchesMouse(button)) captureUse(down(DraftKeys.ASSIST));
        else if (DraftKeys.ASSIST.matchesMouse(button) && down(client.options.useKey)) captureUse(true);
    }
    private static void captureUse(boolean assisted) {
        if (assisted) { suppressHeldUse = true; GESTURE.capture(System.nanoTime(), true, true); }
        else { suppressHeldUse = false; GESTURE.clear(); }
    }
    public static void fill() {
        if (!eligible() || !GESTURE.consume(System.nanoTime()) || inFlight) return;
        var client = MinecraftClient.getInstance();
        var server = client.getServer();
        if (server == null) { tell("整份草稿一键填充需要服务端支持；当前版本支持单人世界"); return; }
        var stack = client.player.getMainHandStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) { tell("请先在主手拿对应方块材料"); return; }
        if (!ConstructionFill.usable(stack, stack.getItem())) { tell("带方块状态或方块实体数据的材料请手工使用"); return; }
        try {
            var draft = ConstructionProjection.draft();
            var item = stack.getItem();
            // Freeze the client draft here; the server task never reads DraftSession or client world.
            var targets = draft.plannedBlocks().stream().filter(block -> block.state().getBlock().asItem() == item)
                .map(block -> new ConstructionFill.Target(draft.worldPos(block.relative()), block.state()))
                .sorted(java.util.Comparator.comparingInt((ConstructionFill.Target target) -> target.position().getY())
                    .thenComparingInt(target -> target.position().getX()).thenComparingInt(target -> target.position().getZ())).toList();
            if (targets.isEmpty()) { tell("草稿中没有这种材料"); return; }
            var playerId = client.player.getUuid();
            var dimension = client.world.getRegistryKey();
            long requestEpoch = epoch;
            inFlight = true;
            tell("正在一次填充整份草稿的 " + item.getName().getString() + "…");
            server.execute(() -> {
                String result;
                try {
                    var player = server.getPlayerManager().getPlayer(playerId);
                    result = player == null || !player.getServerWorld().getRegistryKey().equals(dimension)
                        ? "玩家已离开原世界，未执行填充" : ConstructionFill.apply(player, item, targets);
                } catch (RuntimeException e) {
                    BuildDraftClient.LOGGER.warn("Whole-draft construction fill failed", e);
                    result = "填充异常，已成功的位置按实际数量扣料，请检查世界";
                }
                String message = result;
                client.execute(() -> { if (epoch == requestEpoch) { inFlight = false; tell(message); } });
            });
        } catch (RuntimeException e) {
            inFlight = false;
            tell("无法读取施工草稿，请检查方块和投影");
            BuildDraftClient.LOGGER.warn("Could not submit whole-draft fill", e);
        }
    }
    public static void tick() {
        if (!eligible()) { GESTURE.clear(); suppressHeldUse = false; }
        else if (!down(MinecraftClient.getInstance().options.useKey) && !GESTURE.pending(System.nanoTime())) suppressHeldUse = false;
    }
    private static void tell(String message) { status = message; statusUntil = System.nanoTime() + 8_000_000_000L; }
    public static void hud(DrawContext draw) {
        var client = MinecraftClient.getInstance();
        if (!ConstructionProjection.active() || client.currentScreen != null || client.options.hudHidden) return;
        String label = "施工 · " + ConstructionProjection.name() + " · " + DraftKeys.label(DraftKeys.ASSIST) + "+" + DraftKeys.label(client.options.useKey)
            + " 一键填满同材质 · " + DraftKeys.label(DraftKeys.LIBRARY) + " 草稿";
        draw.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(label, draw.getScaledWindowWidth() - 16), 8, 8, 0xfffff3d7);
        if (inFlight || down(DraftKeys.ASSIST) || System.nanoTime() < statusUntil)
            draw.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(status, draw.getScaledWindowWidth() - 16), 8, 22, 0xfffff3d7);
    }
    public static void reset() {
        epoch++; inFlight = false; suppressHeldUse = false; GESTURE.clear();
        tell("主手拿对应方块，辅助键+使用键一次填满整份草稿的同种材料");
    }
}
