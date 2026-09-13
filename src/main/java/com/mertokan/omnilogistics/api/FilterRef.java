package com.mertokan.omnilogistics.api;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The filter's reference stacks as one data component value. NeoForge rejects component values without equals /
 * hashCode and a bare ItemStack has neither, so they travel in this record. Treat the stacks as read-only.
 * The codec still reads a single stack, which is what cards written before multi-reference filters carry.
 */
public record FilterRef(List<ItemStack> stacks) {
    public static final FilterRef EMPTY = new FilterRef(List.of());
    public static final Codec<FilterRef> CODEC = Codec.either(ItemStack.OPTIONAL_CODEC.listOf(), ItemStack.OPTIONAL_CODEC)
        .xmap(e -> new FilterRef(e.map(l -> l, List::of)), f -> com.mojang.datafixers.util.Either.left(f.stacks()));
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterRef> STREAM_CODEC =
        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(FilterSpec.MAX_REFS)).map(FilterRef::new, FilterRef::stacks);

    public static FilterRef of(List<ItemStack> stacks) {
        return new FilterRef(stacks.stream().map(ItemStack::copy).toList());
    }

    public static FilterRef of(ItemStack stack) {
        return stack.isEmpty() ? EMPTY : new FilterRef(List.of(stack.copy()));
    }

    /** The first reference, for the callers that only ever look at one. */
    public ItemStack stack() {
        return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FilterRef f) || f.stacks.size() != stacks.size()) return false;
        for (int i = 0; i < stacks.size(); i++) if (!ItemStack.matches(stacks.get(i), f.stacks.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (ItemStack s : stacks) h = h * 31 + ItemStack.hashItemAndComponents(s) * 31 + s.getCount();
        return h;
    }
}
