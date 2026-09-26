package com.mertokan.omnilogistics;

import net.minecraft.gametest.framework.GameTestHelper;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.extractor.ComponentExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import static com.mertokan.omnilogistics.api.ComponentPredicateEngine.*;

/** Generated from the 1.21.1 JUnit suite EngineAndExtractorTest: on 26.1 an ItemStack needs a running server. */
public final class EngineGameTests {
    static HolderLookup.Provider regs;
    static Holder<Enchantment> sharpness;

    private static void setup(GameTestHelper h) {
        regs = h.getLevel().registryAccess();
        sharpness = regs.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void flags(GameTestHelper h) {
        setup(h);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack enchanted = sword.copy();
        enchanted.enchant(sharpness, 1);
        ItemStack damaged = sword.copy();
        damaged.set(DataComponents.DAMAGE, 10);

        GameTests.check(h, test(sword, sword, MATCH_ITEM), "test(sword, sword, MATCH_ITEM)");
        GameTests.check(h, !(test(new ItemStack(Items.STONE), sword, MATCH_ITEM)), "!(test(new ItemStack(Items.STONE), sword, MATCH_ITEM))");
        GameTests.check(h, test(new ItemStack(Items.STONE), sword, MATCH_ITEM | INVERT), "test(new ItemStack(Items.STONE), sword, MATCH_ITEM | INVERT)");
        GameTests.check(h, !(test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS)), "!(test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS))");
        GameTests.check(h, test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS | IGNORE_DAMAGE), "test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS | IGNORE_DAMAGE)");
        GameTests.check(h, test(enchanted, ItemStack.EMPTY, HAS_ENCHANTS), "test(enchanted, ItemStack.EMPTY, HAS_ENCHANTS)");
        GameTests.check(h, !(test(sword, ItemStack.EMPTY, HAS_ENCHANTS)), "!(test(sword, ItemStack.EMPTY, HAS_ENCHANTS))");
        GameTests.check(h, !(test(enchanted, ItemStack.EMPTY, HAS_MOD_COMPONENTS)), "!(test(enchanted, ItemStack.EMPTY, HAS_MOD_COMPONENTS))");
        GameTests.check(h, !(test(ItemStack.EMPTY, sword, INVERT)), "!(test(ItemStack.EMPTY, sword, INVERT))");

        // book with Sharpness III in the filter slot, Match Item off: only gear with Sharpness >= III passes
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(sharpness, 3);
        book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        ItemStack sharp3 = sword.copy();
        sharp3.enchant(sharpness, 3);
        GameTests.check(h, test(sharp3, book, MATCH_ENCHANTS), "test(sharp3, book, MATCH_ENCHANTS)");
        GameTests.check(h, !(test(enchanted, book, MATCH_ENCHANTS)), "!(test(enchanted, book, MATCH_ENCHANTS))"); // only level I
        GameTests.check(h, !(test(sword, book, MATCH_ENCHANTS)), "!(test(sword, book, MATCH_ENCHANTS))");
        GameTests.check(h, !(test(sharp3, book, MATCH_ENCHANTS | MATCH_ITEM)), "!(test(sharp3, book, MATCH_ENCHANTS | MATCH_ITEM))"); // sword is not a book

        // a 4 / 16 / 64 card: any of the references may match, everything else is still refused
        FilterSpec multi = new FilterSpec(java.util.List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.IRON_INGOT)),
            MATCH_ITEM, java.util.List.of(), java.util.List.of());
        GameTests.check(h, multi.test(new ItemStack(Items.COBBLESTONE)), "multi.test(new ItemStack(Items.COBBLESTONE))");
        GameTests.check(h, multi.test(new ItemStack(Items.IRON_INGOT)), "multi.test(new ItemStack(Items.IRON_INGOT))");
        GameTests.check(h, !(multi.test(new ItemStack(Items.GOLD_INGOT))), "!(multi.test(new ItemStack(Items.GOLD_INGOT)))");
        GameTests.check(h, multi.withConfig(MATCH_ITEM | INVERT, java.util.List.of(), java.util.List.of(), java.util.List.of()).test(new ItemStack(Items.GOLD_INGOT)), "multi.withConfig(MATCH_ITEM | INVERT, java.util.List.of(), java.util.List.of(), java.util.List.of()).test(new ItemStack(Items.GOLD_INGOT))");

        // MATCH_TAG is not unit-testable here: item tags are datapack data, not bound by Bootstrap. Covered in-game.

        // only the chosen component must match: damage equal, enchantments ignored
        FilterSpec byDamage = new FilterSpec(java.util.List.of(damaged), MATCH_SELECTED, java.util.List.of(), java.util.List.of("minecraft:damage"));
        ItemStack damagedEnchanted = damaged.copy();
        damagedEnchanted.enchant(sharpness, 1);
        GameTests.check(h, byDamage.test(damagedEnchanted), "byDamage.test(damagedEnchanted)");
        GameTests.check(h, !(byDamage.test(sword)), "!(byDamage.test(sword))");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void extractorMovesEnchantsToBook(GameTestHelper h) {
        setup(h);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness, 3);

        ComponentExtractor.Result r = ComponentExtractor.extract(sword, true, false, regs);
        GameTests.check(h, r.usedBook(), "r.usedBook()");
        GameTests.check(h, !(hasEnchantments(r.cleaned())), "!(hasEnchantments(r.cleaned()))");
        GameTests.check(h, java.util.Objects.equals(1, r.extracted().size()), "java.util.Objects.equals(1, r.extracted().size())");
        GameTests.check(h, r.extracted().get(0).is(Items.ENCHANTED_BOOK), "r.extracted().get(0).is(Items.ENCHANTED_BOOK)");
        GameTests.check(h, java.util.Objects.equals(3, r.extracted().get(0).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness)), "java.util.Objects.equals(3, r.extracted().get(0).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness))");

        ComponentExtractor.Result noBook = ComponentExtractor.extract(sword, false, false, regs);
        GameTests.check(h, !(noBook.usedBook()), "!(noBook.usedBook())");
        GameTests.check(h, noBook.extracted().isEmpty(), "noBook.extracted().isEmpty()");
        GameTests.check(h, hasEnchantments(noBook.cleaned()), "hasEnchantments(noBook.cleaned())");
        h.succeed();
    }
}
