package com.mertokan.omnilogistics.miner;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.AABB;

/**
 * The cycle, above the block: a void-purple beam with a white core rising out of the top port, a spinning tier-coloured ring
 * hovering over it, and the item being mined descending through the beam into the machine over the cycle time.
 * Everything is on the translucent block sheet (flushed before the translucent chunk layer) with a full-bright lightmap.
 */
public class VoidMinerRenderer implements BlockEntityRenderer<VoidMinerBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "block/glow");
    private static final int FULL = LightTexture.FULL_BRIGHT;

    public VoidMinerRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(VoidMinerBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (!be.mining || be.getLevel() == null) return;
        float t = be.getLevel().getGameTime() + partial;
        float k = Mth.clamp((t - be.cycleStart) / Math.max(1, be.cycleTime), 0, 1);
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GLOW);
        VertexConsumer vc = buf.getBuffer(Sheets.translucentCullBlockSheet());
        PoseStack.Pose p = pose.last();
        int c = be.tier().color;
        float r = FastColor.ARGB32.red(c) / 255f, g = FastColor.ARGB32.green(c) / 255f, b = FastColor.ARGB32.blue(c) / 255f;

        // beam: purple sheath that breathes and fades out upward, hot white core
        float w = 0.14f + 0.02f * Mth.sin(t * 0.35f);
        Quads.column(vc, p, 0.5f - w, 1.0f, 0.5f - w, 0.5f + w, 2.3f, 0.5f + w, sp, 0.55f, 0.22f, 1f, 0.8f, 0.05f, FULL, overlay);
        Quads.column(vc, p, 0.465f, 1.0f, 0.465f, 0.535f, 2.0f, 0.535f, sp, 1f, 0.9f, 1f, 0.95f, 0f, FULL, overlay);

        // spinning tier ring hovering over the port
        pose.pushPose();
        pose.translate(0.5, 1.28 + 0.06 * Mth.sin(t * 0.12f), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(t * 2.5f));
        PoseStack.Pose rp = pose.last();
        float o = 0.36f, i = 0.30f, h = 0.025f;
        Quads.box(vc, rp, -o, -h, -o, o, h, -i, 0, sp, r, g, b, 0.95f, FULL, overlay);
        Quads.box(vc, rp, -o, -h, i, o, h, o, 0, sp, r, g, b, 0.95f, FULL, overlay);
        Quads.box(vc, rp, -o, -h, -i, -i, h, i, 0, sp, r, g, b, 0.95f, FULL, overlay);
        Quads.box(vc, rp, i, -h, -i, o, h, i, 0, sp, r, g, b, 0.95f, FULL, overlay);
        pose.popPose();

        // the item being pulled out of the void: descends into the port over the cycle
        if (!be.pending.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 2.05 - 0.95 * k, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(t * 4));
            float sc = be.pending.getItem() instanceof BlockItem ? 0.5f : 0.4f;
            pose.scale(sc, sc, sc);
            Minecraft.getInstance().getItemRenderer().renderStatic(be.pending, ItemDisplayContext.FIXED, FULL, overlay, pose, buf, be.getLevel(), 0);
            pose.popPose();
        }
    }

    @Override
    public AABB getRenderBoundingBox(VoidMinerBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, 1.5, 0);
    }
}
