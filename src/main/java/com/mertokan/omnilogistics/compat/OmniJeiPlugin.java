package com.mertokan.omnilogistics.compat;

import com.google.gson.JsonObject;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mojang.logging.LogUtils;
import com.mertokan.omnilogistics.miner.MinerTier;
import com.mertokan.omnilogistics.pipe.PipeTier;
import com.mertokan.omnilogistics.pipe.PipeType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ItemLike;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JEI: press U on the Component Extractor to see what it does (Extraction / Fusion category), and U on any
 * OmniLogistics item for its description. Only loaded when JEI is installed (JEI scans for this annotation).
 */
@JeiPlugin
public class OmniJeiPlugin implements IModPlugin {
    public static final RecipeType<ExtractionRecipe> EXTRACTION = RecipeType.create(OmniLogistics.MODID, "extraction", ExtractionRecipe.class);

    /**
     * The card tier upgrade, for JEI only. The real recipe is a {@code CustomRecipe} (it has to be: it copies the
     * card's filter, binding, mode and overclock onto the new card), and JEI hides special recipes - which is why
     * nobody could find a recipe for this. These three shapeless stand-ins are never registered with the game, so they
     * cannot clash with the real one; they exist to be looked at.
     */
    private static List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> cardUpgrades() {
        var out = new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>>();
        int[] caps = {4, 16, 64};
        ItemStack[] cores = {new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.DIAMOND), new ItemStack(Items.NETHERITE_INGOT)};
        ItemStack from = new ItemStack(OmniLogistics.CARD.get());
        for (int i = 0; i < caps.length; i++) {
            ItemStack to = new ItemStack(OmniLogistics.MULTI_CARDS.get(caps[i]).get());
            var ingredients = net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                net.minecraft.world.item.crafting.Ingredient.of(from), net.minecraft.world.item.crafting.Ingredient.of(cores[i]));
            out.add(new net.minecraft.world.item.crafting.RecipeHolder<>(
                ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "card_upgrade_" + caps[i]),
                new net.minecraft.world.item.crafting.ShapelessRecipe("", net.minecraft.world.item.crafting.CraftingBookCategory.MISC, to, ingredients)));
            from = to;
        }
        return out;
    }

    /** A worked example shown in JEI: gear (+ donor) -> outputs. */
    public record ExtractionRecipe(ItemStack gear, ItemStack donor, ItemStack result, List<ItemStack> extras, boolean fuse, String noteKey) {}

    public static final RecipeType<MinerDrops> MINER = RecipeType.create(OmniLogistics.MODID, "miner", MinerDrops.class);

    /** One miner tier's JEI page: items and the already-formatted share of the table, same index. */
    public record MinerDrops(MinerTier tier, List<ItemStack> items, List<String> chance) {}

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration r) {
        var gui = r.getJeiHelpers().getGuiHelper();
        r.addRecipeCategories(new ExtractionCategory(gui), new MinerCategory(gui));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration r) {
        r.addRecipeCatalyst(new ItemStack(OmniLogistics.EXTRACTOR_ITEM.get()), EXTRACTION);
        for (MinerTier tier : MinerTier.values()) r.addRecipeCatalyst(OmniLogistics.MINER_ITEMS.get(tier).get(), MINER);
    }

    @Override
    public void registerRecipes(IRecipeRegistration r) {
        r.addRecipes(EXTRACTION, examples());
        r.addRecipes(MINER, minerDrops());
        r.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, cardUpgrades());
        for (PipeType type : PipeType.values())
            for (PipeTier tier : PipeTier.values())
                info(r, OmniLogistics.PIPE_ITEMS.get(type).get(tier).get(), "desc.omnilogistics." + OmniLogistics.pipeId(type, tier), "jei.omnilogistics.pipe_tiers");
        info(r, OmniLogistics.EXPOSER_ITEM.get(), "desc.omnilogistics.inventory_exposer", "jei.omnilogistics.exposer");
        info(r, OmniLogistics.ROUTER_ITEM.get(), "desc.omnilogistics.wireless_router", "jei.omnilogistics.router");
        info(r, OmniLogistics.EXTRACTOR_ITEM.get(), "desc.omnilogistics.component_extractor", "jei.omnilogistics.extractor");
        for (MinerTier tier : MinerTier.values())
            info(r, OmniLogistics.MINER_ITEMS.get(tier).get(), "desc.omnilogistics." + tier.getSerializedName() + "_void_miner", "jei.omnilogistics.miner");
        info(r, OmniLogistics.DISTRIBUTOR_ITEM.get(), "desc.omnilogistics.batch_distributor", "jei.omnilogistics.distributor");
        info(r, OmniLogistics.MONITOR_ITEM.get(), "desc.omnilogistics.machine_monitor", "jei.omnilogistics.monitor");
        info(r, OmniLogistics.CARDS.get(com.mertokan.omnilogistics.router.CardKind.STOCK).get(), "desc.omnilogistics.stock_card", "jei.omnilogistics.stock_card");
        info(r, OmniLogistics.ROUTER_ITEM.get(), "jei.omnilogistics.crossdim");
        info(r, OmniLogistics.CARD.get(), "jei.omnilogistics.autoface");
        for (int cap : new int[]{4, 16, 64})
            info(r, OmniLogistics.MULTI_CARDS.get(cap).get(), "jei.omnilogistics.card_upgrade");
        info(r, OmniLogistics.CARD.get(), "desc.omnilogistics.logistics_card", "jei.omnilogistics.card");
        for (int cap : new int[]{4, 16, 64})
            info(r, OmniLogistics.MULTI_CARDS.get(cap).get(), "desc.omnilogistics." + (cap == 4 ? "advanced" : cap == 16 ? "elite" : "ultimate") + "_logistics_card", "jei.omnilogistics.multi_card");
        info(r, OmniLogistics.ENERGY_CARD.get(), "desc.omnilogistics.energy_card");
        info(r, OmniLogistics.WRENCH.get(), "desc.omnilogistics.wrench");
        info(r, OmniLogistics.SPEED_UPGRADE.get(), "desc.omnilogistics.speed_upgrade");
        info(r, OmniLogistics.PARALLEL_UPGRADE.get(), "desc.omnilogistics.parallel_upgrade");
        info(r, OmniLogistics.RANGE_UPGRADE.get(), "desc.omnilogistics.range_upgrade");
        info(r, OmniLogistics.GEM_MODULE.get(), "desc.omnilogistics.gem_module");
        info(r, OmniLogistics.FUSION_MODULE.get(), "desc.omnilogistics.fusion_module");
    }

    private static void info(IRecipeRegistration r, ItemLike item, String... keys) {
        List<Component> lines = new ArrayList<>();
        for (String k : keys) lines.add(Component.translatable(k));
        r.addIngredientInfo(item, lines.toArray(Component[]::new));
    }

    private static List<ExtractionRecipe> examples() {
        List<ExtractionRecipe> out = new ArrayList<>();
        var level = Minecraft.getInstance().level;
        if (level == null) return out;
        Holder<Enchantment> sharp = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
        Holder<Enchantment> unbreaking = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);

        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharp, 5);
        sword.enchant(unbreaking, 3);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(sharp, 5);
        m.set(unbreaking, 3);
        book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        out.add(new ExtractionRecipe(sword, new ItemStack(Items.BOOK), new ItemStack(Items.DIAMOND_SWORD), List.of(book), false, "jei.omnilogistics.note_extract"));
        out.add(new ExtractionRecipe(sword, new ItemStack(Items.BOOKSHELF), new ItemStack(Items.DIAMOND_SWORD), List.of(book), false, "jei.omnilogistics.note_bookshelf"));
        out.add(new ExtractionRecipe(new ItemStack(Items.NETHERITE_CHESTPLATE), ItemStack.EMPTY, new ItemStack(Items.NETHERITE_CHESTPLATE),
            List.of(new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)), false, "jei.omnilogistics.note_gems"));
        ItemStack fused = new ItemStack(Items.DIAMOND_SWORD);
        fused.enchant(sharp, 5);
        fused.enchant(unbreaking, 3);
        out.add(new ExtractionRecipe(new ItemStack(Items.DIAMOND_SWORD), book, fused, List.of(), true, "jei.omnilogistics.note_fuse"));
        return out;
    }

    /** ponytail: reads the shipped display copy of the weights (assets/omnilogistics/miner_loot.json, written by
     *  tools/gen_resources.py from the same MINER_LOOT dict as the loot tables). Loot tables live in the server's
     *  reloadable registries and are never synced, so a datapack override of omnilogistics:miner/<tier> is not shown
     *  here (said so in the catalyst tooltip). Upgrade path: encode the resolved table server-side and send it on
     *  OnDatapackSyncEvent, parsed by this same loop. */
    private static List<MinerDrops> minerDrops() {
        List<MinerDrops> out = new ArrayList<>();
        var res = Minecraft.getInstance().getResourceManager()
            .getResource(ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "miner_loot.json"));
        if (res.isEmpty()) return out;
        try (BufferedReader in = res.get().openAsReader()) {
            JsonObject root = GsonHelper.parse(in);
            for (MinerTier tier : MinerTier.values()) {
                JsonObject o = root.getAsJsonObject(tier.getSerializedName());
                if (o == null) continue;
                int total = 0;                                   // over every entry, so a missing item does not renormalise the rest
                for (var e : o.entrySet()) total += e.getValue().getAsInt();
                List<ItemStack> items = new ArrayList<>();
                List<String> chance = new ArrayList<>();
                for (var e : o.entrySet()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey()));
                    if (item == Items.AIR) continue;             // item removed by a pack / missing mod
                    items.add(new ItemStack(item));
                    chance.add(String.format(Locale.ROOT, "%.1f", 100.0 * e.getValue().getAsInt() / total));
                }
                if (!items.isEmpty()) out.add(new MinerDrops(tier, items, chance));
            }
        } catch (Exception ex) {
            LogUtils.getLogger().warn("omnilogistics: miner_loot.json unreadable, JEI miner page skipped", ex);
        }
        return out;
    }
}
