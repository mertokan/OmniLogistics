package com.mertokan.omnilogistics.distributor;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: Batch Distributor GUI actions. 0 = flip SPLIT / CLUSTER, 1 = auto-bind the machines around it. */
public record DistributorModePayload(BlockPos pos, int action) implements CustomPacketPayload {
    public static final Type<DistributorModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "distributor_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DistributorModePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DistributorModePayload::pos,
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT, DistributorModePayload::action,
        DistributorModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DistributorModePayload msg, IPayloadContext ctx) {
        if (!ctx.player().isWithinBlockInteractionRange(msg.pos(), 4.0)) return;
        if (!(ctx.player().level().getBlockEntity(msg.pos()) instanceof DistributorBlockEntity be)) return;
        if (msg.action() == 0) {
            be.toggleMode();
        } else if (ctx.player().level() instanceof net.minecraft.server.level.ServerLevel sl) {
            int n = be.autoBind(sl);
            ctx.player().sendOverlayMessage(net.minecraft.network.chat.Component.translatable("msg.omnilogistics.distributor_bound", n));
        }
    }
}
