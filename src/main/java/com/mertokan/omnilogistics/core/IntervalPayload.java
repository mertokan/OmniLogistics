package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: run the machine at this tick every N ticks. One packet for every {@link IntervalHost}. */
public record IntervalPayload(BlockPos pos, int ticks) implements CustomPacketPayload {
    public static final Type<IntervalPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "interval"));
    public static final StreamCodec<RegistryFriendlyByteBuf, IntervalPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, IntervalPayload::pos,
        ByteBufCodecs.VAR_INT, IntervalPayload::ticks,
        IntervalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IntervalPayload msg, IPayloadContext ctx) {
        if (!ctx.player().canInteractWithBlock(msg.pos(), 4.0)) return;
        if (ctx.player().level().getBlockEntity(msg.pos()) instanceof IntervalHost h) h.setInterval(msg.ticks());
    }
}
