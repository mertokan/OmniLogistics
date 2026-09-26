package com.mertokan.omnilogistics.extractor;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: toggle the extractor between EXTRACT and FUSE. */
public record ExtractorModePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ExtractorModePayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(OmniLogistics.MODID, "extractor_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExtractorModePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, ExtractorModePayload::pos,
        ExtractorModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExtractorModePayload msg, IPayloadContext ctx) {
        if (!ctx.player().isWithinBlockInteractionRange(msg.pos(), 4.0)) return;
        if (ctx.player().level().getBlockEntity(msg.pos()) instanceof ExtractorBlockEntity be) be.toggleMode();
    }
}
