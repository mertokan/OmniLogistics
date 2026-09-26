package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.core.Caps;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import com.mertokan.omnilogistics.core.FilterMenu;
import com.mertokan.omnilogistics.api.FilterSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Sneak + right-click a block: bind the card to that block face. Right-click in air: cards with a filter open
 * the filter GUI, cards with only a mode flip EXTRACT / INSERT. Config lives in data components, so a card is portable.
 * Craft a card with Speed Upgrades (up to 3) to overclock it: every step doubles what it moves per action.
 */
public class LogisticsCardItem extends Item {
    public static final int EXTRACT = 0, INSERT = 1, MAX_SPEED = 3;
    public final CardKind kind;
    /** How many reference items this card can whitelist: 1 plain, then 4 / 16 / 64 for the tiered cards. */
    public final int capacity;

    public LogisticsCardItem(Properties props, CardKind kind) {
        this(props, kind, 1);
    }

    public LogisticsCardItem(Properties props, CardKind kind, int capacity) {
        super(props);
        this.kind = kind;
        this.capacity = capacity;
    }

    public static int capacity(ItemStack card) {
        return card.getItem() instanceof LogisticsCardItem c ? c.capacity : 1;
    }

    public static int speed(ItemStack card) {
        return Math.min(MAX_SPEED, card.getOrDefault(OmniLogistics.CARD_SPEED.get(), 0));
    }

    /** Only the plain Logistics Card filters items; the other kinds mean something else in a router. */
    /** Right-click a card you are carrying onto another card in the inventory: the filter, flags, tags, components and
     *  mode are copied onto it. Filling a 64-reference card by hand once is bad enough; twice is why this exists. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof LogisticsCardItem src) || src.kind != kind) return false;
        CardConfig.copyFilter(other, stack);
        player.sendOverlayMessage(Component.translatable("msg.omnilogistics.card_copied"));
        return true;
    }

    /** The Fluid Card: the one that belongs in a fluid conduit. */
    public static boolean isFluidCard(ItemStack card) {
        return card.getItem() instanceof LogisticsCardItem c && c.kind == CardKind.FLUID;
    }

    public static boolean isFilterCard(ItemStack card) {
        return card.getItem() instanceof LogisticsCardItem c && c.kind == CardKind.ITEM;
    }

    public static boolean extractMode(ItemStack card) {
        return card.getOrDefault(OmniLogistics.CARD_MODE.get(), EXTRACT) == EXTRACT;
    }

    /** Point a card at a block face. The block id goes along for the ride so the tooltip can name it later, when
     *  the chunk is long unloaded or a dimension away. */
    public static void bind(ItemStack card, Level level, BlockPos pos, @Nullable Direction side) {
        card.set(OmniLogistics.CARD_TARGET.get(), GlobalPos.of(level.dimension(), pos));
        if (side != null) card.set(OmniLogistics.CARD_SIDE.get(), side);
        card.set(OmniLogistics.CARD_TARGET_BLOCK.get(),
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString());
    }

    /** What the card points at, by name. Falls back to the raw id, then to a shrug for cards bound before this existed. */
    public static Component boundName(ItemStack card) {
        String id = card.get(OmniLogistics.CARD_TARGET_BLOCK.get());
        if (id == null) return Component.translatable("tooltip.omnilogistics.card_target_unknown");
        var block = BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id));
        return block.isPresent() ? block.get().getName() : Component.literal(id);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p == null || !p.isShiftKeyDown()) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide()) {
            ItemStack s = ctx.getItemInHand();
            bind(s, ctx.getLevel(), ctx.getClickedPos(), ctx.getClickedFace());
            p.sendOverlayMessage(Component.translatable("msg.omnilogistics.card_bound",
                ctx.getClickedPos().toShortString(), ctx.getClickedFace().getName()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // a tier core in the other hand turns this click into the upgrade ritual instead of opening the filter
        if (!CardUpgradeRecipe.upgraded(stack, player.getItemInHand(other(hand))).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            if (kind.hasFilter) {
                CardHost host = new CardHost(player, hand);
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new FilterMenu(id, inv, host, BlockPos.ZERO, hand.ordinal()), stack.getHoverName()),
                    buf -> FilterMenu.writeData(buf, host, BlockPos.ZERO, hand.ordinal()));
            } else if (kind.hasMode) {
                int mode = stack.getOrDefault(OmniLogistics.CARD_MODE.get(), EXTRACT) ^ 1;
                stack.set(OmniLogistics.CARD_MODE.get(), mode);
                player.sendOverlayMessage(Component.translatable("tooltip.omnilogistics.card_mode", mode == INSERT ? "INSERT" : "EXTRACT"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** How long the upgrade takes; long enough to see it happen, short enough not to be a chore. */
    public static final int UPGRADE_TICKS = 50;

    private static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return UPGRADE_TICKS;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;   // the scrubbing motion: it reads as rubbing the core into the card
    }

    /** Sparks and a rising hum while the core is absorbed. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int done = UPGRADE_TICKS - remaining;
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) {
            // everything happens in the gap between the two hands, right in front of the eyes
            Vec3 hands = entity.getEyePosition().add(entity.getLookAngle().scale(0.65)).subtract(0, 0.25, 0);
            double a = done * 0.55, r = 0.28 - done * 0.004;
            for (int i = 0; i < 2; i++) {
                double t = a + i * Math.PI;                       // two arms of the swirl, one per hand
                level.addParticle(ParticleTypes.ENCHANT, hands.x + Math.cos(t) * r, hands.y + Math.sin(t * 1.7) * 0.08,
                    hands.z + Math.sin(t) * r, -Math.cos(t) * 0.04, 0.01, -Math.sin(t) * 0.04);
            }
            if (done % 2 == 0)                                    // friction sparks off the core
                level.addParticle(done > UPGRADE_TICKS * 2 / 3 ? ParticleTypes.END_ROD : ParticleTypes.CRIT,
                    hands.x + (level.getRandom().nextDouble() - 0.5) * 0.2, hands.y + (level.getRandom().nextDouble() - 0.5) * 0.12,
                    hands.z + (level.getRandom().nextDouble() - 0.5) * 0.2,
                    (level.getRandom().nextDouble() - 0.5) * 0.08, level.getRandom().nextDouble() * 0.05, (level.getRandom().nextDouble() - 0.5) * 0.08);
        } else {
            float t = done / (float) UPGRADE_TICKS;
            if (done % 5 == 0)                                    // the rub itself
                level.playSound(null, entity.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.6f, 0.8f + t);
            if (done % 10 == 0)                                   // the spell rising under it
                level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5f, 0.7f + t);
        }
    }

    /** The moment it becomes the next card: the core is spent, every component comes along, and the player has now
     *  learned the recipe - doing it by hand once is the point, after that it belongs in a crafting table. */
    @Override
    public ItemStack finishUsingItem(ItemStack card, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return card;
        ItemStack core = player.getItemInHand(other(player.getUsedItemHand()));
        ItemStack out = CardUpgradeRecipe.upgraded(card, core);
        if (out.isEmpty()) return card;
        if (!level.isClientSide()) {
            core.shrink(1);
            level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                CardUpgradeRecipe.learn(sp);
                sp.sendOverlayMessage(Component.translatable("msg.omnilogistics.card_upgraded", out.getHoverName()));
            }
        } else {
            for (int i = 0; i < 24; i++)
                level.addParticle(ParticleTypes.END_ROD, entity.getX(), entity.getEyeY() - 0.3, entity.getZ(),
                    (level.getRandom().nextDouble() - 0.5) * 0.3, level.getRandom().nextDouble() * 0.25, (level.getRandom().nextDouble() - 0.5) * 0.3);
        }
        return out;
    }

    /** The card's filter, assembled from its components. */
    public static FilterSpec spec(ItemStack card) {
        return new FilterSpec(
            card.getOrDefault(OmniLogistics.CARD_FILTER.get(), com.mertokan.omnilogistics.api.FilterRef.EMPTY).stacks(),
            card.getOrDefault(OmniLogistics.CARD_FLAGS.get(), ComponentPredicateEngine.DEFAULT),
            card.getOrDefault(OmniLogistics.CARD_TAGS.get(), List.of()),
            card.getOrDefault(OmniLogistics.CARD_COMPONENTS.get(), List.of()),
            card.getOrDefault(OmniLogistics.CARD_NBT.get(), List.of()));
    }

    /** Level the card points at, or null if unbound / unloaded / out of range. */
    public static @Nullable ServerLevel targetLevel(ItemStack card, ServerLevel from, BlockPos fromPos, int range) {
        GlobalPos gp = card.get(OmniLogistics.CARD_TARGET.get());
        if (gp == null) return null;
        ServerLevel lvl = from.getServer().getLevel(gp.dimension());
        if (lvl == null || !lvl.isLoaded(gp.pos())) return null;
        if (lvl == from && gp.pos().distSqr(fromPos) > (double) range * range) return null;
        return lvl;
    }

    public static @Nullable BlockPos targetPos(ItemStack card) {
        GlobalPos gp = card.get(OmniLogistics.CARD_TARGET.get());
        return gp == null ? null : gp.pos();
    }

    public static Direction targetSide(ItemStack card) {
        Direction d = card.get(OmniLogistics.CARD_SIDE.get());
        return d == null ? Direction.UP : d;
    }

    /**
     * Auto input / auto output. A card names a block; WHICH of its faces works is our job. We try the face the card was
     * bound to first (the player pointed at it for a reason), then the other five, then the unsided view - the first one
     * that can actually do the job wins. Nobody has to configure a Mekanism side, or guess which face of a foreign
     * machine is the output, or even click the right face when binding.
     */
    private static @Nullable IItemHandler face(ItemStack card, ServerLevel from, BlockPos fromPos, int range, Predicate<IItemHandler> works) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        if (lvl == null) return null;
        BlockPos target = targetPos(card);
        Direction bound = targetSide(card);
        boolean hasFaces = false;
        for (Direction d : sides(bound)) {
            IItemHandler h = Caps.items(lvl, target, d);
            if (h == null) continue;
            hasFaces = true;
            if (works.test(h)) return h;
        }
        // The null side is a machine raw internals: slots it never meant to expose - an assembler pattern slot, a
        // crafting output, a smelting result. Forcing a FACE is our job; reaching behind a declared interface is not.
        // So the unsided handler is only for blocks that expose no face at all.
        if (hasFaces) return null;
        IItemHandler any = Caps.items(lvl, target, null);
        return any != null && works.test(any) ? any : null;
    }

    /** The face that will take this stack (or at least part of it). */
    public static @Nullable IItemHandler insertTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range, ItemStack stack) {
        if (stack.isEmpty()) return null;
        return face(card, from, fromPos, range, h -> ItemHandlerHelper.insertItem(h, stack.copy(), true).getCount() < stack.getCount());
    }

    /** The face that is holding something the filter wants. */
    public static @Nullable IItemHandler extractTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range, Predicate<ItemStack> want) {
        return face(card, from, fromPos, range, h -> {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.extractItem(i, 1, true);
                if (!s.isEmpty() && want.test(s)) return true;
            }
            return false;
        });
    }

    /** Same idea for FE: the face that will take (or give) power. */
    public static @Nullable IEnergyStorage energyTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range, boolean extract) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        if (lvl == null) return null;
        BlockPos target = targetPos(card);
        Direction bound = targetSide(card);
        for (Direction d : sides(bound)) {
            IEnergyStorage e = Caps.energy(lvl, target, d);
            // probe with a real amount first: Mekanism converts FE to Joules and rounds 1 FE down to nothing, so a
            // one-unit probe answers "this face takes no power" on a machine that is perfectly happy to be charged
            if (e != null && (extract ? e.extractEnergy(1000, true) > 0 || e.extractEnergy(1, true) > 0
                                      : e.receiveEnergy(1000, true) > 0 || e.receiveEnergy(1, true) > 0)) return e;
        }
        return Caps.energy(lvl, target, null);
    }

    /** And for fluid: the face that will take (or give) this much. */
    public static @Nullable IFluidHandler fluidTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range, boolean extract) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        if (lvl == null) return null;
        BlockPos target = targetPos(card);
        boolean hasFaces = false;
        for (Direction d : sides(targetSide(card))) {
            IFluidHandler f = Caps.fluids(lvl, target, d);
            if (f == null || f.getTanks() == 0) continue;
            hasFaces = true;
            if (extract ? !f.getFluidInTank(0).isEmpty() : f.getTankCapacity(0) > f.getFluidInTank(0).getAmount()) return f;
        }
        if (hasFaces) return null;   // same rule as items: an output tank is not ours to fill
        return Caps.fluids(lvl, target, null);
    }

    /** The bound face first, then the rest. */
    private static Direction[] sides(@Nullable Direction bound) {
        if (bound == null) return Direction.values();
        Direction[] out = new Direction[6];
        out[0] = bound;
        int i = 1;
        for (Direction d : Direction.values()) if (d != bound) out[i++] = d;
        return out;
    }

    public static @Nullable IItemHandler resolveTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        return lvl == null ? null : Caps.items(lvl, targetPos(card), targetSide(card));
    }

    public static @Nullable IEnergyStorage resolveEnergyTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        return lvl == null ? null : Caps.energy(lvl, targetPos(card), targetSide(card));
    }

    public static @Nullable IFluidHandler resolveFluidTarget(ItemStack card, ServerLevel from, BlockPos fromPos, int range) {
        ServerLevel lvl = targetLevel(card, from, fromPos, range);
        return lvl == null ? null : Caps.fluids(lvl, targetPos(card), targetSide(card));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> out, TooltipFlag flag) {
        List<Component> tip = new java.util.ArrayList<>();
        describe(stack, tip);
        tip.forEach(out);
    }

    private void describe(ItemStack stack, List<Component> tip) {
        if (kind.hasMode) tip.add(Component.translatable("tooltip.omnilogistics.card_mode", extractMode(stack) ? "EXTRACT" : "INSERT"));
        GlobalPos gp = stack.get(OmniLogistics.CARD_TARGET.get());
        Direction side = stack.get(OmniLogistics.CARD_SIDE.get());
        if (gp == null) {
            tip.add(Component.translatable("tooltip.omnilogistics.card_unbound"));
        } else {
            tip.add(Component.translatable("tooltip.omnilogistics.card_bound_to", boundName(stack)).withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("tooltip.omnilogistics.card_target", gp.pos().toShortString(),
                side == null ? "-" : side.getName(), gp.dimension().identifier().getPath()).withStyle(ChatFormatting.DARK_GRAY));
            tip.add(Component.translatable("tooltip.omnilogistics.card_locate",
                Component.keybind("key.omnilogistics.locate")).withStyle(ChatFormatting.DARK_GRAY));
        }
        int sp = speed(stack);
        if (sp > 0) tip.add(Component.translatable("tooltip.omnilogistics.card_speed", sp, 1 << sp));
        if (kind.hasFilter) describeFilter(stack, tip);
    }

    /** Reference item, tags and components of the card's filter, one line each (tooltip and Jade). */
    public static void describeFilter(ItemStack card, List<Component> out) {
        FilterSpec s = spec(card);
        long n = s.refs().stream().filter(r -> !r.isEmpty()).count();
        if (n == 1) out.add(Component.translatable("tooltip.omnilogistics.card_filter", s.ref().getHoverName()));
        else if (n > 1) out.add(Component.translatable("tooltip.omnilogistics.card_filter_n", n, s.ref().getHoverName()));
        if (!s.tags().isEmpty()) out.add(Component.translatable("tooltip.omnilogistics.card_tag", String.join(", ", s.tags())));
        if (!s.components().isEmpty()) out.add(Component.translatable("tooltip.omnilogistics.card_components", String.join(", ", s.components())));
        if (!s.rules().isEmpty()) {
            boolean any = (s.flags() & ComponentPredicateEngine.NBT_ANY) != 0;
            out.add(Component.translatable(any ? "tooltip.omnilogistics.card_nbt_any" : "tooltip.omnilogistics.card_nbt_all", s.rules().size()));
            for (var r : s.rules()) {
                if (!r.enabled()) continue;
                out.add(Component.literal("  " + com.mertokan.omnilogistics.api.NbtRule.pretty(r.path()) + " ")
                    .append(Component.translatable("gui.omnilogistics.nbt.op." + r.op().key()))
                    .append(r.op().takesValue() ? " " + r.value() : "").withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }
}
