package com.mertokan.omnilogistics.api;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;
import java.util.Objects;

/**
 * Unified DataComponent filter. One reference stack + a flag bitmask (+ a chosen tag, chosen component keys).
 * Every filter (pipe, exposer, router card) calls {@link #test(ItemStack, FilterSpec)}.
 *
 * The flags cover the common cases in one click each; anything finer - a durability range, an enchantment level, a
 * value deep in a mod's custom data - is an {@link NbtRule}, applied on top when MATCH_NBT is set.
 */
public final class ComponentPredicateEngine {
    private ComponentPredicateEngine() {}

    /** Item id must equal the reference item. */
    public static final int MATCH_ITEM       = 1;
    /** Whole component patch must equal the reference (see IGNORE_DAMAGE). */
    public static final int MATCH_COMPONENTS = 1 << 1;
    /** Stack carries ENCHANTMENTS or STORED_ENCHANTMENTS. Works with an empty reference. */
    public static final int HAS_ENCHANTS     = 1 << 2;
    /** Stack carries any non-"minecraft" component (Apotheosis affixes/gems, etc). Works with an empty reference. */
    public static final int HAS_MOD_COMPONENTS = 1 << 3;
    /** Drop DAMAGE before comparing components. */
    public static final int IGNORE_DAMAGE    = 1 << 4;
    /** Whitelist -> blacklist. */
    public static final int INVERT           = 1 << 5;
    /** Stack must carry every enchantment of the reference (book or gear) at >= that level. */
    public static final int MATCH_ENCHANTS   = 1 << 6;
    /** Stack must be in ANY of the item tags chosen from the reference's tags. */
    public static final int MATCH_TAG        = 1 << 7;
    /** Only the chosen component keys must equal the reference's values. */
    public static final int MATCH_SELECTED   = 1 << 8;
    /** The NBT rules apply (set by the GUI whenever there is at least one). */
    public static final int MATCH_NBT        = 1 << 9;
    /** NBT rules: one holding rule is enough, instead of all of them. */
    public static final int NBT_ANY          = 1 << 10;

    public static final int DEFAULT = MATCH_ITEM;

    public static boolean test(ItemStack stack, FilterSpec spec) {
        if (stack.isEmpty()) return false;
        int flags = spec.flags();
        boolean r = true;
        if (has(flags, MATCH_TAG) && !spec.tags().isEmpty())
            r &= inAnyTag(stack, spec.tags());
        if (r && !spec.refs().isEmpty() && has(flags, MATCH_ITEM | MATCH_COMPONENTS | MATCH_SELECTED | MATCH_ENCHANTS))
            r &= anyRef(stack, spec, flags);
        if (r && has(flags, HAS_ENCHANTS))
            r &= hasEnchantments(stack);
        if (r && has(flags, HAS_MOD_COMPONENTS))
            r &= hasModComponents(stack);
        if (r && has(flags, MATCH_NBT) && !spec.rules().isEmpty())
            r &= NbtRule.test(stack, spec.rules(), has(flags, NBT_ANY), registries());
        return r ^ has(flags, INVERT);
    }

    /** The reference-dependent flags, applied to every reference in turn: one match is enough (that is what a
     *  4 / 16 / 64 slot card is for). With a single reference this is exactly the old conjunction. */
    private static boolean anyRef(ItemStack stack, FilterSpec spec, int flags) {
        for (ItemStack ref : spec.refs()) {
            if (ref.isEmpty()) continue;
            boolean r = true;
            if (has(flags, MATCH_ITEM))
                r &= ItemStack.isSameItem(stack, ref);
            if (r && has(flags, MATCH_COMPONENTS))
                r &= patch(stack, flags).equals(patch(ref, flags));
            if (r && has(flags, MATCH_SELECTED))
                r &= selectedEqual(stack, ref, spec.components(), flags);
            if (r && has(flags, MATCH_ENCHANTS))
                r &= matchesEnchantments(stack, ref);
            if (r) return true;
        }
        return false;
    }

    /** Convenience for the simple cases (tests, extractor). */
    public static boolean test(ItemStack stack, ItemStack reference, int flags) {
        return test(stack, new FilterSpec(reference.isEmpty() ? List.of() : List.of(reference), flags, List.of(), List.of()));
    }

    /** The server's registries when there is a server in this process; enchantments and other holders need them to
     *  serialize. A pure client has none, and only ever tests filters for display. */
    private static net.minecraft.core.HolderLookup.@org.jetbrains.annotations.Nullable Provider registries() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.registryAccess();
    }

    public static boolean inAnyTag(ItemStack stack, List<String> tags) {
        for (String tag : tags) {
            Identifier id = Identifier.tryParse(tag);
            if (id != null && stack.is(TagKey.create(Registries.ITEM, id))) return true;
        }
        return false;
    }

    /** Each chosen component key: stack value must equal reference value (both absent counts as equal). */
    public static boolean selectedEqual(ItemStack stack, ItemStack ref, List<String> keys, int flags) {
        for (String key : keys) {
            Identifier id = Identifier.tryParse(key);
            DataComponentType<?> type = id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
            if (type == null || (has(flags, IGNORE_DAMAGE) && type == DataComponents.DAMAGE)) continue;
            if (!Objects.equals(stack.get(type), ref.get(type))) return false;
        }
        return true;
    }

    public static boolean hasEnchantments(ItemStack stack) {
        return !enchantmentsOf(stack).isEmpty();
    }

    /** Gear enchantments, or stored (book) enchantments when the stack has none of its own. */
    public static ItemEnchantments enchantmentsOf(ItemStack stack) {
        ItemEnchantments own = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        return own.isEmpty() ? stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY) : own;
    }

    /** Every enchantment on the reference must be on the stack at the same level or higher. */
    public static boolean matchesEnchantments(ItemStack stack, ItemStack reference) {
        ItemEnchantments want = enchantmentsOf(reference);
        ItemEnchantments have = enchantmentsOf(stack);
        for (var e : want.entrySet())
            if (have.getLevel(e.getKey()) < e.getIntValue()) return false;
        return true;
    }

    /** Patch-based on purpose: a modded item's *default* components don't count, only what a mod stamped on. */
    public static boolean hasModComponents(ItemStack stack) {
        for (var e : stack.getComponentsPatch().entrySet()) {
            if (e.getValue().isEmpty()) continue; // removal marker
            if (isModded(e.getKey())) return true;
        }
        return false;
    }

    public static boolean isModded(DataComponentType<?> type) {
        Identifier key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        return key != null && !Identifier.DEFAULT_NAMESPACE.equals(key.getNamespace());
    }

    /** One row of the component picker: registry id, value preview, and whether the stack changed it from the item default. */
    public record ComponentInfo(String key, String value, boolean patched) {}

    /** Every data component on the stack (item defaults + stamped-on ones), sorted, patched ones first. What the GUI picker lists. */
    public static List<ComponentInfo> componentInfos(ItemStack stack) {
        var patch = stack.getComponentsPatch();
        List<ComponentInfo> out = new java.util.ArrayList<>();
        for (var typed : stack.getComponents()) {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type());
            if (id == null) continue;
            boolean patched = patch.getPatch(typed.type()) != null;
            out.add(new ComponentInfo(id.toString(), String.valueOf(typed.value()), patched));
        }
        out.sort((a, b) -> a.patched != b.patched ? (a.patched ? -1 : 1) : a.key.compareTo(b.key));
        return out;
    }

    public static List<String> componentKeys(ItemStack stack) {
        return componentInfos(stack).stream().map(ComponentInfo::key).toList();
    }

    /** Item tags the stack is in, sorted. What the GUI picker lists. */
    public static List<String> tagKeys(ItemStack stack) {
        return stack.typeHolder().tags().map(TagKey::location).map(Identifier::toString).sorted().toList();
    }

    private static DataComponentPatch patch(ItemStack stack, int flags) {
        DataComponentPatch p = stack.getComponentsPatch();
        return has(flags, IGNORE_DAMAGE) ? p.forget(t -> t == DataComponents.DAMAGE) : p;
    }

    private static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
