package dev.builddraft;

import dev.builddraft.model.*;
import dev.builddraft.model.BuildGeometry.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;

/** Real terrain and virtual shape raycasts share the same camera ray. */
public final class DraftPicker {
    public record Selection(GridPos block, GridPos placement, Face face, boolean virtual, Vec3d point) { }
    public static Ray ray(DraftSession draft, double mouseX, double mouseY, int width, int height) {
        var client = MinecraftClient.getInstance();
        var camera = client.gameRenderer.getCamera();
        double tangent = Math.tan(Math.toRadians(30));
        double aspect = (double) client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight();
        var direction = new Vector3f((float) (-(2 * mouseX / width - 1) * aspect * tangent),
            (float) ((1 - 2 * mouseY / height) * tangent), 1).rotate(camera.getRotation());
        Vec3d eye = camera.getPos().subtract(Vec3d.of(draft.origin));
        return new Ray(new Vec(eye.x, eye.y, eye.z), new Vec(direction.x, direction.y, direction.z));
    }
    public static Selection pick(DraftSession draft, Ray ray, boolean bypassDraft) {
        var client = MinecraftClient.getInstance();
        Vec origin = ray.origin(), end = ray.at(256);
        Vec3d start = new Vec3d(origin.x(), origin.y(), origin.z()).add(Vec3d.of(draft.origin));
        Vec3d finish = new Vec3d(end.x(), end.y(), end.z()).add(Vec3d.of(draft.origin));
        BlockHitResult real = client.world.raycast(new RaycastContext(start, finish, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.ANY, client.player));
        BlockHitResult closest = real.getType() == HitResult.Type.BLOCK ? real : null;
        boolean virtual = false;
        if (!bypassDraft) {
            for (GridPos pos : BuildGeometry.cells(ray, 256)) {
                var spec = draft.blueprint.blocks().get(pos);
                if (spec == null) continue;
                BlockPos world = draft.worldPos(pos);
                if (world.getY() < client.world.getBottomY() || world.getY() >= client.world.getTopY()) continue;
                net.minecraft.util.shape.VoxelShape shape;
                try { shape = BlockStates.resolve(spec).getOutlineShape(draft.worldView(), world); }
                catch (RuntimeException e) { shape = VoxelShapes.fullCube(); }
                if (shape.isEmpty()) shape = VoxelShapes.fullCube();
                BlockHitResult hit = shape.raycast(start, finish, world);
                if (hit != null && (closest == null || start.squaredDistanceTo(hit.getPos()) <= start.squaredDistanceTo(closest.getPos()) + 1e-6)) {
                    closest = hit; virtual = true;
                }
                // Later voxel cells cannot be nearer than a hit in this one.
                if (hit != null) break;
            }
        }
        if (closest == null) return null;
        BlockPos p = closest.getBlockPos().subtract(draft.origin);
        GridPos block = new GridPos(p.getX(), p.getY(), p.getZ());
        Face face = Face.valueOf(closest.getSide().name());
        boolean replaceable = !virtual && client.world.getBlockState(closest.getBlockPos()).isReplaceable()
            && client.world.getFluidState(closest.getBlockPos()).isEmpty();
        return new Selection(block, replaceable ? block : face.offset(block, 1), face, virtual, closest.getPos());
    }
}
