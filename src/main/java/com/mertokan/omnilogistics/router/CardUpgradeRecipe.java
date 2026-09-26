package com.mertokan.omnilogistics.router;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/**
 * Crafting grid, two jobs, both of which keep every component the card carries (filter, binding, mode, speed):
 * one card + N Speed Upgrades -> the same card overclocked (max 3, LaserIO-style), and
 * one Logistics Card + gold / diamond / netherite -> the 4 / 16 / 64 reference version of it.
 */
public class CardUpgradeRecipe extends CustomRecipe {
    /** No settings of its own, so one instance serves every recipe file that names this type. */
    public static final CardUpgradeRecipe INSTANCE = new CardUpgradeRecipe();
    public static final RecipeSerializer<CardUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(
        com.mojang.serialization.MapCodec.unit(INSTANCE), net.minecraft.network.codec.StreamCodec.unit(INSTANCE));

    private CardUpgradeRecipe() {
    }

    /** upgrades[0] = speed upgrades in the grid, upgrades[1] = the capacity the tier core upgrades to (0 = none). */
    private static ItemStack find(CraftingInput in, int[] upgrades) {
        ItemStack card = ItemStack.EMPTY;
        upgrades[0] = 0;
        upgrades[1] = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof LogisticsCardItem) {
                if (!card.isEmpty()) return ItemStack.EMPTY;
                card = s;
            } else if (s.is(OmniLogistics.SPEED_UPGRADE.get())) {
                upgrades[0] += s.getCount();
            } else if (core(s) != 0) {
                if (upgrades[1] != 0 || s.getCount() != 1) return ItemStack.EMPTY;
                upgrades[1] = core(s);
            } else {
                return ItemStack.EMPTY;
            }
        }
        return card;
    }

    /** Which capacity this ingredient upgrades a card to, or 0 when it is not a tier core. */
    public static int core(ItemStack s) {
        if (s.is(Tags.Items.INGOTS_GOLD)) return 4;
        if (s.is(Tags.Items.GEMS_DIAMOND)) return 16;
        if (s.is(Tags.Items.INGOTS_NETHERITE)) return 64;
        return 0;
    }

    /** The tier core only upgrades an ITEM card, and only to the next step up from what it already holds. */
    public static boolean canTier(ItemStack card, int to) {
        if (!(card.getItem() instanceof LogisticsCardItem c) || c.kind != CardKind.ITEM) return false;
        int from = c.capacity;
        return to == (from == 1 ? 4 : from == 4 ? 16 : from == 16 ? 64 : 0);
    }

    /** The next card up, carrying everything this one had. EMPTY when this core cannot upgrade this card. */
    public static ItemStack upgraded(ItemStack card, ItemStack core) {
        int to = core(core);
        if (to == 0 || !canTier(card, to)) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(OmniLogistics.MULTI_CARDS.get(to).get());
        out.applyComponents(card.getComponentsPatch());
        return out;
    }

    @Override
    public boolean matches(CraftingInput in, Level level) {
        int[] n = new int[2];
        ItemStack card = find(in, n);
        if (card.isEmpty() || (n[0] > 0) == (n[1] > 0)) return false;   // exactly one of the two jobs
        return n[1] > 0 ? canTier(card, n[1]) : LogisticsCardItem.speed(card) + n[0] <= LogisticsCardItem.MAX_SPEED;
    }

    @Override
    public ItemStack assemble(CraftingInput in) {
        int[] n = new int[2];
        ItemStack card = find(in, n);
        if (n[1] > 0) {   // tier up: a bigger card carrying everything the old one had
            ItemStack out = new ItemStack(OmniLogistics.MULTI_CARDS.get(n[1]).get());
            out.applyComponents(card.getComponentsPatch());
            return out;
        }
        ItemStack out = card.copyWithCount(1);
        out.set(OmniLogistics.CARD_SPEED.get(), LogisticsCardItem.speed(out) + n[0]);
        return out;
    }

    /** Doing it by hand once is the tutorial: it hands the player the advancement that says the crafting table can do
     *  this too, which is what everybody wants the moment they start automating. */
    public static void learn(net.minecraft.server.level.ServerPlayer player) {
        var advancements = player.level().getServer().getAdvancements()
            .get(net.minecraft.resources.Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "card_upgrade"));
        if (advancements != null) player.getAdvancements().award(advancements, "code");
    }

    @Override
    public RecipeSerializer<CardUpgradeRecipe> getSerializer() {
        return SERIALIZER;
    }
}
