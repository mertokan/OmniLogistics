package com.mertokan.omnilogistics.miner;

import com.mertokan.omnilogistics.core.Quads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The cycle, above the block: a void-purple beam with a white core rising out of the top port, a spinning tier-coloured ring
 * hovering over it, and the item being mined descending through the beam into the machine over the cycle time.
 * Everything is on the translucent block sheet with a full-bright lightmap.
 */
public class VoidMinerRenderer implements BlockEntityRenderer<VoidMinerBlockEntity, VoidMinerRenderer.State> {
    private static final int FULL = LightCoordsUtil.FULL_BRIGHT;

    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean mining, hasItem;
        float t, k, itemScale;
        int color;
    }

    private final ItemModelResolver items;

    public VoidMinerRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VoidMinerBlockEntity be, State st, float partial, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, st, partial, camera, crumbling);
        st.mining = be.mining && be.getLevel() != null;
        st.hasItem = false;
        if (!st.mining) return;
        st.t = be.getLevel().getGameTime() + partial;
        st.k = Mth.clamp((st.t - be.cycleStart) / Math.max(1, be.cycleTime), 0, 1);
        st.color = be.tier().color;
        if (!be.pending.isEmpty()) {
            items.updateForTopItem(st.item, be.pending, ItemDisplayContext.FIXED, be.getLevel(), null, 0);
            st.itemScale = be.pending.getItem() instanceof BlockItem ? 0.5f : 0.4f;
            st.hasItem = true;
        }
    }

    @Override
    public void submit(State st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        if (!st.mining) return;
        TextureAtlasSprite sp = com.mertokan.omnilogistics.pipe.ConduitRenderer.glow();
        float t = st.t;
        int overlay = OverlayTexture.NO_OVERLAY;
        float r = ARGB.red(st.color) / 255f, g = ARGB.green(st.color) / 255f, b = ARGB.blue(st.color) / 255f;

        // beam: purple sheath that breathes and fades out upward, hot white core
        out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), (p, vc) -> {
            float w = 0.14f + 0.02f * Mth.sin(t * 0.35f);
            Quads.column(vc, p, 0.5f - w, 1.0f, 0.5f - w, 0.5f + w, 2.3f, 0.5f + w, sp, 0.55f, 0.22f, 1f, 0.8f, 0.05f, FULL, overlay);
            Quads.column(vc, p, 0.465f, 1.0f, 0.465f, 0.535f, 2.0f, 0.535f, sp, 1f, 0.9f, 1f, 0.95f, 0f, FULL, overlay);
        });

        // spinning tier ring hovering over the port
        pose.pushPose();
        pose.translate(0.5, 1.28 + 0.06 * Mth.sin(t * 0.12f), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.5f));
        out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), (rp, vc) -> {
            float o = 0.36f, i = 0.30f, h = 0.025f;
            Quads.box(vc, rp, -o, -h, -o, o, h, -i, 0, sp, r, g, b, 0.95f, FULL, overlay);
            Quads.box(vc, rp, -o, -h, i, o, h, o, 0, sp, r, g, b, 0.95f, FULL, overlay);
            Quads.box(vc, rp, -o, -h, -i, -i, h, i, 0, sp, r, g, b, 0.95f, FULL, overlay);
            Quads.box(vc, rp, i, -h, -i, o, h, i, 0, sp, r, g, b, 0.95f, FULL, overlay);
        });
        pose.popPose();

        // the item being pulled out of the void: descends into the port over the cycle
        if (st.hasItem) {
            pose.pushPose();
            pose.translate(0.5, 2.05 - 0.95 * st.k, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(t * 4));
            pose.scale(st.itemScale, st.itemScale, st.itemScale);
            st.item.submit(pose, out, FULL, overlay, 0);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(VoidMinerBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.5, 0);
    }
}
