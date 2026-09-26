package com.mertokan.omnilogistics.monitor;

import com.mertokan.omnilogistics.core.Quads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws the block entity's snapshot on the front face: title, an FE bar, a fluid bar and the first item stacks.
 * The whole face is 1 block, so everything is laid out in a 64 x 64 "screen" and scaled down once.
 */
public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity, MonitorRenderer.State> {
    private static final int W = 64, PAD = 8, INNER = W - 2 * PAD;   // PAD = the bezel in the front texture
    private static final int TEXT = 0xFFE6EDF3, MUTED = 0xFF8B95A1, FE = 0x3FD3FF, FLUID = 0x3F8CFF;
    private static final float SCALE = 1f / W;
    private static final int LIGHT = LightCoordsUtil.FULL_BRIGHT;   // a screen glows; the block's own light is the light inside a solid block

    /** One stack on the screen: its model and the line of text next to it. */
    private record Row(ItemStackRenderState model, String label) {}

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        boolean bound;
        String title = "", energyText = "", fluidText = "";
        float energyFrac = -1, fluidFrac = -1;
        final List<Row> rows = new ArrayList<>();
        int hidden;
    }

    private final ItemModelResolver items;

    public MonitorRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MonitorBlockEntity be, State st, float partial, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(be, st, partial, camera, crumbling);
        Font font = Minecraft.getInstance().font;
        st.facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        st.bound = be.bound();
        st.rows.clear();
        st.energyFrac = st.fluidFrac = -1;
        if (!st.bound) return;
        st.title = trim(font, be.title, INNER);
        if (be.energyMax > 0) {
            st.energyFrac = be.energy / (float) be.energyMax;
            st.energyText = trim(font, compact(be.energy) + " / " + compact(be.energyMax) + " FE", INNER);
        }
        if (be.fluidMax > 0 && !be.fluidName.isEmpty()) {
            st.fluidFrac = be.fluid / (float) be.fluidMax;
            st.fluidText = trim(font, compact(be.fluid) + " mB " + be.fluidName, INNER);
        }
        int room = (W - PAD - PAD - 10 - (st.energyFrac >= 0 ? 17 : 0) - (st.fluidFrac >= 0 ? 17 : 0)) / 10;
        for (ItemStack s : be.items) {
            if (st.rows.size() >= room) break;
            ItemStackRenderState model = new ItemStackRenderState();
            items.updateForTopItem(model, s, ItemDisplayContext.GUI, be.getLevel(), null, 0);
            st.rows.add(new Row(model, trim(font, s.getCount() + "x " + s.getHoverName().getString(), INNER - 10)));
        }
        st.hidden = be.moreKinds + (be.items.size() - st.rows.size());   // kinds the machine had but the glass has no room for
    }

    @Override
    public void submit(State st, PoseStack pose, SubmitNodeCollector out, CameraRenderState camera) {
        pose.pushPose();
        // onto the front face, turned outward, then everything below is laid out in screen pixels
        pose.translate(0.5 + st.facing.getStepX() * 0.502, 0.5, 0.5 + st.facing.getStepZ() * 0.502);
        pose.mulPose(Axis.YP.rotationDegrees(-st.facing.toYRot()));
        pose.scale(SCALE, -SCALE, SCALE);          // negative y, like a sign: screen pixels, +x right, +y down, +z out
        pose.translate(-W / 2f, -W / 2f, 0.2f);    // origin top-left, a hair in front of the bezel

        int y = PAD;
        if (!st.bound) {
            line(out, pose, Component.translatable("gui.omnilogistics.monitor_no_card").getString(), PAD, y, MUTED);
            pose.popPose();
            return;
        }
        line(out, pose, st.title, PAD, y, TEXT);
        y += 10;
        if (st.energyFrac >= 0) {
            bar(out, pose, PAD, y, INNER, 5, st.energyFrac, FE);
            line(out, pose, st.energyText, PAD, y + 7, MUTED);
            y += 17;
        }
        if (st.fluidFrac >= 0) {
            bar(out, pose, PAD, y, INNER, 5, st.fluidFrac, FLUID);
            line(out, pose, st.fluidText, PAD, y + 7, MUTED);
            y += 17;
        }
        for (Row row : st.rows) {
            pose.pushPose();
            pose.translate(PAD + 4, y + 4, 4);
            pose.scale(8, -8, 8);           // 8 screen pixels tall, standing off the glass so a block model still reads as 3D
            row.model().submit(pose, out, LIGHT, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
            line(out, pose, row.label(), PAD + 10, y + 2, TEXT);
            y += 10;
        }
        if (st.hidden > 0 && y + 5 <= W - PAD)
            line(out, pose, Component.translatable("gui.omnilogistics.monitor_more", st.hidden).getString(), PAD, y, MUTED);
        pose.popPose();
    }

    private static void line(SubmitNodeCollector out, PoseStack pose, String text, float x, float y, int colour) {
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.5f, 0.5f, 0.5f);   // vanilla glyphs are 8px tall; half that fits four lines on one face
        out.submitText(pose, 0, 0, FormattedCharSequence.forward(text, net.minecraft.network.chat.Style.EMPTY), false,
            Font.DisplayMode.NORMAL, LIGHT, colour, 0, 0);
        pose.popPose();
    }

    private static void bar(SubmitNodeCollector out, PoseStack pose, float x, float y, float w, float h, float frac, int colour) {
        TextureAtlasSprite sp = com.mertokan.omnilogistics.pipe.ConduitRenderer.glow();
        float r = (colour >> 16 & 255) / 255f, g = (colour >> 8 & 255) / 255f, b = (colour & 255) / 255f;
        float fill = Math.max(0.5f, w * Math.min(1, Math.max(0, frac)));
        int overlay = OverlayTexture.NO_OVERLAY;
        out.submitCustomGeometry(pose, Sheets.translucentBlockSheet(), (p, vc) -> {
            // the empty track has to read against the black glass
            Quads.quad(vc, p, x, y + h, 0, x + w, y + h, 0, x + w, y, 0, x, y, 0, 0, 0, 1, sp, 0.16f, 0.18f, 0.22f, 1f, LIGHT, overlay);
            Quads.quad(vc, p, x, y + h, 0.05f, x + fill, y + h, 0.05f, x + fill, y, 0.05f, x, y, 0.05f, 0, 0, 1, sp, r, g, b, 1f, LIGHT, overlay);
        });
    }

    private static String trim(Font font, String s, int width) {
        return font.plainSubstrByWidth(s, width * 2);   // the text is drawn at half scale
    }

    private static String compact(int n) {
        return n >= 1_000_000 ? String.format(Locale.ROOT, "%.1fM", n / 1e6)
            : n >= 1000 ? String.format(Locale.ROOT, "%.1fk", n / 1e3) : Integer.toString(n);
    }
}
