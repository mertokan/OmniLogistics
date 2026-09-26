package com.mertokan.omnilogistics;

import net.minecraft.gametest.framework.GameTestHelper;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.api.NbtRule;
import com.mertokan.omnilogistics.api.NbtRule.Op;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.List;

import static com.mertokan.omnilogistics.api.ComponentPredicateEngine.*;

/** Generated from the 1.21.1 JUnit suite NbtRuleTest: on 26.1 an ItemStack needs a running server. */
public final class NbtRuleGameTests {
    static HolderLookup.Provider regs;

    private static void setup(GameTestHelper h) {
        regs = h.getLevel().registryAccess();
    }

    static NbtRule rule(Op op, String value, String... path) {
        return new NbtRule(List.of(path), op, value, true);
    }

    static boolean test(ItemStack stack, NbtRule... rules) {
        return NbtRule.test(stack, List.of(rules), false, regs);
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void durabilityRangeIncludingTheItemDefault(GameTestHelper h) {
        setup(h);
        ItemStack fresh = new ItemStack(Items.DIAMOND_SWORD);   // damage 0 is a default component, not in the patch
        ItemStack worn = fresh.copy();
        worn.set(DataComponents.DAMAGE, 900);
        NbtRule lightlyUsed = rule(Op.LE, "10", "minecraft:damage");
        GameTests.check(h, test(fresh, lightlyUsed), "a fresh sword has damage 0 through its defaults");
        GameTests.check(h, !(test(worn, lightlyUsed)), "!(test(worn, lightlyUsed))");
        GameTests.check(h, test(worn, rule(Op.GT, "500", "minecraft:damage")), "test(worn, rule(Op.GT, \"500\", \"minecraft:damage\"))");
        GameTests.check(h, test(worn, rule(Op.EQ, "900", "minecraft:damage")), "test(worn, rule(Op.EQ, \"900\", \"minecraft:damage\"))");
        GameTests.check(h, test(worn, rule(Op.EQ, "900.0d", "minecraft:damage")), "numbers compare by value, not NBT width");
        GameTests.check(h, test(worn, rule(Op.NE, "1", "minecraft:damage")), "test(worn, rule(Op.NE, \"1\", \"minecraft:damage\"))");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void enchantmentLevelDeepInTheTree(GameTestHelper h) {
        setup(h);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(regs.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 4);
        String[] path = {"minecraft:enchantments", "minecraft:sharpness"};   // 26.1: no "levels" wrapper
        GameTests.check(h, test(sword, rule(Op.GE, "3", path)), "test(sword, rule(Op.GE, \"3\", path))");
        GameTests.check(h, !(test(sword, rule(Op.GE, "5", path))), "!(test(sword, rule(Op.GE, \"5\", path)))");
        GameTests.check(h, test(sword, rule(Op.EXISTS, "", path)), "test(sword, rule(Op.EXISTS, \"\", path))");
        GameTests.check(h, !(test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.EXISTS, "", path))), "!(test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.EXISTS, \"\", path)))");
        GameTests.check(h, test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.ABSENT, "", path)), "test(new ItemStack(Items.DIAMOND_SWORD), rule(Op.ABSENT, \"\", path))");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void modCustomDataAndText(GameTestHelper h) {
        setup(h);
        ItemStack cell = new ItemStack(Items.PAPER);
        CompoundTag data = new CompoundTag();
        data.putLong("Energy", 250_000L);
        data.putString("Owner", "Mert");
        cell.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        cell.set(DataComponents.CUSTOM_NAME, Component.literal("Backup Cell"));
        GameTests.check(h, test(cell, rule(Op.GE, "100000", "minecraft:custom_data", "Energy")), "test(cell, rule(Op.GE, \"100000\", \"minecraft:custom_data\", \"Energy\"))");
        GameTests.check(h, test(cell, rule(Op.EQ, "Mert", "minecraft:custom_data", "Owner")), "plain text works without quotes");
        GameTests.check(h, test(cell, rule(Op.EQ, "\"Mert\"", "minecraft:custom_data", "Owner")), "test(cell, rule(Op.EQ, \"\\\"Mert\\\"\", \"minecraft:custom_data\", \"Owner\"))");
        GameTests.check(h, test(cell, rule(Op.CONTAINS, "backup", "minecraft:custom_name")), "contains ignores case");
        GameTests.check(h, test(cell, rule(Op.CONTAINS, "Energy", "minecraft:custom_data")), "contains on a compound = has key");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void anyElementOfAList(GameTestHelper h) {
        setup(h);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
            List.of(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, new ItemStack(Items.DIAMOND, 3))));
        List<NbtRule.Leaf> leaves = NbtRule.leaves(box, regs);
        NbtRule.Leaf diamond = leaves.stream().filter(l -> l.value().contains("diamond")).findFirst().orElseThrow();
        NbtRule exact = new NbtRule(diamond.path(), Op.EQ, diamond.value(), true);
        GameTests.check(h, test(box, exact), "a leaf picked from the item matches that item");
        NbtRule anywhere = exact.anyIndex();
        GameTests.check(h, anywhere.path().contains(NbtRule.ANY), "anywhere.path().contains(NbtRule.ANY)");
        ItemStack moved = new ItemStack(Items.SHULKER_BOX);
        moved.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        GameTests.check(h, !(test(moved, exact)), "the picked index is a different slot here");
        GameTests.check(h, test(moved, anywhere), "any element finds it in whatever slot it sits");
        GameTests.check(h, anywhere.anyIndex().path().stream().noneMatch(NbtRule.ANY::equals), "and back to a fixed position");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void allAnyAndDisabled(GameTestHelper h) {
        setup(h);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        NbtRule yes = rule(Op.EQ, "0", "minecraft:damage"), no = rule(Op.GT, "5", "minecraft:damage");
        GameTests.check(h, !(NbtRule.test(sword, List.of(yes, no), false, regs)), "!(NbtRule.test(sword, List.of(yes, no), false, regs))");
        GameTests.check(h, NbtRule.test(sword, List.of(yes, no), true, regs), "NbtRule.test(sword, List.of(yes, no), true, regs)");
        GameTests.check(h, NbtRule.test(sword, List.of(yes, no.toggled()), false, regs), "a switched-off rule is ignored");
        GameTests.check(h, NbtRule.test(sword, List.of(no.toggled()), true, regs), "no enabled rule lets everything through");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void wiredIntoTheFilterAndSaved(GameTestHelper h) {
        setup(h);
        ItemStack fresh = new ItemStack(Items.IRON_PICKAXE), worn = fresh.copy();
        worn.set(DataComponents.DAMAGE, 200);
        FilterSpec spec = new FilterSpec(List.of(), MATCH_NBT, List.of(), List.of(), List.of(rule(Op.LT, "50", "minecraft:damage")));
        GameTests.check(h, spec.test(fresh), "spec.test(fresh)");
        GameTests.check(h, !(spec.test(worn)), "!(spec.test(worn))");
        GameTests.check(h, spec.withConfig(MATCH_NBT | INVERT, List.of(), List.of(), spec.rules()).test(worn), "spec.withConfig(MATCH_NBT | INVERT, List.of(), List.of(), spec.rules()).test(worn)");

        var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, regs);
        spec.save(out);
        FilterSpec back = FilterSpec.load(net.minecraft.world.level.storage.TagValueInput.create(
            net.minecraft.util.ProblemReporter.DISCARDING, regs, out.buildResult()));
        GameTests.check(h, java.util.Objects.equals(spec.rules(), back.rules()), "java.util.Objects.equals(spec.rules(), back.rules())");
        GameTests.check(h, java.util.Objects.equals(spec.rules(), NbtRule.CODEC.listOf().parse(NbtOps.INSTANCE,
            NbtRule.CODEC.listOf().encodeStart(NbtOps.INSTANCE, spec.rules()).getOrThrow()).getOrThrow()), "java.util.Objects.equals(spec.rules(), NbtRule.CODEC.listOf().parse(NbtOps.INSTANCE, NbtRule.CODEC.listOf().encodeStart(NbtOps.INSTANCE, spec.rules()).getOrThro");
        h.succeed();
    }

    @GameTests.OmniTest(timeoutTicks = 20)
    public static void cleanCapsWhatAClientSends(GameTestHelper h) {
        setup(h);
        List<NbtRule> many = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) many.add(rule(Op.EQ, "x".repeat(1000), "minecraft:damage"));
        many.add(new NbtRule(List.of(), Op.EQ, "1", true));
        List<NbtRule> clean = NbtRule.clean(many);
        GameTests.check(h, java.util.Objects.equals(NbtRule.MAX_RULES, clean.size()), "java.util.Objects.equals(NbtRule.MAX_RULES, clean.size())");
        GameTests.check(h, clean.stream().allMatch(r -> r.value().length() <= NbtRule.MAX_VALUE && !r.path().isEmpty()), "clean.stream().allMatch(r -> r.value().length() <= NbtRule.MAX_VALUE && !r.path().isEmpty())");
        h.succeed();
    }
}
