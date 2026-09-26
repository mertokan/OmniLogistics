package com.mertokan.omnilogistics.api;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything a filter needs: reference stacks, flag bitmask, chosen item tags, chosen component keys, NBT rules.
 * Immutable; hosts replace the whole record. A stack passes when it matches ANY reference (the tag / enchant / mod
 * flags still apply on top), so one card can whitelist up to its item's capacity - 1, 4, 16 or 64 references.
 * The NBT rules are one more condition on top of that, and they need no reference at all.
 */
public record FilterSpec(List<ItemStack> refs, int flags, List<String> tags, List<String> components, List<NbtRule> rules) {
    public static final int MAX_COMPONENTS = 16, MAX_TAGS = 16, MAX_REFS = 64;
    public static final FilterSpec EMPTY = new FilterSpec(List.of(), ComponentPredicateEngine.DEFAULT, List.of(), List.of());

    public FilterSpec(List<ItemStack> refs, int flags, List<String> tags, List<String> components) {
        this(refs, flags, tags, components, List.of());
    }

    /**
     * The same filter, asked about a fluid: a reference container (a water bucket, a tank item) names its fluid, and a
     * tag entry may name a fluid tag. No references and no tags = everything passes, exactly like the item side.
     */
    public boolean testFluid(net.neoforged.neoforge.fluids.FluidStack fluid) {
        if (fluid.isEmpty()) return false;
        boolean invert = (flags & ComponentPredicateEngine.INVERT) != 0;
        if (refs.isEmpty() && tags.isEmpty()) return true;
        boolean hit = false;
        for (ItemStack ref : refs) {
            net.neoforged.neoforge.fluids.FluidStack in = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(ref)
                .orElse(net.neoforged.neoforge.fluids.FluidStack.EMPTY);
            if (!in.isEmpty() && net.neoforged.neoforge.fluids.FluidStack.isSameFluid(in, fluid)) { hit = true; break; }
        }
        if (!hit) for (String tag : tags) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(tag);
            if (id != null && fluid.getFluid().builtInRegistryHolder()
                .is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, id))) { hit = true; break; }
        }
        return invert != hit;
    }

    public boolean test(ItemStack stack) {
        return ComponentPredicateEngine.test(stack, this);
    }

    /** The first reference: what the tag / component pickers and the tooltips read. */
    public ItemStack ref() {
        return refs.isEmpty() ? ItemStack.EMPTY : refs.get(0);
    }

    public ItemStack ref(int i) {
        return i >= 0 && i < refs.size() ? refs.get(i) : ItemStack.EMPTY;
    }

    public FilterSpec withRef(ItemStack stack) {
        return withRef(0, stack);
    }

    /** Set (or clear, with an empty stack) one reference slot; trailing empties are trimmed off. */
    public FilterSpec withRef(int index, ItemStack stack) {
        if (index < 0 || index >= MAX_REFS) return this;
        List<ItemStack> out = new ArrayList<>(refs);
        while (out.size() <= index) out.add(ItemStack.EMPTY);
        out.set(index, stack.copyWithCount(1));
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return new FilterSpec(List.copyOf(out), flags, tags, components, rules);
    }

    /** Server-side sanitising of client input. */
    public FilterSpec withConfig(int newFlags, List<String> newTags, List<String> newComponents, List<NbtRule> newRules) {
        return new FilterSpec(refs, newFlags, clean(newTags, MAX_TAGS), clean(newComponents, MAX_COMPONENTS), NbtRule.clean(newRules));
    }

    private static List<String> clean(List<String> in, int max) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (out.size() < max && s.length() <= 128 && !out.contains(s)) out.add(s);
        return List.copyOf(out);
    }

    public void save(net.minecraft.world.level.storage.ValueOutput out) {
        if (!refs.isEmpty()) out.store("Filters", ItemStack.OPTIONAL_CODEC.listOf(), refs);
        out.putInt("Flags", flags);
        out.store("Tags", com.mojang.serialization.Codec.STRING.listOf(), tags);
        out.store("Components", com.mojang.serialization.Codec.STRING.listOf(), components);
        if (!rules.isEmpty()) out.store("Nbt", NbtRule.CODEC.listOf(), rules);
    }

    public static FilterSpec load(net.minecraft.world.level.storage.ValueInput in) {
        var strings = com.mojang.serialization.Codec.STRING.listOf();
        return new FilterSpec(List.copyOf(in.read("Filters", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of())),
            in.getIntOr("Flags", 0), in.read("Tags", strings).orElse(List.of()), in.read("Components", strings).orElse(List.of()),
            NbtRule.clean(in.read("Nbt", NbtRule.CODEC.listOf()).orElse(List.of())));
    }
}
