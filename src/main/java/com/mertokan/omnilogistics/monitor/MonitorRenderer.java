package com.mertokan.omnilogistics.monitor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mertokan.omnilogistics.core.Quads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.Locale;

/**
 * Draws the block entity's snapshot on the front face: title, an FE bar, a fluid bar and the first item stacks.
 * The whole face is 1 block, so everything is laid out in a 64 x 64 "screen" and scaled down once.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("omnilogistics", "block/glow");
    private static final int W = 64, PAD = 8, INNER = W - 2 * PAD;   // PAD = the bezel in the front texture
    private static final int TEXT = 0xE6EDF3, MUTED = 0x8B95A1, FE = 0x3FD3FF, FLUID = 0x3F8CFF;
    private static final float SCALE = 1f / W;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(MonitorBlockEntity be, float partial, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Font font = Minecraft.getInstance().font;
        light = LightTexture.FULL_BRIGHT;   // it is a screen: it glows, and the BE's own light is the light inside a solid block
        pose.pushPose();
        // onto the front face, turned outward, then everything below is laid out in screen pixels
        pose.translate(0.5 + facing.getStepX() * 0.502, 0.5, 0.5 + facing.getStepZ() * 0.502);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.scale(SCALE, -SCALE, SCALE);          // negative y, like a sign: screen pixels, +x right, +y down, +z out
        pose.translate(-W / 2f, -W / 2f, 0.2f);    // origin top-left, a hair in front of the bezel

        int y = PAD;
        if (!be.bound()) {
            line(font, pose, buf, Component.translatable("gui.omnilogistics.monitor_no_card").getString(), PAD, y, MUTED, light);
            pose.popPose();
            return;
        }
        line(font, pose, buf, trim(font, be.title, INNER), PAD, y, TEXT, light);
        y += 10;
        if (be.energyMax > 0) {
            bar(pose, buf, PAD, y, INNER, 5, be.energy / (float) be.energyMax, FE, light, overlay);
            line(font, pose, buf, trim(font, compact(be.energy) + " / " + compact(be.energyMax) + " FE", INNER), PAD, y + 7, MUTED, light);
            y += 17;
        }
        if (be.fluidMax > 0 && !be.fluidName.isEmpty()) {
            bar(pose, buf, PAD, y, INNER, 5, be.fluid / (float) be.fluidMax, FLUID, light, overlay);
            line(font, pose, buf, trim(font, compact(be.fluid) + " mB " + be.fluidName, INNER), PAD, y + 7, MUTED, light);
            y += 17;
        }
        int shown = 0;
        for (ItemStack s : be.items) {
            if (y + 9 > W - PAD) break;
            item(pose, buf, s, PAD, y, light, overlay, be);
            line(font, pose, buf, trim(font, s.getCount() + "x " + s.getHoverName().getString(), INNER - 10), PAD + 10, y + 2, TEXT, light);
            y += 10;
            shown++;
        }
        int hidden = be.moreKinds + (be.items.size() - shown);        // kinds the machine had but the glass has no room for
        if (hidden > 0 && y + 5 <= W - PAD)
            line(font, pose, buf, Component.translatable("gui.omnilogistics.monitor_more", hidden).getString(), PAD, y, MUTED, light);
        pose.popPose();
    }

    private static void line(Font font, PoseStack pose, MultiBufferSource buf, String text, float x, float y, int colour, int light) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5f, 0.5f, 0.5f);   // vanilla glyphs are 8px tall; half that fits four lines on one face
        font.drawInBatch(text, 0, 0, colour, false, pose.last().pose(), buf, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }

    private static void bar(PoseStack pose, MultiBufferSource buf, float x, float y, float w, float h, float frac, int colour, int light, int overlay) {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GLOW);
        var vc = buf.getBuffer(Sheets.translucentCullBlockSheet());
        float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
        Quads.quad(vc, pose.last(), x, y + h, 0, x + w, y + h, 0, x + w, y, 0, x, y, 0, 0, 0, 1, sp, 0.16f, 0.18f, 0.22f, 1f, light, overlay);   // the empty track has to read against the black glass
        float fill = Math.max(0.5f, w * Math.min(1, Math.max(0, frac)));
        Quads.quad(vc, pose.last(), x, y + h, 0.05f, x + fill, y + h, 0.05f, x + fill, y, 0.05f, x, y, 0.05f, 0, 0, 1, sp, r, g, b, 1f, LightTexture.FULL_BRIGHT, overlay);
    }

    private static void item(PoseStack pose, MultiBufferSource buf, ItemStack s, float x, float y, int light, int overlay, MonitorBlockEntity be) {
        pose.pushPose();
        pose.translate(x + 4, y + 4, 4);
        pose.scale(8, -8, 8);           // 8 screen pixels tall, standing off the glass so a block model still reads as 3D
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.GUI, light, overlay, pose, buf, be.getLevel(), 0);
        pose.popPose();
    }

    private static String trim(Font font, String s, int width) {
        return font.plainSubstrByWidth(s, width * 2);   // the text is drawn at half scale
    }

    private static String compact(int n) {
        return n >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", n / 1e6)
            : n >= 1000 ? String.format(Locale.ROOT, "%.1fk", n / 1e3) : Integer.toString(n);
    }
}
