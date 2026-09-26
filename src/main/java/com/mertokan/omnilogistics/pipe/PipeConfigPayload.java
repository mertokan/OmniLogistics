package com.mertokan.omnilogistics.pipe;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: "cycle entry {@code index} of the conduit config" (0..5 = side modes, 6 = redstone mode, see
 * {@link ConduitBlockEntity#config()}). An intent rather than a snapshot, so quick or concurrent clicks never revert each other.
 */
public record PipeConfigPayload(BlockPos pos, int index) implements CustomPacketPayload {
    public static final Type<PipeConfigPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "pipe_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PipeConfigPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, PipeConfigPayload::pos,
        ByteBufCodecs.VAR_INT, PipeConfigPayload::index,
        PipeConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server main thread. Trust boundary: only the conduit whose GUI this player has open (opened through the event-gated right-click). */
    public static void handle(PipeConfigPayload msg, IPayloadContext ctx) {
        if (!(ctx.player().containerMenu instanceof PipeMenu m) || !m.pos.equals(msg.pos()) || !ctx.player().isWithinBlockInteractionRange(msg.pos(), 4.0)) return;
        if (msg.index() < 0 || msg.index() >= ConduitBlockEntity.CONFIG_LEN) return;
        if (ctx.player().level().getBlockEntity(msg.pos()) instanceof ConduitBlockEntity c) c.cycle(msg.index());
    }
}
