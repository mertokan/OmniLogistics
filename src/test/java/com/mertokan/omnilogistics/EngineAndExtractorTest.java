package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.extractor.ComponentExtractor;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.mertokan.omnilogistics.api.ComponentPredicateEngine.*;
import static org.junit.jupiter.api.Assertions.*;

class EngineAndExtractorTest {
    static HolderLookup.Provider regs;
    static Holder<Enchantment> sharpness;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        regs = VanillaRegistries.createLookup();
        sharpness = regs.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS);
    }

    @Test
    void flags() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack enchanted = sword.copy();
        enchanted.enchant(sharpness, 1);
        ItemStack damaged = sword.copy();
        damaged.set(DataComponents.DAMAGE, 10);

        assertTrue(test(sword, sword, MATCH_ITEM));
        assertFalse(test(new ItemStack(Items.STONE), sword, MATCH_ITEM));
        assertTrue(test(new ItemStack(Items.STONE), sword, MATCH_ITEM | INVERT));
        assertFalse(test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS));
        assertTrue(test(damaged, sword, MATCH_ITEM | MATCH_COMPONENTS | IGNORE_DAMAGE));
        assertTrue(test(enchanted, ItemStack.EMPTY, HAS_ENCHANTS));
        assertFalse(test(sword, ItemStack.EMPTY, HAS_ENCHANTS));
        assertFalse(test(enchanted, ItemStack.EMPTY, HAS_MOD_COMPONENTS));
        assertFalse(test(ItemStack.EMPTY, sword, INVERT));

        // book with Sharpness III in the filter slot, Match Item off: only gear with Sharpness >= III passes
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        m.set(sharpness, 3);
        book.set(DataComponents.STORED_ENCHANTMENTS, m.toImmutable());
        ItemStack sharp3 = sword.copy();
        sharp3.enchant(sharpness, 3);
        assertTrue(test(sharp3, book, MATCH_ENCHANTS));
        assertFalse(test(enchanted, book, MATCH_ENCHANTS)); // only level I
        assertFalse(test(sword, book, MATCH_ENCHANTS));
        assertFalse(test(sharp3, book, MATCH_ENCHANTS | MATCH_ITEM)); // sword is not a book

        // a 4 / 16 / 64 card: any of the references may match, everything else is still refused
        FilterSpec multi = new FilterSpec(java.util.List.of(new ItemStack(Items.COBBLESTONE), new ItemStack(Items.IRON_INGOT)),
            MATCH_ITEM, java.util.List.of(), java.util.List.of());
        assertTrue(multi.test(new ItemStack(Items.COBBLESTONE)));
        assertTrue(multi.test(new ItemStack(Items.IRON_INGOT)));
        assertFalse(multi.test(new ItemStack(Items.GOLD_INGOT)));
        assertTrue(multi.withConfig(MATCH_ITEM | INVERT, java.util.List.of(), java.util.List.of(), java.util.List.of()).test(new ItemStack(Items.GOLD_INGOT)));

        // MATCH_TAG is not unit-testable here: item tags are datapack data, not bound by Bootstrap. Covered in-game.

        // only the chosen component must match: damage equal, enchantments ignored
        FilterSpec byDamage = new FilterSpec(java.util.List.of(damaged), MATCH_SELECTED, java.util.List.of(), java.util.List.of("minecraft:damage"));
        ItemStack damagedEnchanted = damaged.copy();
        damagedEnchanted.enchant(sharpness, 1);
        assertTrue(byDamage.test(damagedEnchanted));
        assertFalse(byDamage.test(sword));
    }

    @Test
    void extractorMovesEnchantsToBook() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(sharpness, 3);

        ComponentExtractor.Result r = ComponentExtractor.extract(sword, true, false, regs);
        assertTrue(r.usedBook());
        assertFalse(hasEnchantments(r.cleaned()));
        assertEquals(1, r.extracted().size());
        assertTrue(r.extracted().get(0).is(Items.ENCHANTED_BOOK));
        assertEquals(3, r.extracted().get(0).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(sharpness));

        ComponentExtractor.Result noBook = ComponentExtractor.extract(sword, false, false, regs);
        assertFalse(noBook.usedBook());
        assertTrue(noBook.extracted().isEmpty());
        assertTrue(hasEnchantments(noBook.cleaned()));
    }
}
