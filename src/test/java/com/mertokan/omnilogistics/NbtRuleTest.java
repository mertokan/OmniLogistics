package com.mertokan.omnilogistics;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.api.NbtRule;
import com.mertokan.omnilogistics.api.NbtRule.Op;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mertokan.omnilogistics.api.ComponentPredicateEngine.*;
import static org.junit.jupiter.api.Assertions.*;

class NbtRuleTest {
    static HolderLookup.Provider regs;

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        regs = VanillaRegistries.createLookup();
    }

    static NbtRule rule(Op op, String value, String... path) {
        return new NbtRule(List.of(path), op, value, true);
    }

    static boolean test(ItemStack stack, NbtRule... rules) {
        return NbtRule.test(stack, List.of(rules), false, regs);
    }

    @Test
    void durabilityRangeIncludingTheItemDefault() {
        ItemStack fresh = new ItemStack(Items.DIAMOND_SWORD);   // damage 0 is a default component, not in the patch
        ItemStack worn = fresh.copy();
        worn.set(DataComponents.DAMAGE, 900);
        NbtRule lightlyUsed = rule(Op.LE, "10", "minecraft:damage");
        assertTrue(test(fresh, lightlyUsed), "a fresh sword has damage 0 through its defaults");
        assertFalse(test(worn, lightlyUsed));
        assertTrue(test(worn, rule(Op.GT, "500", "minecraft:damage")));
        assertTrue(test(worn, rule(Op.EQ, "900", "minecraft:damage")));
        assertTrue(test(worn, rule(Op.EQ, "900.0d", "minecraft:damage")), "numbers compare by value, not NBT width");
        assertTrue(test(worn, rule(Op.NE, "1", "minecraft:damage")));
    }

    @Test
    void enchantmentLevelDeepInTheTree() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(regs.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 4);
        String[] path = {"minecraft:enchantments", "levels", "minecraft:sharpness"};
        assertTrue(test(sword, rule(Op.GE, "3", path)));
        assertFalse(test(sword, rule(Op.GE, "5", path)));
        assertTrue(test(sword, rule(Op.EXISTS, "", path)));
        assertFalse(test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.EXISTS, "", path)));
        assertTrue(test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.ABSENT, "", path)));
    }

    @Test
    void modCustomDataAndText() {
        ItemStack cell = new ItemStack(Items.PAPER);
        CompoundTag data = new CompoundTag();
        data.putLong("Energy", 250_000L);
        data.putString("Owner", "Mert");
        cell.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        cell.set(DataComponents.CUSTOM_NAME, Component.literal("Backup Cell"));
        assertTrue(test(cell, rule(Op.GE, "100000", "minecraft:custom_data", "Energy")));
        assertTrue(test(cell, rule(Op.EQ, "Mert", "minecraft:custom_data", "Owner")), "plain text works without quotes");
        assertTrue(test(cell, rule(Op.EQ, "\"Mert\"", "minecraft:custom_data", "Owner")));
        assertTrue(test(cell, rule(Op.CONTAINS, "backup", "minecraft:custom_name")), "contains ignores case");
        assertTrue(test(cell, rule(Op.CONTAINS, "Energy", "minecraft:custom_data")), "contains on a compound = has key");
    }

    @Test
    void anyElementOfAList() {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
            List.of(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 3))));
        List<NbtRule.Leaf> leaves = NbtRule.leaves(box, regs);
        NbtRule.Leaf diamond = leaves.stream().filter(l -> l.value().contains("diamond")).findFirst().orElseThrow();
        NbtRule exact = new NbtRule(diamond.path(), Op.EQ, diamond.value(), true);
        assertTrue(test(box, exact), "a leaf picked from the item matches that item");
        NbtRule anywhere = exact.anyIndex();
        assertTrue(anywhere.path().contains(NbtRule.ANY));
        ItemStack moved = new ItemStack(Items.SHULKER_BOX);
        moved.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        assertFalse(test(moved, exact), "the picked index is a different slot here");
        assertTrue(test(moved, anywhere), "any element finds it in whatever slot it sits");
        assertTrue(anywhere.anyIndex().path().stream().noneMatch(NbtRule.ANY::equals), "and back to a fixed position");
    }

    @Test
    void allAnyAndDisabled() {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        NbtRule yes = rule(Op.EQ, "0", "minecraft:damage"), no = rule(Op.GT, "5", "minecraft:damage");
        assertFalse(NbtRule.test(sword, List.of(yes, no), false, regs));
        assertTrue(NbtRule.test(sword, List.of(yes, no), true, regs));
        assertTrue(NbtRule.test(sword, List.of(yes, no.toggled()), false, regs), "a switched-off rule is ignored");
        assertTrue(NbtRule.test(sword, List.of(no.toggled()), true, regs), "no enabled rule lets everything through");
    }

    @Test
    void wiredIntoTheFilterAndSaved() {
        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE), worn = fresh.copy();
        worn.set(DataComponents.DAMAGE, 200);
        FilterSpec spec = new FilterSpec(List.of(), MATCH_NBT, List.of(), List.of(), List.of(rule(Op.LT, "50", "minecraft:damage")));
        assertTrue(spec.test(fresh));
        assertFalse(spec.test(worn));
        assertTrue(spec.withConfig(MATCH_NBT | INVERT, List.of(), List.of(), spec.rules()).test(worn));

        CompoundTag tag = new CompoundTag();
        spec.save(tag, regs);
        FilterSpec back = FilterSpec.load(tag, regs);
        assertEquals(spec.rules(), back.rules());
        assertEquals(spec.rules(), NbtRule.CODEC.listOf().parse(NbtOps.INSTANCE,
            NbtRule.CODEC.listOf().encodeStart(NbtOps.INSTANCE, spec.rules()).getOrThrow()).getOrThrow());
    }

    @Test
    void cleanCapsWhatAClientSends() {
        List<NbtRule> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(rule(Op.EQ, "x".repeat(1000), "minecraft:damage"));
        many.add(new NbtRule(List.of(), Op.EQ, "1", true));
        List<NbtRule> clean = NbtRule.clean(many);
        assertEquals(NbtRule.MAX_RULES, clean.size());
        assertTrue(clean.stream().allMatch(r -> r.value().length() <= NbtRule.MAX_VALUE && !r.path().isEmpty()));
    }
}
