package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * "Which block was this card bound to again?" The tooltip answers that in words; this answers it by pointing. Hold
 * the card, press the key, and the block flashes red through whatever machine wall is in the way.
 */
@EventBusSubscriber(modid = OmniLogistics.MODID, value = Dist.CLIENT)
public final class CardLocator {
    private static final long FLASH_MS = 6000;
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "main"));
    private static final KeyMapping LOCATE = new KeyMapping("key.omnilogistics.locate", GLFW.GLFW_KEY_V, CATEGORY);

    /** Lines with the depth test switched off: the one way to see a block through the machine it is buried in. */
    private static final RenderPipeline XRAY_LINES = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "pipeline/xray_lines"))
        .withDepthStencilState(java.util.Optional.empty())
        .build();
    private static final RenderType XRAY = RenderType.create("omnilogistics_xray_lines",
        RenderSetup.builder(XRAY_LINES).setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING).createRenderSetup());

    private static @Nullable BlockPos flash;
    private static long until;

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent e) {
        e.registerCategory(CATEGORY);
        e.register(LOCATE);
    }

    @SubscribeEvent
    static void tick(ClientTickEvent.Post e) {
        while (LOCATE.consumeClick()) locate();
    }

    /** Nothing here needs the server: the binding lives in a synced component, so the client already knows. */
    private static void locate() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ItemStack card = held(p);
        if (card == null) return;                 // not holding a card: leave the key to whoever else wants it
        GlobalPos gp = card.get(OmniLogistics.CARD_TARGET.get());
        if (gp == null) {
            p.sendOverlayMessage(Component.translatable("msg.omnilogistics.locate_none"));
            return;
        }
        Component name = LogisticsCardItem.boundName(card);
        if (!gp.dimension().equals(mc.level.dimension())) {
            p.sendOverlayMessage(Component.translatable("msg.omnilogistics.locate_far", name,
                gp.dimension().identifier().getPath()));
            return;
        }
        flash = gp.pos();
        until = Util.getMillis() + FLASH_MS;
        p.sendOverlayMessage(Component.translatable("msg.omnilogistics.locate", name,
            Math.round(Math.sqrt(flash.distToCenterSqr(p.position())))));
    }

    private static @Nullable ItemStack held(LocalPlayer p) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = p.getItemInHand(h);
            if (s.getItem() instanceof LogisticsCardItem) return s;
        }
        return null;
    }

    private static final float[][] CORNER = {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}};
    private static final int[][] EDGE = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};

    @SubscribeEvent
    static void pipelines(RegisterRenderPipelinesEvent e) {
        e.registerPipeline(XRAY_LINES);
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent.AfterTranslucentParticles e) {
        if (flash == null) return;
        if (Util.getMillis() > until) {
            flash = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        float a = 0.3f + 0.55f * (float) Math.abs(Math.sin(Util.getMillis() / 150.0));   // the flashing part
        int color = ARGB.colorFromFloat(a, 1f, 0.15f, 0.15f);

        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(flash.getX() - cam.x, flash.getY() - cam.y, flash.getZ() - cam.z);
        PoseStack.Pose p = pose.last();
        var buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(XRAY);
        for (int[] edge : EDGE) {
            float[] c0 = CORNER[edge[0]], c1 = CORNER[edge[1]];
            float nx = c1[0] - c0[0], ny = c1[1] - c0[1], nz = c1[2] - c0[2];
            corner(vc, p, c0, nx, ny, nz, color);
            corner(vc, p, c1, nx, ny, nz, color);
        }
        buffers.endBatch(XRAY);
        pose.popPose();
    }

    /** A hair outside the block, so the box never z-fights the block's own faces. */
    private static void corner(VertexConsumer vc, PoseStack.Pose p, float[] c, float nx, float ny, float nz, int color) {
        vc.addVertex(p, c[0] - 0.003f + c[0] * 0.006f, c[1] - 0.003f + c[1] * 0.006f, c[2] - 0.003f + c[2] * 0.006f)
            .setColor(color).setNormal(p, nx, ny, nz).setLineWidth(3f);
    }
}
