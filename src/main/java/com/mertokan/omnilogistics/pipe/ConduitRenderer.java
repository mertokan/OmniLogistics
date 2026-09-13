package com.mertokan.omnilogistics.pipe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.Quads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * What you see through the glass: the item in transit (slides in from the side it came from, then spins in the knot),
 * the fluid as a translucent column whose thickness follows the fill level, energy as a pulsing emissive core.
 * Contents reach the client through {@link ConduitBlockEntity#markDirty()}.
 */
public class ConduitRenderer implements BlockEntityRenderer<ConduitBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "block/glow");

    public ConduitRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ConduitBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof SmartPipeBlock) || be.getLevel() == null) return;
        float t = be.getLevel().getGameTime() + partial;
        if (be instanceof PipeBlockEntity p) item(p, partial, pose, buf, light, overlay);
        else if (be instanceof FluidPipeBlockEntity f) fluid(f, state, pose, buf, light, overlay);
        else if (be instanceof EnergyCableBlockEntity e) energy(e, state, t, pose, buf, overlay);
    }

    /** The stack crosses the WHOLE block in one tier interval, clocked off the server tick it entered ({@code enteredAt}, synced
     *  in the update tag): entry face -> centre -> exit face. Drawn one tick behind the server, because a buffer change during
     *  tick T is only broadcast during tick T+1 (the chunk source ticks before the block entities). At the handover frame the
     *  source is clamped at k=1 and the destination starts at k=0 - the same world point - so the seam is continuous instead of
     *  skipping 1/interval of a block. Blocked = waits at the exit face. */
    private void item(PipeBlockEntity p, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        ItemStack s = p.bufferStack();
        if (s.isEmpty()) return;
        long now = p.getLevel().getGameTime();
        // subtract in long (game time widened to float loses whole ticks past 2^24), then take off the one tick of broadcast lag
        float k = Mth.clamp(((now - p.enteredAt - 1) + partial) / p.tier().interval, 0, 1);   // 0 = on the entry face, 1 = on the exit face
        Direction from = p.lastFrom(), to = exitOf(p.getBlockState(), from);
        float ox = 0, oy = 0, oz = 0;
        if (from != null && k < 0.5f) { float d = 0.5f - k; ox = from.getStepX() * d; oy = from.getStepY() * d; oz = from.getStepZ() * d; }
        else if (to != null && k >= 0.5f) { float d = k - 0.5f; ox = to.getStepX() * d; oy = to.getStepY() * d; oz = to.getStepZ() * d; }
        pose.pushPose();
        pose.translate(0.5 + ox, 0.5 + oy, 0.5 + oz);
        pose.mulPose(Axis.YP.rotationDegrees((now % 120L + partial) * 3f));   // 120 ticks = one full turn, so the wrap is seamless
        float sc = s.getItem() instanceof net.minecraft.world.item.BlockItem ? 0.56f : 0.36f;   // blocks 0.28 of a block, sprites 0.36
        pose.scale(sc, sc, sc);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, light, overlay, pose, buf, p.getLevel(), 0);
        pose.popPose();
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

    private void fluid(FluidPipeBlockEntity f, BlockState state, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        FluidStack fs = f.fluid();
        if (fs.isEmpty()) return;
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fs.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fs));
        int c = ext.getTintColor(fs);
        float frac = fs.getAmount() / (float) Math.max(1, f.capacity());
        // the translucent block sheet is flushed before the translucent chunk layer, so the glass blends over the contents
        float r = FastColor.ARGB32.red(c) / 255f, g = FastColor.ARGB32.green(c) / 255f, b = FastColor.ARGB32.blue(c) / 255f;
        // the translucent block sheet is flushed before the translucent chunk layer, so the glass blends over the column
        VertexConsumer vc = buf.getBuffer(Sheets.translucentCullBlockSheet());
        beams(vc, pose.last(), state, 0.08f + 0.10f * frac, sprite, r, g, b, 0.92f, light, overlay);
    }

    private void energy(EnergyCableBlockEntity e, BlockState state, float t, PoseStack pose, MultiBufferSource buf, int overlay) {
        if (e.stored() <= 0) return;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GLOW);
        VertexConsumer vc = buf.getBuffer(Sheets.translucentCullBlockSheet());   // full-bright lightmap instead of the emissive shader
        float a = 0.8f + 0.2f * Mth.sin(t * 0.3f);
        beams(vc, pose.last(), state, 0.12f, sprite, 1f, 0.88f, 0.35f, a, LightTexture.FULL_BRIGHT, overlay);
    }

    /** Centre cube plus a bar out to the block edge on every connected side. half = half thickness in blocks.
     *  No faces where pieces meet (centre/bar, bar/next block): through the glass they would show as rings at every joint. */
    private static void beams(VertexConsumer vc, PoseStack.Pose p, BlockState state, float half, TextureAtlasSprite sp,
                              float r, float g, float b, float a, int light, int overlay) {
        float lo = 0.5f - half, hi = 0.5f + half;
        int open = 0;
        for (Direction d : Direction.values())
            if (state.getValue(SmartPipeBlock.PROP.get(d)) != SmartPipeBlock.Connection.NONE) open |= 1 << d.ordinal();
        Quads.box(vc, p, lo, lo, lo, hi, hi, hi, open, sp, r, g, b, a, light, overlay);
        for (Direction d : Direction.values()) {
            if ((open & 1 << d.ordinal()) == 0) continue;
            float x0 = lo, y0 = lo, z0 = lo, x1 = hi, y1 = hi, z1 = hi;
            switch (d) {
                case EAST -> { x0 = hi; x1 = 1; }
                case WEST -> { x0 = 0; x1 = lo; }
                case UP -> { y0 = hi; y1 = 1; }
                case DOWN -> { y0 = 0; y1 = lo; }
                case SOUTH -> { z0 = hi; z1 = 1; }
                case NORTH -> { z0 = 0; z1 = lo; }
            }
            Quads.box(vc, p, x0, y0, z0, x1, y1, z1, 1 << d.ordinal() | 1 << d.getOpposite().ordinal(), sp, r, g, b, a, light, overlay);
        }
    }

    /** The stack's centre reaches the block face, so up to ~0.4 block of a spinning BlockItem hangs into the neighbour. NeoForge
     *  frustum-tests each renderer against this box and the default is just the unit cube, so without it the item pops out at
     *  the edge of the screen. Harmless for the fluid / energy beams, which stay inside the block. */
    @Override
    public AABB getRenderBoundingBox(ConduitBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(0.5);
    }
}
