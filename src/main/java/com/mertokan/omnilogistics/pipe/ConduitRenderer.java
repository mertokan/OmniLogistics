package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.Quads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * What you see through the glass: the item in transit (slides in from the side it came from, then spins in the knot),
 * the fluid as a translucent column whose thickness follows the fill level, energy as a pulsing emissive core.
 * Contents reach the client through {@link ConduitBlockEntity#markDirty()}.
 *
 * <p>Since 1.21.9 a block entity renderer works in two steps: {@link #extractRenderState} copies what it needs out of
 * the block entity while the world is stable, {@link #submit} draws from that copy.
 */
public class ConduitRenderer implements BlockEntityRenderer<ConduitBlockEntity, ConduitRenderer.State> {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "block/glow");

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean hasItem, beams;
        float ox, oy, oz, spin, scale;
        int open;
        float half, r, g, b, a;
        int light;
        @Nullable TextureAtlasSprite sprite;
    }

    private final ItemModelResolver items;

    public ConduitRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ConduitBlockEntity be, State st, float partial, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, st, partial, camera, crumbling);
        st.hasItem = st.beams = false;
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SmartPipeBlock) || be.getLevel() == null) return;
        st.open = 0;
        for (Direction d : Direction.values())
            if (state.getValue(SmartPipeBlock.PROP.get(d)) != SmartPipeBlock.Connection.NONE) st.open |= 1 << d.ordinal();
        float t = be.getLevel().getGameTime() + partial;
        if (be instanceof PipeBlockEntity p) item(p, partial, st);
        else if (be instanceof FluidPipeBlockEntity f) fluid(f, st);
        else if (be instanceof EnergyCableBlockEntity e) energy(e, t, st);
    }

    /** The stack crosses the WHOLE block in one tier interval, clocked off the server tick it entered ({@code enteredAt}, synced
     *  in the update tag): entry face -> centre -> exit face. Drawn one tick behind the server, because a buffer change during
     *  tick T is only broadcast during tick T+1. At the handover frame the source is clamped at k=1 and the destination starts
     *  at k=0 - the same world point - so the seam is continuous. Blocked = waits at the exit face. */
    private void item(PipeBlockEntity p, float partial, State st) {
        ItemStack s = p.bufferStack();
        if (s.isEmpty()) return;
        long now = p.getLevel().getGameTime();
        float k = Mth.clamp(((now - p.enteredAt - 1) + partial) / p.tier().interval, 0, 1);   // 0 = entry face, 1 = exit face
        Direction from = p.lastFrom(), to = exitOf(p.getBlockState(), from);
        st.ox = st.oy = st.oz = 0;
        if (from != null && k < 0.5f) { float d = 0.5f - k; st.ox = from.getStepX() * d; st.oy = from.getStepY() * d; st.oz = from.getStepZ() * d; }
        else if (to != null && k >= 0.5f) { float d = k - 0.5f; st.ox = to.getStepX() * d; st.oy = to.getStepY() * d; st.oz = to.getStepZ() * d; }
        st.spin = (now % 120L + partial) * 3f;   // 120 ticks = one full turn, so the wrap is seamless
        st.scale = s.getItem() instanceof net.minecraft.world.item.BlockItem ? 0.56f : 0.36f;
        items.updateForTopItem(st.item, s, ItemDisplayContext.FIXED, p.getLevel(), null, 0);
        st.hasItem = true;
    }

    /** The one connected side other than {@code from}; null at a junction or dead end (the stack then waits in the centre). */
    private static @Nullable Direction exitOf(BlockState state, @Nullable Direction from) {
        Direction exit = null;
        for (Direction d : Direction.values()) {
            if (d == from || state.getValue(SmartPipeBlock.PROP.get(d)) == SmartPipeBlock.Connection.NONE) continue;
            if (exit != null) return null;
            exit = d;
        }
        return exit;
    }

    private void fluid(FluidPipeBlockEntity f, State st) {
        FluidStack fs = f.fluid();
        if (fs.isEmpty()) return;
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fs.getFluid().defaultFluidState());
        int c = model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fs) : -1;
        float frac = fs.getAmount() / (float) Math.max(1, f.capacity());
        beams(st, model.stillMaterial().sprite(), 0.08f + 0.10f * frac, ARGB.red(c) / 255f, ARGB.green(c) / 255f, ARGB.blue(c) / 255f, 0.92f, st.lightCoords);
    }

    private void energy(EnergyCableBlockEntity e, float t, State st) {
        if (e.stored() <= 0) return;
        beams(st, glow(), 0.12f, 1f, 0.88f, 0.35f, 0.8f + 0.2f * Mth.sin(t * 0.3f), LightCoordsUtil.FULL_BRIGHT);
    }

    private static void beams(State st, TextureAtlasSprite sprite, float half, float r, float g, float b, float a, int light) {
        st.beams = true;
        st.sprite = sprite;
        st.half = half;
        st.r = r; st.g = g; st.b = b; st.a = a;
        st.light = light;
    }

    public static TextureAtlasSprite glow() {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(GLOW);
    }

    @Override
    public void submit(State st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (st.hasItem) {
            pose.pushPose();
            pose.translate(0.5 + st.ox, 0.5 + st.oy, 0.5 + st.oz);
            pose.mulPose(Axis.YP.rotationDegrees(st.spin));
            pose.scale(st.scale, st.scale, st.scale);
            st.item.submit(pose, out, st.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (st.beams && st.sprite != null)
            out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), (p, vc) -> beams(vc, p, st));
    }

    /** Centre cube plus a bar out to the block edge on every connected side. No faces where pieces meet (centre/bar,
     *  bar/next block): through the glass they would show as rings at every joint. */
    private static void beams(VertexConsumer vc, PoseStack.Pose p, State st) {
        float lo = 0.5f - st.half, hi = 0.5f + st.half;
        int overlay = OverlayTexture.NO_OVERLAY;
        Quads.box(vc, p, lo, lo, lo, hi, hi, hi, st.open, st.sprite, st.r, st.g, st.b, st.a, st.light, overlay);
        for (Direction d : Direction.values()) {
            if ((st.open & 1 << d.ordinal()) == 0) continue;
            float x0 = lo, y0 = lo, z0 = lo, x1 = hi, y1 = hi, z1 = hi;
            switch (d) {
                case EAST -> { x0 = hi; x1 = 1; }
                case WEST -> { x0 = 0; x1 = lo; }
                case UP -> { y0 = hi; y1 = 1; }
                case DOWN -> { y0 = 0; y1 = lo; }
                case SOUTH -> { z0 = hi; z1 = 1; }
                case NORTH -> { z0 = 0; z1 = lo; }
            }
            Quads.box(vc, p, x0, y0, z0, x1, y1, z1, 1 << d.ordinal() | 1 << d.getOpposite().ordinal(), st.sprite, st.r, st.g, st.b, st.a, st.light, overlay);
        }
    }

    /** The stack's centre reaches the block face, so a spinning BlockItem hangs into the neighbour; frustum culling uses
     *  this box, and the default unit cube would make the item pop out at the edge of the screen. */
    @Override
    public AABB getRenderBoundingBox(ConduitBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
