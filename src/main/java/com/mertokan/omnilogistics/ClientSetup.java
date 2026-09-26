package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.core.FilterScreen;
import com.mertokan.omnilogistics.distributor.DistributorScreen;
import com.mertokan.omnilogistics.extractor.ExtractorScreen;
import com.mertokan.omnilogistics.miner.MinerScreen;
import com.mertokan.omnilogistics.miner.VoidMinerRenderer;
import com.mertokan.omnilogistics.pipe.ConduitRenderer;
import com.mertokan.omnilogistics.pipe.PipeScreen;
import com.mertokan.omnilogistics.router.CardKind;
import com.mertokan.omnilogistics.router.RouterScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid = OmniLogistics.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    static void screens(RegisterMenuScreensEvent e) {
        e.register(OmniLogistics.FILTER_MENU.get(), FilterScreen::new);
        e.register(OmniLogistics.PIPE_MENU.get(), PipeScreen::new);
        e.register(OmniLogistics.ROUTER_MENU.get(), RouterScreen::new);
        e.register(OmniLogistics.EXTRACTOR_MENU.get(), ExtractorScreen::new);
        e.register(OmniLogistics.MINER_MENU.get(), MinerScreen::new);
        e.register(OmniLogistics.DISTRIBUTOR_MENU.get(), DistributorScreen::new);
    }

    /** Glass conduits: the contents are drawn by one renderer for all three types. */
    @SubscribeEvent
    static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(OmniLogistics.ITEM_PIPE_BE.get(), ConduitRenderer::new);
        e.registerBlockEntityRenderer(OmniLogistics.ENERGY_CABLE_BE.get(), ConduitRenderer::new);
        e.registerBlockEntityRenderer(OmniLogistics.FLUID_PIPE_BE.get(), ConduitRenderer::new);
        e.registerBlockEntityRenderer(OmniLogistics.MINER_BE.get(), VoidMinerRenderer::new);
        e.registerBlockEntityRenderer(OmniLogistics.MONITOR_BE.get(), com.mertokan.omnilogistics.monitor.MonitorRenderer::new);
    }

    /** Card models switch on mode (extract/insert) and on being bound: two conditions the item model definitions in
     *  assets/omnilogistics/items/ read (see tools/gen_resources.py). Item "overrides" went away in 1.21.4. */
    public record CardInsert() implements net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty {
        public static final com.mojang.serialization.MapCodec<CardInsert> CODEC = com.mojang.serialization.MapCodec.unit(new CardInsert());
        @Override public boolean get(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.@org.jetbrains.annotations.Nullable ClientLevel level,
                                     net.minecraft.world.entity.@org.jetbrains.annotations.Nullable LivingEntity owner, int seed,
                                     net.minecraft.world.item.ItemDisplayContext ctx) {
            return stack.getOrDefault(OmniLogistics.CARD_MODE.get(), 0) == 1;
        }
        @Override public com.mojang.serialization.MapCodec<CardInsert> type() { return CODEC; }
    }

    public record CardBound() implements net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty {
        public static final com.mojang.serialization.MapCodec<CardBound> CODEC = com.mojang.serialization.MapCodec.unit(new CardBound());
        @Override public boolean get(net.minecraft.world.item.ItemStack stack, net.minecraft.client.multiplayer.@org.jetbrains.annotations.Nullable ClientLevel level,
                                     net.minecraft.world.entity.@org.jetbrains.annotations.Nullable LivingEntity owner, int seed,
                                     net.minecraft.world.item.ItemDisplayContext ctx) {
            return stack.has(OmniLogistics.CARD_TARGET.get());
        }
        @Override public com.mojang.serialization.MapCodec<CardBound> type() { return CODEC; }
    }

    @SubscribeEvent
    static void itemConditions(net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent e) {
        e.register(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "insert"), CardInsert.CODEC);
        e.register(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "bound"), CardBound.CODEC);
    }

    /**
     * The card upgrade is two items rubbed together, but vanilla only animates the hand that is using one: the card
     * hand gets the brush scrub for free, and this brings the core in the other hand over to meet it, counter-swinging
     * so the two actually scrape against each other.
     */
    @SubscribeEvent
    static void ritualHand(net.neoforged.neoforge.client.event.RenderHandEvent e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        var p = mc.player;
        if (p == null || !p.isUsingItem() || !(p.getUseItem().getItem() instanceof com.mertokan.omnilogistics.router.LogisticsCardItem)) return;
        net.minecraft.world.InteractionHand used = p.getUsedItemHand();
        net.minecraft.world.InteractionHand off = used == net.minecraft.world.InteractionHand.MAIN_HAND
            ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
        boolean isOff = e.getHand() == off;
        if (com.mertokan.omnilogistics.router.CardUpgradeRecipe.upgraded(p.getUseItem(), p.getItemInHand(off)).isEmpty()) return;

        int total = com.mertokan.omnilogistics.router.LogisticsCardItem.UPGRADE_TICKS;
        float t = (total - p.getUseItemRemainingTicks() + e.getPartialTick()) / total;
        float ease = Math.min(1f, t * 5f);                                 // the hands come together over the first ticks
        float rub = net.minecraft.util.Mth.sin(t * net.minecraft.util.Mth.PI * 2f * 3.5f);
        var arm = e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        float dir = arm == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1f : -1f;
        var pose = e.getPoseStack();

        if (!isOff) {   // the card: vanilla scrubs it, this walks it in to the middle and back off the lens
            pose.translate(-dir * 0.24f * ease, 0.02f * ease, -0.46f * ease);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(dir * rub * -7f * ease));
            return;
        }
        pose.translate(-dir * 0.40f * ease, -0.02f * ease + rub * 0.05f * ease, -0.44f * ease + rub * 0.04f * ease);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(dir * 22f * ease));
        pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(dir * (-18f * ease + rub * 18f * ease)));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-18f * ease - rub * 12f * ease));
    }

    /** Every block/item of the mod gets its "desc.omnilogistics.<id>" lang text under the name. */
    @SubscribeEvent
    static void tooltip(ItemTooltipEvent e) {
        Identifier id = BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem());
        if (!OmniLogistics.MODID.equals(id.getNamespace())) return;
        String key = "desc.omnilogistics." + id.getPath();
        if (!I18n.exists(key)) return;
        int at = Math.min(1, e.getToolTip().size());
        for (String line : I18n.get(key).split("\n"))
            e.getToolTip().add(at++, Component.literal(line).withStyle(ChatFormatting.GRAY));
    }
}
