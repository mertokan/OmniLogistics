package com.mertokan.omnilogistics.pipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mertokan.omnilogistics.core.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * One block class for every conduit (type x tier). One {@link Connection} per side drives the model:
 * no arm when the side is OFF or nothing is there, a plain arm for a connected NORMAL side,
 * green PULL / orange PUSH arms for configured sides.
 */
public class SmartPipeBlock extends MachineBlock {
    public enum Connection implements StringRepresentable {
        NONE, PLAIN, PULL, PUSH;
        @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final Map<Direction, EnumProperty<Connection>> PROP = new EnumMap<>(Direction.class);
    static {
        for (Direction d : Direction.values()) PROP.put(d, EnumProperty.create(d.getName(), Connection.class));
    }

    public static final MapCodec<SmartPipeBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        propertiesCodec(),
        PipeType.CODEC.fieldOf("pipe_type").forGetter(b -> b.type),
        PipeTier.CODEC.fieldOf("tier").forGetter(b -> b.tier)
    ).apply(i, SmartPipeBlock::new));

    private static final VoxelShape CENTER = Block.box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape[] ARM = {
        Block.box(5, 0, 5, 11, 5, 11),   // down
        Block.box(5, 11, 5, 11, 16, 11), // up
        Block.box(5, 5, 0, 11, 11, 5),   // north
        Block.box(5, 5, 11, 11, 11, 16), // south
        Block.box(0, 5, 5, 5, 11, 11),   // west
        Block.box(11, 5, 5, 16, 11, 11), // east
    };
    private static final VoxelShape[] PLATE = {
        Block.box(4, 0, 4, 12, 2, 12),   // down
        Block.box(4, 14, 4, 12, 16, 12), // up
        Block.box(4, 4, 0, 12, 12, 2),   // north
        Block.box(4, 4, 14, 12, 12, 16), // south
        Block.box(0, 4, 4, 2, 12, 12),   // west
        Block.box(14, 4, 4, 16, 12, 12), // east
    };
    private static final Map<BlockState, VoxelShape> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Which arm was clicked: the dominant axis of the hit point; null when the centre cube was hit. */
    public static @org.jetbrains.annotations.Nullable Direction faceFor(BlockHitResult hit, BlockPos pos) {
        Vec3 v = hit.getLocation().subtract(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        double ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        double max = Math.max(ax, Math.max(ay, az));
        if (max <= 3.0 / 16 + 1e-4) return null;
        if (max == ax) return v.x > 0 ? Direction.EAST : Direction.WEST;
        if (max == ay) return v.y > 0 ? Direction.UP : Direction.DOWN;
        return v.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    public final PipeType type;
    public final PipeTier tier;

    public SmartPipeBlock(Properties props, PipeType type, PipeTier tier) {
        super(props);
        this.type = type;
        this.tier = tier;
        BlockState s = getStateDefinition().any();
        for (Direction d : Direction.values()) s = s.setValue(PROP.get(d), Connection.NONE);
        registerDefaultState(s);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        for (Direction d : Direction.values()) b.add(PROP.get(d));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.newBlockEntity(pos, state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return compute(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @org.jetbrains.annotations.Nullable net.minecraft.world.level.redstone.Orientation orientation, boolean moving) {
        update(level, pos);
    }

    /** Recompute this conduit and every adjacent conduit (their arms depend on our modes). */
    public static void refresh(Level level, BlockPos pos) {
        update(level, pos);
        for (Direction d : Direction.values()) update(level, pos.relative(d));
    }

    private static void update(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof SmartPipeBlock)) return;
        BlockState next = compute(s, level, pos);
        if (next != s) level.setBlock(pos, next, Block.UPDATE_CLIENTS);
    }

    private static BlockState compute(BlockState state, Level level, BlockPos pos) {
        SmartPipeBlock block = (SmartPipeBlock) state.getBlock();
        ConduitBlockEntity self = level.getBlockEntity(pos) instanceof ConduitBlockEntity c ? c : null;
        for (Direction d : Direction.values()) {
            ConduitBlockEntity.Mode mine = self == null ? ConduitBlockEntity.Mode.NORMAL : self.mode(d);
            BlockPos n = pos.relative(d);
            Connection c = Connection.NONE;
            if (mine != ConduitBlockEntity.Mode.NONE) {
                if (level.getBlockEntity(n) instanceof ConduitBlockEntity other) {
                    if (other.type() == block.type && other.mode(d.getOpposite()) != ConduitBlockEntity.Mode.NONE) c = own(mine);
                } else if (block.type.hasCapability(level, n, d.getOpposite())) {
                    c = own(mine);
                }
            }
            state = state.setValue(PROP.get(d), c);
        }
        return state;
    }

    private static Connection own(ConduitBlockEntity.Mode mine) {
        return switch (mine) {
            case PULL -> Connection.PULL;
            case PUSH -> Connection.PUSH;
            default -> Connection.PLAIN;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.computeIfAbsent(state, st -> {
            VoxelShape s = CENTER;
            for (Direction d : Direction.values()) {
                Connection c = st.getValue(PROP.get(d));
                if (c == Connection.NONE) continue;
                s = Shapes.or(s, ARM[d.ordinal()]);
                if (c != Connection.PLAIN) s = Shapes.or(s, PLATE[d.ordinal()]);
            }
            return s;
        });
    }
}
