package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.core.MenuHost;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import com.mertokan.omnilogistics.core.Wrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Shared conduit behaviour (Mekanism semantics):
 * NORMAL = connected, accepts input, receives deliveries; PULL = also extracts from that neighbour;
 * PUSH = output only (refuses input); NONE = disconnected.
 * Every interval: PULL sides extract into the buffer, then the buffer is delivered round-robin to
 * PUSH/NORMAL sides. Conduit-to-conduit delivery skips the side we last received from, so a chain
 * flows forward and never ping-pongs. A per-conduit redstone mode gates the whole conduit.
 * Only the ends of a run ({@link #isEnd()}) open the {@link PipeMenu}; a conduit in the middle is a plain cable.
 */
public abstract class ConduitBlockEntity extends TickingBlockEntity implements Wrenchable, MenuHost, com.mertokan.omnilogistics.core.InfoLines {
    public enum Mode { NORMAL, PULL, PUSH, NONE }
    public enum Redstone { ALWAYS, ON_SIGNAL, WITHOUT_SIGNAL }
    /** {@link #config()} length: 6 side modes + redstone mode. */
    public static final int CONFIG_LEN = 7;

    protected final Mode[] sides = new Mode[6];
    protected Redstone redstone = Redstone.ALWAYS;
    protected @Nullable Direction lastFrom;
    private int rr;
    private boolean connectionsChecked;
    // the glass tube shows the contents (ConduitRenderer), so buffer changes are pushed to clients, throttled
    private boolean dirty;
    private long lastSync;
    private boolean lastEmpty = true;

    protected ConduitBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(sides, Mode.NORMAL);
    }

    public PipeType type() { return ((SmartPipeBlock) getBlockState().getBlock()).type; }
    public PipeTier tier() { return ((SmartPipeBlock) getBlockState().getBlock()).tier; }
    public Mode mode(Direction d) { return sides[d.ordinal()]; }
    public Redstone redstone() { return redstone; }
    public @Nullable Direction lastFrom() { return lastFrom; }

    /** Buffer changed: clients get it on the next tick (items) or within {@link #syncInterval()} ticks (energy / fluid). */
    protected void markDirty() {
        dirty = true;
        setChanged();
    }

    protected int syncInterval() { return 10; }

    /** May a neighbour on this side insert into us? */
    public boolean accepts(@Nullable Direction side) {
        return side == null || mode(side) == Mode.NORMAL || mode(side) == Mode.PULL;
    }

    /** Has at least one IN or OUT face, i.e. is a source or a sink rather than a pass-through. */
    public boolean hasEndpoint() {
        for (Mode m : sides) if (m == Mode.PULL || m == Mode.PUSH) return true;
        return false;
    }

    /** Something to configure here: touches a non-conduit block that speaks our capability, or already has an IN / OUT face (item pipes: or holds a card). */
    public boolean isEnd() {
        if (hasEndpoint()) return true;
        for (Direction d : Direction.values()) {
            BlockPos n = worldPosition.relative(d);
            if (!(level.getBlockEntity(n) instanceof ConduitBlockEntity) && type().hasCapability(level, n, d.getOpposite())) return true;
        }
        return false;
    }

    public boolean redstoneAllows() {
        return switch (redstone) {
            case ALWAYS -> true;
            case ON_SIGNAL -> level.hasNeighborSignal(worldPosition);
            case WITHOUT_SIGNAL -> !level.hasNeighborSignal(worldPosition);
        };
    }

    protected int interval() { return tier().interval; }

    /** Try to extract from the neighbour on this side into the buffer. */
    protected abstract boolean pull(Direction from);
    /** Try to deliver from the buffer to the neighbour on this side. */
    protected abstract boolean push(Direction to);
    protected abstract boolean bufferEmpty();
    /** One line for Jade / tooltips describing the buffer. */
    public abstract Component bufferInfo();

    @Override
    public void serverTick() {
        if (!connectionsChecked) { // placement by setBlock / chunk load never ran getStateForPlacement: self-heal once
            connectionsChecked = true;
            SmartPipeBlock.refresh(level, worldPosition);
        }
        if (dirty && (bufferEmpty() != lastEmpty || level.getGameTime() - lastSync >= syncInterval())) {
            dirty = false;
            lastSync = level.getGameTime();
            lastEmpty = bufferEmpty();
            sync();
        }
        if (!redstoneAllows()) return;
        boolean phase = level.getGameTime() % interval() == 0;
        if (phase)
            for (Direction d : Direction.values())
                if (sides[d.ordinal()] == Mode.PULL && bufferEmptyOrPartial() && pull(d)) break;
        if (bufferEmpty() || !readyToPush(phase)) return;
        for (int k = 0; k < 6; k++) {
            Direction d = Direction.values()[(rr + k) % 6];
            Mode m = sides[d.ordinal()];
            if (m == Mode.NONE || m == Mode.PULL) continue;
            boolean conduit = level.getBlockEntity(worldPosition.relative(d)) instanceof ConduitBlockEntity;
            if (conduit && d == lastFrom) continue;
            if (push(d)) { rr = (rr + k + 1) % 6; return; }
        }
    }

    /** Items pull only when empty; energy/fluid keep topping up. */
    protected boolean bufferEmptyOrPartial() { return bufferEmpty(); }

    /** Energy / fluid push on the tier phase; item pipes hold each stack for one interval first (see PipeBlockEntity). */
    protected boolean readyToPush(boolean phase) { return phase; }

    @Override
    public java.util.List<Component> infoLines() {
        net.minecraft.network.chat.MutableComponent faces = Component.empty();
        boolean any = false;
        for (Direction d : Direction.values()) {
            Mode m = sides[d.ordinal()];
            if (m == Mode.NORMAL) continue;
            if (any) faces.append(Component.literal("  "));
            any = true;
            faces.append(Component.literal(Character.toUpperCase(d.getName().charAt(0)) + ":")).append(modeName(m));
        }
        java.util.List<Component> out = new java.util.ArrayList<>();
        out.add(Component.translatable("jade.omnilogistics.sides", any ? faces : Component.translatable("jade.omnilogistics.all_normal")));
        if (redstone != Redstone.ALWAYS) out.add(Component.translatable("jade.omnilogistics.redstone", redstoneName(redstone)));
        out.add(bufferInfo());
        return out;
    }

    // ---- config ---------------------------------------------------------------------

    public byte[] modes() {
        byte[] b = new byte[6];
        for (int i = 0; i < 6; i++) b[i] = (byte) sides[i].ordinal();
        return b;
    }

    public void setModes(byte[] modes) {
        for (int i = 0; i < 6; i++) sides[i] = Mode.values()[Math.floorMod(modes[i], Mode.values().length)];
        modesChanged();
    }

    public void setRedstone(Redstone r) {
        redstone = r;
        sync();
    }

    /** What the GUI edits: 6 side modes followed by the redstone mode. */
    public byte[] config() {
        byte[] b = Arrays.copyOf(modes(), CONFIG_LEN);
        b[6] = (byte) redstone.ordinal();
        return b;
    }

    /** Server side, from the GUI: cycle one entry of {@link #config()} (index already range-checked). */
    public void cycle(int index) {
        if (index == 6) cycleRedstone();
        else cycleSide(Direction.values()[index]);
    }

    /** NORMAL -> PULL -> PUSH -> NONE -> ... for one face. */
    public Mode cycleSide(Direction face) {
        Mode m = Mode.values()[(sides[face.ordinal()].ordinal() + 1) % Mode.values().length];
        sides[face.ordinal()] = m;
        modesChanged();
        return m;
    }

    // ---- MenuHost ---------------------------------------------------------------------

    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new PipeMenu(id, inv, this); }
    @Override public void writeMenuData(RegistryFriendlyByteBuf buf) { PipeMenu.writeData(buf, this); }

    protected void modesChanged() {
        sync();
        if (level != null && !level.isClientSide()) {
            invalidateCapabilities();
            SmartPipeBlock.refresh(level, worldPosition);
        }
    }

    @Override
    public Component wrenchFace(Direction face, Player player) {
        return Component.translatable("msg.omnilogistics.side_mode", face.getName(), modeName(cycleSide(face)));
    }

    /** Wrench on the conduit centre (or the GUI button): cycle the redstone mode. */
    public Component cycleRedstone() {
        setRedstone(Redstone.values()[(redstone.ordinal() + 1) % Redstone.values().length]);
        return Component.translatable("msg.omnilogistics.redstone_mode", redstoneName(redstone));
    }

    public static Component modeName(Mode m) {
        return Component.translatable("gui.omnilogistics.pipe_mode." + m.name().toLowerCase(Locale.ROOT));
    }

    public static Component redstoneName(Redstone r) {
        return Component.translatable("gui.omnilogistics.redstone." + r.name().toLowerCase(Locale.ROOT));
    }

    // ---- NBT ------------------------------------------------------------------------

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
        super.saveAdditional(tag);
        int[] m = new int[6];
        for (int i = 0; i < 6; i++) m[i] = sides[i].ordinal();
        tag.putIntArray("Sides", m);
        tag.putInt("Redstone", redstone.ordinal());
        if (lastFrom != null) tag.putInt("LastFrom", lastFrom.ordinal());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
        super.loadAdditional(tag);
        int[] b = tag.getIntArray("Sides").orElse(new int[0]);
        for (int i = 0; i < 6; i++) sides[i] = b.length == 6 ? Mode.values()[Math.floorMod(b[i], Mode.values().length)] : Mode.NORMAL;
        redstone = Redstone.values()[Math.floorMod(tag.getIntOr("Redstone", 0), Redstone.values().length)];
        lastFrom = tag.getInt("LastFrom").map(i -> Direction.values()[Math.floorMod(i, 6)]).orElse(null);
    }
}
