package com.mertokan.omnilogistics.extractor;

import com.mojang.serialization.Codec;
import com.mertokan.omnilogistics.api.ComponentPredicateEngine;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure logic for the Component Extractor.
 * EXTRACT: enchantments -> enchanted book; with the Component Module also any item stacks embedded inside
 * modded components (Apotheosis socketed gems and the like) -> loose items, the component removed once emptied.
 * FUSE (Fusion Module): donor enchantments merge onto the gear (anvil rules: equal level bumps by one up to max),
 * and every modded component the gear lacks is copied from the donor.
 *
 * ponytail: generic NBT walk instead of per-mod APIs. Works when a mod stores the gem as a real ItemStack;
 * a mod that stores only an id needs its own compat class.
 */
public final class ComponentExtractor {
    private ComponentExtractor() {}

    public record Result(ItemStack cleaned, List<ItemStack> extracted, boolean usedBook) {}
    public record Fusion(ItemStack fused, boolean changed) {}

    public static Result extract(ItemStack gear, boolean bookAvailable, boolean moddedComponents, HolderLookup.Provider regs) {
        ItemStack out = gear.copy();
        List<ItemStack> extras = new ArrayList<>();
        boolean usedBook = false;

        ItemEnchantments ench = out.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (bookAvailable && !ench.isEmpty()) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.set(DataComponents.STORED_ENCHANTMENTS, ench);
            extras.add(book);
            out.remove(DataComponents.ENCHANTMENTS);
            usedBook = true;
        }

        if (moddedComponents) {
            for (var e : gear.getComponentsPatch().entrySet()) {
                if (e.getValue().isEmpty() || !ComponentPredicateEngine.isModded(e.getKey())) continue;
                List<ItemStack> found = embeddedStacks(e.getKey(), e.getValue().get(), regs);
                if (!found.isEmpty()) {
                    extras.addAll(found);
                    out.remove(e.getKey());
                }
            }
        }
        return new Result(out, extras, usedBook);
    }

    public static Fusion fuse(ItemStack gear, ItemStack donor) {
        ItemStack out = gear.copy();
        boolean changed = false;

        ItemEnchantments give = ComponentPredicateEngine.enchantmentsOf(donor);
        if (!give.isEmpty()) {
            ItemEnchantments.Mutable m = new ItemEnchantments.Mutable(out.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
            for (var e : give.entrySet()) {
                int cur = m.getLevel(e.getKey()), lvl = e.getIntValue();
                int next = cur == lvl ? Math.min(lvl + 1, e.getKey().value().getMaxLevel()) : Math.max(cur, lvl);
                if (next != cur) { m.set(e.getKey(), next); changed = true; }
            }
            if (changed) out.set(DataComponents.ENCHANTMENTS, m.toImmutable());
        }
        for (var e : donor.getComponentsPatch().entrySet()) {
            if (e.getValue().isEmpty() || !ComponentPredicateEngine.isModded(e.getKey())) continue;
            if (out.get(e.getKey()) == null) {
                setRaw(out, e.getKey(), e.getValue().get());
                changed = true;
            }
        }
        return new Fusion(out, changed);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setRaw(ItemStack stack, DataComponentType<T> type, Object value) {
        stack.set(type, (T) value);
    }

    @SuppressWarnings("unchecked")
    static <T> List<ItemStack> embeddedStacks(DataComponentType<T> type, Object value, HolderLookup.Provider regs) {
        List<ItemStack> list = new ArrayList<>();
        Codec<T> codec = type.codec();
        if (codec == null) return list;
        codec.encodeStart(regs.createSerializationContext(NbtOps.INSTANCE), (T) value)
            .result().ifPresent(tag -> walk(tag, list, regs));
        return list;
    }

    private static void walk(Tag tag, List<ItemStack> out, HolderLookup.Provider regs) {
        if (tag instanceof CompoundTag c) {
            if (c.contains("id", Tag.TAG_STRING) && c.contains("count", Tag.TAG_INT)) {
                ItemStack.parse(regs, c).filter(s -> !s.isEmpty()).ifPresent(out::add);
                return;
            }
            for (String k : c.getAllKeys()) walk(c.get(k), out, regs);
        } else if (tag instanceof ListTag l) {
            for (Tag t : l) walk(t, out, regs);
        }
    }
}
