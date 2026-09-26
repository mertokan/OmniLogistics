package com.mertokan.omnilogistics.api;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One condition on an item's data: walk {@code path} into the item's components (serialized to NBT, item defaults
 * included, so "damage = 0" holds on a fresh sword) and compare what is there with {@code value}.
 *
 * <p>The path is a list of keys, not a dotted string, because component and registry ids are full of dots and colons.
 * A key {@code [3]} indexes a list; {@code *} means "any element", so {@code minecraft:container / * / item / id}
 * asks whether a shulker box holds something anywhere inside it. The value is SNBT ({@code 5}, {@code 2.5d},
 * {@code "text"}, {@code {a:1b}}); anything that does not parse as SNBT is taken as a plain string.
 */
public record NbtRule(List<String> path, Op op, String value, boolean enabled) {
    public static final int MAX_RULES = 16, MAX_DEPTH = 12, MAX_VALUE = 256, MAX_LEAVES = 400;
    public static final String ANY = "*";

    public enum Op {
        EQ, NE, GT, GE, LT, LE, EXISTS, ABSENT, CONTAINS;

        public boolean takesValue() {
            return this != EXISTS && this != ABSENT;
        }

        public Op next(int dir) {
            return values()[Math.floorMod(ordinal() + dir, values().length)];
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Op parse(String s) {
            for (Op o : values()) if (o.name().equalsIgnoreCase(s)) return o;
            return EQ;
        }
    }

    public static final Codec<NbtRule> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.listOf().fieldOf("path").forGetter(NbtRule::path),
        Codec.STRING.xmap(Op::parse, Op::name).optionalFieldOf("op", Op.EQ).forGetter(NbtRule::op),
        Codec.STRING.optionalFieldOf("value", "").forGetter(NbtRule::value),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(NbtRule::enabled)
    ).apply(i, NbtRule::new));

    public static final StreamCodec<ByteBuf, NbtRule> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(MAX_DEPTH)), NbtRule::path,
        ByteBufCodecs.VAR_INT.map(i -> Op.values()[Math.floorMod(i, Op.values().length)], Op::ordinal), NbtRule::op,
        ByteBufCodecs.stringUtf8(MAX_VALUE), NbtRule::value,
        ByteBufCodecs.BOOL, NbtRule::enabled,
        NbtRule::new);

    public static final StreamCodec<ByteBuf, List<NbtRule>> LIST_STREAM_CODEC =
        STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RULES));

    public NbtRule {
        path = List.copyOf(path);
    }

    public NbtRule with(Op o) { return new NbtRule(path, o, value, enabled); }
    public NbtRule with(String v) { return new NbtRule(path, op, v, enabled); }
    public NbtRule toggled() { return new NbtRule(path, op, value, !enabled); }

    /** Every list index in the path swapped for "any element", or back to the first element. */
    public NbtRule anyIndex() {
        boolean hasIndex = path.stream().anyMatch(NbtRule::isIndex);
        List<String> p = new ArrayList<>(path);
        for (int i = 0; i < p.size(); i++) {
            if (hasIndex && isIndex(p.get(i))) p.set(i, ANY);
            else if (!hasIndex && ANY.equals(p.get(i))) p.set(i, "[0]");
        }
        return new NbtRule(p, op, value, enabled);
    }

    public boolean hasListStep() {
        return path.stream().anyMatch(k -> isIndex(k) || ANY.equals(k));
    }

    /** Server-side sanitising: a client decides these strings. */
    public static List<NbtRule> clean(List<NbtRule> in) {
        List<NbtRule> out = new ArrayList<>();
        for (NbtRule r : in) {
            if (out.size() >= MAX_RULES || r.path.isEmpty() || r.path.size() > MAX_DEPTH) continue;
            if (r.path.stream().anyMatch(k -> k.length() > 512)) continue;
            out.add(r.value.length() > MAX_VALUE ? r.with(r.value.substring(0, MAX_VALUE)) : r);
        }
        return List.copyOf(out);
    }

    // ---- matching ---------------------------------------------------------------------------------------------

    /** ALL: every enabled rule holds. ANY: at least one does. No enabled rule at all lets everything through. */
    public static boolean test(ItemStack stack, List<NbtRule> rules, boolean any, @Nullable HolderLookup.Provider regs) {
        CompoundTag data = null;
        boolean seen = false;
        for (NbtRule r : rules) {
            if (!r.enabled) continue;
            seen = true;
            if (data == null) data = components(stack, regs);   // ponytail: serialized per test; cache per stack if a
            boolean ok = r.test(data);                            // pack runs thousands of rule checks a tick
            if (any && ok) return true;
            if (!any && !ok) return false;
        }
        return !seen || !any;
    }

    public boolean test(CompoundTag data) {
        List<Tag> found = new ArrayList<>();
        collect(data, 0, found);
        if (op == Op.EXISTS) return !found.isEmpty();
        if (op == Op.ABSENT) return found.isEmpty();
        Tag want = parse(value);
        for (Tag t : found) if (compare(t, want)) return true;   // with "*" in the path, one element is enough
        return false;
    }

    private void collect(Tag at, int depth, List<Tag> out) {
        if (depth == path.size()) { out.add(at); return; }
        String key = path.get(depth);
        if (ANY.equals(key)) {
            if (at instanceof CompoundTag c) for (String k : c.keySet()) collect(c.get(k), depth + 1, out);
            else if (at instanceof CollectionTag l) for (Tag t : l) collect(t, depth + 1, out);
        } else if (at instanceof CompoundTag c) {
            Tag next = c.get(key);
            if (next != null) collect(next, depth + 1, out);
        } else if (at instanceof CollectionTag l && isIndex(key)) {
            int i = index(key);
            if (i >= 0 && i < l.size()) collect(l.get(i), depth + 1, out);
        }
    }

    private boolean compare(Tag have, Tag want) {
        return switch (op) {
            case EQ -> same(have, want);
            case NE -> !same(have, want);
            case GT, GE, LT, LE -> {
                if (!(have instanceof NumericTag h) || !(want instanceof NumericTag w)) yield false;
                int c = Double.compare(h.asDouble().orElse(0d), w.asDouble().orElse(0d));
                yield op == Op.GT ? c > 0 : op == Op.GE ? c >= 0 : op == Op.LT ? c < 0 : c <= 0;
            }
            case CONTAINS -> {
                if (have instanceof StringTag s)
                    yield s.value().toLowerCase(Locale.ROOT).contains(text(want).toLowerCase(Locale.ROOT));
                if (have instanceof CollectionTag l) {
                    for (Tag t : l) if (same(t, want)) yield true;
                    yield false;
                }
                yield have instanceof CompoundTag c && c.contains(text(want));
            }
            default -> false;
        };
    }

    /** Numbers compare by value whatever their NBT width (5b, 5, 5.0d are all 5); everything else by content. */
    static boolean same(Tag a, Tag b) {
        if (a instanceof NumericTag x && b instanceof NumericTag y) return Double.compare(x.asDouble().orElse(0d), y.asDouble().orElse(0d)) == 0;
        if (a instanceof StringTag || b instanceof StringTag) return text(a).equals(text(b));
        return a.equals(b);
    }

    private static String text(Tag t) {
        return t instanceof StringTag s ? s.value() : t.toString();
    }

    /** SNBT, or a plain string when it is not SNBT (so {@code diamond} works without quotes). */
    public static Tag parse(String snbt) {
        String s = snbt.trim();
        if (s.isEmpty()) return StringTag.valueOf("");
        try {
            Tag t = TagParser.parseCompoundFully("{v:" + s + "}").get("v");
            return t == null ? StringTag.valueOf(s) : t;
        } catch (CommandSyntaxException e) {
            return StringTag.valueOf(s);
        }
    }

    // ---- the item's data, as NBT ------------------------------------------------------------------------------

    /**
     * Every component on the stack - item defaults too - as one compound keyed by component id. A component without
     * a codec (transient, never saved) is skipped, and so is one that cannot encode without the registries.
     */
    public static CompoundTag components(ItemStack stack, @Nullable HolderLookup.Provider regs) {
        CompoundTag out = new CompoundTag();
        DynamicOps<Tag> ops = regs == null ? NbtOps.INSTANCE : regs.createSerializationContext(NbtOps.INSTANCE);
        for (TypedDataComponent<?> c : stack.getComponents()) encode(out, c, ops);
        return out;
    }

    private static <T> void encode(CompoundTag out, TypedDataComponent<T> c, DynamicOps<Tag> ops) {
        Codec<T> codec = c.type().codec();
        Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
        if (codec == null || id == null) return;
        try {
            codec.encodeStart(ops, c.value()).result().ifPresent(t -> out.put(id.toString(), t));
        } catch (RuntimeException ignored) {   // a holder that needs the registries we did not have
        }
    }

    /** One end point of the data tree, as the path browser lists it: where it is and what it holds. */
    public record Leaf(List<String> path, String value, boolean patched) {}

    /** Every leaf of the stack's data, the components the stack changed from its item's defaults first. */
    public static List<Leaf> leaves(ItemStack stack, @Nullable HolderLookup.Provider regs) {
        CompoundTag data = components(stack, regs);
        var patch = stack.getComponentsPatch();
        List<String> keys = new ArrayList<>(data.keySet());
        keys.sort((a, b) -> {
            boolean pa = patched(patch, a), pb = patched(patch, b);
            return pa != pb ? (pa ? -1 : 1) : a.compareTo(b);
        });
        List<Leaf> out = new ArrayList<>();
        for (String k : keys) walk(data.get(k), new ArrayList<>(List.of(k)), patched(patch, k), out);
        return out;
    }

    private static boolean patched(net.minecraft.core.component.DataComponentPatch patch, String key) {
        Identifier id = Identifier.tryParse(key);
        var type = id == null ? null : BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
        return type != null && patch.getPatch(type) != null;
    }

    private static void walk(Tag t, List<String> path, boolean patched, List<Leaf> out) {
        if (out.size() >= MAX_LEAVES) return;
        if (path.size() < MAX_DEPTH && t instanceof CompoundTag c && !c.isEmpty()) {
            List<String> keys = new ArrayList<>(c.keySet());
            keys.sort(null);
            for (String k : keys) step(c.get(k), path, k, patched, out);
        } else if (path.size() < MAX_DEPTH && t instanceof CollectionTag l && !l.isEmpty()) {
            for (int i = 0; i < l.size(); i++) step(l.get(i), path, "[" + i + "]", patched, out);
        } else {
            out.add(new Leaf(List.copyOf(path), t.toString(), patched));
        }
    }

    private static void step(Tag t, List<String> path, String key, boolean patched, List<Leaf> out) {
        path.add(key);
        walk(t, path, patched, out);
        path.remove(path.size() - 1);
    }

    static boolean isIndex(String key) {
        return key.length() > 2 && key.charAt(0) == '[' && key.charAt(key.length() - 1) == ']' && index(key) >= 0;
    }

    private static int index(String key) {
        try {
            return Integer.parseInt(key.substring(1, key.length() - 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "enchantments › levels › sharpness": namespaces dropped, the way a player reads it. */
    public static String pretty(List<String> path) {
        StringBuilder b = new StringBuilder();
        for (String k : path) {
            if (b.length() > 0) b.append(" › ");
            int colon = k.indexOf(':');
            b.append(colon > 0 && !k.startsWith("[") ? k.substring(colon + 1) : ANY.equals(k) ? "any" : k);
        }
        return b.toString();
    }
}
