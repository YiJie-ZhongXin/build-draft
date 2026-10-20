package dev.builddraft;

import dev.builddraft.model.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import dev.builddraft.render.DraftWorldView;
import java.util.*;

public final class DraftSession {
    public static final int HOTBAR_SIZE = 12;
    public Blueprint blueprint = new Blueprint();
    public BlockPos origin;
    public double cameraX, cameraY, cameraZ, previousCameraX, previousCameraY, previousCameraZ;
    public double flySpeed = 24;
    public boolean cameraReady;
    public float yaw, pitch;
    public boolean solidPreview = true;
    public float ghostAlpha = .45f;
    public BlockState brush = Blocks.OAK_PLANKS.getDefaultState();
    private final BlockState[] hotbar = {
        Blocks.OAK_PLANKS.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
        Blocks.OAK_LOG.getDefaultState(), Blocks.GLASS.getDefaultState(), Blocks.OAK_STAIRS.getDefaultState(),
        Blocks.OAK_SLAB.getDefaultState(), Blocks.BRICKS.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState(),
        Blocks.GLASS_PANE.getDefaultState(), Blocks.LANTERN.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()
    };
    private int selectedSlot;
    public final RecentMaterials recentMaterials = new RecentMaterials();
    public BlockCategoryRules.Material paletteMaterial = BlockCategoryRules.Material.ALL;
    public BlockCategoryRules.Shape paletteShape = BlockCategoryRules.Shape.ALL;
    public BlockCategoryRules.Source paletteSource = BlockCategoryRules.Source.ALL;
    public boolean paletteSpecial;
    public String paletteQuery = "";
    public GridPos hovered;
    public GridPos placementPreview;
    public boolean placementValid;
    public boolean erasingPreview, bypassDraft;
    public boolean automaticPlacement = true;
    public BlockState placementState;
    public DraftPicker.Selection selection;
    private long cachedRevision = -1;
    private final Map<BlockSpec, BlockState> stateCache = new HashMap<>();
    private List<PlannedBlock> cachedBlocks = List.of();
    private DraftWorldView cachedView;
    private Blueprint viewBlueprint;
    private long viewRevision = -1;
    private BlockPos viewOrigin;
    public record PlannedBlock(GridPos relative, BlockState state) { }

    public DraftSession(BlockPos origin) { this.origin = origin.toImmutable(); }
    public int selectedSlot() { return selectedSlot; }
    public BlockState hotbarState(int slot) { return slot == selectedSlot ? brush : hotbar[slot]; }
    public void selectSlot(int slot) {
        selectSlot(slot, true);
    }
    private void selectSlot(int slot, boolean remember) {
        if (slot < 0 || slot >= hotbar.length) throw new IllegalArgumentException("无效快捷栏槽位");
        hotbar[selectedSlot] = brush;
        selectedSlot = slot;
        brush = hotbar[slot];
        placementState = null;
        if (remember) rememberMaterial(brush);
    }
    public void setSlot(int slot, BlockState state) {
        if (slot < 0 || slot >= hotbar.length || state == null || state.isAir()) throw new IllegalArgumentException("无效快捷栏方块");
        hotbar[slot] = state;
        if (slot == selectedSlot) brush = state;
        placementState = null;
        rememberMaterial(state);
    }
    public void cycleSlot(int delta) { selectSlot(Math.floorMod(selectedSlot + delta, hotbar.length)); }
    public void advanceSlot() { selectSlot((selectedSlot + 1) % hotbar.length, false); }
    public void rememberMaterial(BlockState state) {
        if (state != null && !state.isAir()) recentMaterials.use(net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString());
    }
    public void importPlayerHotbar() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        for (int i = 0; i < Math.min(9, hotbar.length); i++) {
            var item = player.getInventory().getStack(i).getItem();
            if (item instanceof net.minecraft.item.BlockItem block) hotbar[i] = block.getBlock().getDefaultState();
        }
        selectedSlot = player.getInventory().selectedSlot;
        brush = hotbar[selectedSlot];
    }
    public void returnToPlayer() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        var eye = player.getEyePos();
        cameraX = eye.x; cameraY = eye.y; cameraZ = eye.z;
        yaw = FreeCameraMath.wrapYaw(player.getYaw()); pitch = FreeCameraMath.clampPitch(player.getPitch());
        cameraReady = true;
        stopCamera();
    }
    public void stopCamera() { previousCameraX = cameraX; previousCameraY = cameraY; previousCameraZ = cameraZ; }
    public void focusDraft() {
        if (blueprint.blocks().isEmpty()) return;
        var xs = blueprint.blocks().keySet().stream().mapToInt(GridPos::x).summaryStatistics();
        var ys = blueprint.blocks().keySet().stream().mapToInt(GridPos::y).summaryStatistics();
        var zs = blueprint.blocks().keySet().stream().mapToInt(GridPos::z).summaryStatistics();
        double distance = Math.max(8, Math.min(64, Math.max(xs.getMax() - xs.getMin(), zs.getMax() - zs.getMin()) + 4));
        cameraX = origin.getX() + (xs.getMin() + xs.getMax()) / 2.0 + distance;
        cameraY = origin.getY() + ys.getMax() + distance;
        cameraZ = origin.getZ() + (zs.getMin() + zs.getMax()) / 2.0 + distance;
        yaw = 135; pitch = 35; cameraReady = true; stopCamera();
    }
    public void fly(double right, double forward, double up, boolean fast) {
        stopCamera();
        var delta = FreeCameraMath.step(right, forward, up, yaw, pitch, flySpeed * (fast ? 3 : 1), .05);
        cameraX = Math.max(-GridWindow.WORLD_LIMIT, Math.min(GridWindow.WORLD_LIMIT, cameraX + delta.x()));
        cameraY += delta.y();
        cameraZ = Math.max(-GridWindow.WORLD_LIMIT, Math.min(GridWindow.WORLD_LIMIT, cameraZ + delta.z()));
    }
    public BlockPos worldPos(GridPos p) { return origin.add(p.x(), p.y(), p.z()); }
    public DraftWorldView worldView() {
        if (cachedView == null || viewBlueprint != blueprint || viewRevision != blueprint.revision() || !origin.equals(viewOrigin)) {
            cachedView = new DraftWorldView(this, MinecraftClient.getInstance().world);
            viewBlueprint = blueprint; viewRevision = blueprint.revision(); viewOrigin = origin;
        }
        return cachedView;
    }
    public boolean validPosition(GridPos pos) {
        var world = MinecraftClient.getInstance().world;
        return blueprint.contains(pos) && world != null && worldPos(pos).getX() >= -GridWindow.WORLD_LIMIT && worldPos(pos).getX() < GridWindow.WORLD_LIMIT
            && worldPos(pos).getZ() >= -GridWindow.WORLD_LIMIT && worldPos(pos).getZ() < GridWindow.WORLD_LIMIT && worldPos(pos).getY() >= world.getBottomY() && worldPos(pos).getY() < world.getTopY();
    }
    public List<PlannedBlock> plannedBlocks() {
        if (cachedRevision != blueprint.revision()) {
            stateCache.clear();
            cachedBlocks = blueprint.blocks().entrySet().stream()
                .map(e -> new PlannedBlock(e.getKey(), stateCache.computeIfAbsent(e.getValue(), BlockStates::resolve))).toList();
            cachedRevision = blueprint.revision();
        }
        return cachedBlocks;
    }

    public void load(Blueprint loaded) {
        loaded.blocks().values().stream().distinct().forEach(BlockStates::resolve);
        blueprint = loaded;
        cachedRevision = -1;
        hovered = null; selection = null; placementPreview = null; placementState = null; placementValid = false;
    }

    public void paint(GridPos pos, boolean erase) {
        if (pos == null || !blueprint.contains(pos)) return;
        BlockSpec spec = erase ? null : BlockStates.describe(placementState == null ? brush : placementState);
        blueprint.put(pos, spec);
        if (!erase) rememberMaterial(placementState == null ? brush : placementState);
    }
}
