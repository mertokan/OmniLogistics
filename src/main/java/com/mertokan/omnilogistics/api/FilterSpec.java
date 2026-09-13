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
 * Everything a filter needs: reference stacks, flag bitmask, chosen item tags, chosen component keys.
 * Immutable; hosts replace the whole record. A stack passes when it matches ANY reference (the tag / enchant / mod
 * flags still apply on top), so one card can whitelist up to its item's capacity - 1, 4, 16 or 64 references.
 */
public record FilterSpec(List<ItemStack> refs, int flags, List<String> tags, List<String> components) {
    public static final int MAX_COMPONENTS = 16, MAX_TAGS = 16, MAX_REFS = 64;
    public static final FilterSpec EMPTY = new FilterSpec(List.of(), ComponentPredicateEngine.DEFAULT, List.of(), List.of());

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
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(tag);
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
        return new FilterSpec(List.copyOf(out), flags, tags, components);
    }

    /** Server-side sanitising of client input. */
    public FilterSpec withConfig(int newFlags, List<String> newTags, List<String> newComponents) {
        return new FilterSpec(refs, newFlags, clean(newTags, MAX_TAGS), clean(newComponents, MAX_COMPONENTS));
    }

    private static List<String> clean(List<String> in, int max) {
        List<String> out = new ArrayList<>();
        for (String s : in) if (out.size() < max && s.length() <= 128 && !out.contains(s)) out.add(s);
        return List.copyOf(out);
    }

    public void save(CompoundTag tag, HolderLookup.Provider regs) {
        if (!refs.isEmpty()) {
            ListTag list = new ListTag();
            for (ItemStack s : refs) list.add(s.isEmpty() ? new CompoundTag() : (CompoundTag) s.save(regs));
            tag.put("Filters", list);
        }
        tag.putInt("Flags", flags);
        tag.put("Tags", strings(tags));
        tag.put("Components", strings(components));
    }

    public static FilterSpec load(CompoundTag tag, HolderLookup.Provider regs) {
        List<ItemStack> refs = new ArrayList<>();
        if (tag.contains("Filters"))
            for (Tag t : tag.getList("Filters", Tag.TAG_COMPOUND)) refs.add(ItemStack.parseOptional(regs, (CompoundTag) t));
        else if (tag.contains("Filter")) refs.add(ItemStack.parseOptional(regs, tag.getCompound("Filter")));   // pre-multi saves
        return new FilterSpec(List.copyOf(refs), tag.getInt("Flags"), strings(tag.getList("Tags", Tag.TAG_STRING)),
            strings(tag.getList("Components", Tag.TAG_STRING)));
    }

    private static ListTag strings(List<String> in) {
        ListTag list = new ListTag();
        for (String s : in) list.add(StringTag.valueOf(s));
        return list;
    }

    private static List<String> strings(ListTag in) {
        List<String> out = new ArrayList<>();
        for (Tag t : in) out.add(t.getAsString());
        return List.copyOf(out);
    }
}
