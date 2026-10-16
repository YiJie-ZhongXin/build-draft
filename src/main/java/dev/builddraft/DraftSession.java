package dev.builddraft;

import dev.builddraft.model.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import dev.builddraft.render.DraftWorldView;
import dev.builddraft.model.BuildGeometry.*;
import java.util.*;

public final class DraftSession {
    public static final int HOTBAR_SIZE = 12;
    public Blueprint blueprint = new Blueprint();
    public BlockPos origin;
    public double focusX, focusZ, zoom = 32;
    public double focusY;
    public float yaw = 0, pitch = 55;
    public boolean solidPreview;
    public float ghostAlpha = .45f;
    public boolean showGrid = true;
    public boolean controlsCollapsed = true;
    public int layer;
    public int gridX, gridZ;
    public BlockState brush = Blocks.OAK_PLANKS.getDefaultState();
    private final BlockState[] hotbar = {
        Blocks.OAK_PLANKS.getDefaultState(), Blocks.STONE.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
        Blocks.OAK_LOG.getDefaultState(), Blocks.GLASS.getDefaultState(), Blocks.OAK_STAIRS.getDefaultState(),
        Blocks.OAK_SLAB.getDefaultState(), Blocks.BRICKS.getDefaultState(), Blocks.WHITE_CONCRETE.getDefaultState(),
        Blocks.GLASS_PANE.getDefaultState(), Blocks.LANTERN.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()
    };
    private int selectedSlot;
    public boolean projection = true;
    public boolean showComplete;
    public boolean rectangleTool;
    public boolean extrusionTool;
    public Plane lockedPlane, rectanglePlane;
    public GridPos rectangleStart;
    public GridPos hovered;
    public GridPos placementPreview;
    public boolean placementValid;
    public boolean erasingPreview, bypassDraft;
    public boolean automaticPlacement = true;
    public BlockState placementState;
    public double viewportOffset;
    public DraftPicker.Selection selection;
    public Set<GridPos> extrusionSelection = Set.of();
    public Map<GridPos, BlockSpec> extrusionPreview = Map.of();
    public Face extrusionFace;
    public String name = "我的蓝图";
    private long cachedRevision = -1;
    private final Map<BlockSpec, BlockState> stateCache = new HashMap<>();
    private List<PlannedBlock> cachedBlocks = List.of();
    private DraftWorldView cachedView;
    private Blueprint viewBlueprint;
    private long viewRevision = -1;
    private BlockPos viewOrigin;
    public record PlannedBlock(GridPos relative, BlockState state) { }

    public DraftSession(BlockPos origin) { this.origin = origin.toImmutable(); center(); }
    public int selectedSlot() { return selectedSlot; }
    public BlockState hotbarState(int slot) { return slot == selectedSlot ? brush : hotbar[slot]; }
    public void selectSlot(int slot) {
        if (slot < 0 || slot >= hotbar.length) throw new IllegalArgumentException("无效快捷栏槽位");
        hotbar[selectedSlot] = brush;
        selectedSlot = slot;
        brush = hotbar[slot];
        placementState = null;
    }
    public void setSlot(int slot, BlockState state) {
        if (slot < 0 || slot >= hotbar.length || state == null || state.isAir()) throw new IllegalArgumentException("无效快捷栏方块");
        hotbar[slot] = state;
        if (slot == selectedSlot) brush = state;
        placementState = null;
    }
    public void cycleSlot(int delta) { selectSlot(Math.floorMod(selectedSlot + delta, hotbar.length)); }
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
    public void center() { focusX = origin.getX() + gridX + 16; focusZ = origin.getZ() + gridZ + 16; focusY = worldY() + .015; }
    public void moveOrigin(BlockPos position) { origin = position.toImmutable(); center(); }
    public void pan(double right, double forward) {
        var delta = OrbitMath.pan(right, forward, yaw);
        focusX = Math.max(-GridWindow.WORLD_LIMIT, Math.min(GridWindow.WORLD_LIMIT, focusX + delta.x()));
        focusZ = Math.max(-GridWindow.WORLD_LIMIT, Math.min(GridWindow.WORLD_LIMIT, focusZ + delta.z()));
    }
    public int worldY() { return origin.getY() + layer; }
    public BlockPos worldPos(GridPos p) { return origin.add(p.x(), p.y(), p.z()); }
    public void changeLayer(int delta) {
        moveGrid(gridX, worldY() + delta, gridZ);
    }
    public void setWorldY(int y) {
        moveGrid(gridX, y, gridZ);
        focusY = worldY() + .015;
    }
    public BlockPos gridOrigin() { return origin.add(gridX, layer, gridZ); }
    public void moveGrid(int localX, int y, int localZ) {
        var world = MinecraftClient.getInstance().world;
        var window = GridWindow.atWorld(origin.getX() + localX, origin.getZ() + localZ);
        gridX = window.x() - origin.getX(); gridZ = window.z() - origin.getZ();
        int bottom = world == null ? Blueprint.MIN_Y : Math.max(Blueprint.MIN_Y, world.getBottomY() - origin.getY());
        int top = world == null ? Blueprint.MAX_Y - 1 : Math.min(Blueprint.MAX_Y - 1, world.getTopY() - 1 - origin.getY());
        layer = Math.max(bottom, Math.min(top, y - origin.getY()));
        lockedPlane = new Plane(1, layer);
        cancelGesture();
        showGrid = true; placementPreview = null; placementState = null;
    }
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
    public void cancelGesture() {
        rectangleStart = null; rectanglePlane = null;
        extrusionSelection = Set.of(); extrusionPreview = Map.of(); extrusionFace = null;
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
        cancelGesture();
    }

    public void paint(GridPos pos, boolean erase) {
        if (pos == null || !blueprint.contains(pos)) return;
        BlockSpec spec = erase ? null : BlockStates.describe(placementState == null ? brush : placementState);
        if (!rectangleTool) { blueprint.put(pos, spec); return; }
        if (rectangleStart == null) { rectangleStart = pos; return; }
        var plane = rectanglePlane == null ? new Plane(1, rectangleStart.y()) : rectanglePlane;
        Map<GridPos, BlockSpec> edits = BuildGeometry.rectangle(rectangleStart, pos, plane, spec);
        blueprint.edit(edits);
        rectangleStart = null; rectanglePlane = null;
    }
}
