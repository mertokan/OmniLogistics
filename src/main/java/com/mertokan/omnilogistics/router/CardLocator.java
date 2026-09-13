package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * "Which block was this card bound to again?" The tooltip answers that in words; this answers it by pointing. Hold
 * the card, press the key, and the block flashes red through whatever machine wall is in the way.
 */
@EventBusSubscriber(modid = OmniLogistics.MODID, value = Dist.CLIENT)
public final class CardLocator {
    private static final long FLASH_MS = 6000;
    private static final KeyMapping LOCATE =
        new KeyMapping("key.omnilogistics.locate", GLFW.GLFW_KEY_V, "key.categories.omnilogistics");

    private static @Nullable BlockPos flash;
    private static long until;

    @SubscribeEvent
    static void keys(RegisterKeyMappingsEvent e) {
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
            p.displayClientMessage(Component.translatable("msg.omnilogistics.locate_none"), true);
            return;
        }
        Component name = LogisticsCardItem.boundName(card);
        if (!gp.dimension().equals(mc.level.dimension())) {
            p.displayClientMessage(Component.translatable("msg.omnilogistics.locate_far", name,
                gp.dimension().location().getPath()), true);
            return;
        }
        flash = gp.pos();
        until = Util.getMillis() + FLASH_MS;
        p.displayClientMessage(Component.translatable("msg.omnilogistics.locate", name,
            Math.round(Math.sqrt(flash.distToCenterSqr(p.position())))), true);
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
    static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || flash == null) return;
        if (Util.getMillis() > until) {
            flash = null;
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float a = 0.3f + 0.55f * (float) Math.abs(Math.sin(Util.getMillis() / 150.0));   // the flashing part

        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(flash.getX() - cam.x, flash.getY() - cam.y, flash.getZ() - cam.z);
        Matrix4f m = pose.last().pose();

        RenderSystem.disableDepthTest();        // the whole point is to see it through the machine it is buried in
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(3f);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        for (int[] edge : EDGE)
            for (int i : edge) {
                float[] c = CORNER[i];
                bb.addVertex(m, c[0] - 0.003f + c[0] * 0.006f, c[1] - 0.003f + c[1] * 0.006f, c[2] - 0.003f + c[2] * 0.006f)
                    .setColor(1f, 0.15f, 0.15f, a);
            }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.lineWidth(1f);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        pose.popPose();
    }
}
