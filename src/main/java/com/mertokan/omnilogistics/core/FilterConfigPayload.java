package com.mertokan.omnilogistics.core;

import com.mertokan.omnilogistics.api.FilterSpec;
import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.router.CardHost;
import com.mertokan.omnilogistics.router.CardSlots;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import com.mertokan.omnilogistics.router.SlotCardHost;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Client -> server: full filter config for a block ({@code hand == -1}), the card in hand ({@code hand >= 0})
 * or a card sitting in a block slot ({@code hand <= -2}, see {@link SlotCardHost#handCode}).
 * The reference stack goes through the ghost slot, not this payload.
 */
public record FilterConfigPayload(BlockPos pos, int hand, byte[] modes, int flags, List<String> tags, List<String> components)
    implements CustomPacketPayload {
    public static final Type<FilterConfigPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "filter_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterConfigPayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, FilterConfigPayload::pos,
        ByteBufCodecs.VAR_INT, FilterConfigPayload::hand,
        ByteBufCodecs.BYTE_ARRAY, FilterConfigPayload::modes,
        ByteBufCodecs.VAR_INT, FilterConfigPayload::flags,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(FilterSpec.MAX_TAGS)), FilterConfigPayload::tags,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(FilterSpec.MAX_COMPONENTS)), FilterConfigPayload::components,
        FilterConfigPayload::new);

    public static FilterConfigPayload of(FilterMenu menu, byte[] modes, int flags, List<String> tags, List<String> components) {
        return new FilterConfigPayload(menu.pos, menu.hand, modes, flags, tags, components);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Server main thread. Only accepted from the player whose open FilterMenu targets exactly this host. */
    public static void handle(FilterConfigPayload msg, IPayloadContext ctx) {
        if (!(ctx.player().containerMenu instanceof FilterMenu m) || m.hand != msg.hand() || !m.pos.equals(msg.pos())) return;
        FilterHost host = resolve(ctx.player(), msg.pos(), msg.hand());
        if (host != null && msg.modes().length == host.layout().modeCount())
            host.applyConfig(msg.modes(), msg.flags(), msg.tags(), msg.components());
    }

    /** Trust boundary: the client picks pos/hand, so verify reach and item type. Works on both sides. */
    public static @Nullable FilterHost resolve(Player p, BlockPos pos, int hand) {
        if (hand >= 0) {
            InteractionHand h = InteractionHand.values()[hand & 1];
            return p.getItemInHand(h).getItem() instanceof LogisticsCardItem ? new CardHost(p, h) : null;
        }
        if (!p.canInteractWithBlock(pos, 4.0)) return null;
        if (hand <= -2) return p.level().getBlockEntity(pos) instanceof CardSlots c ? SlotCardHost.of(c, SlotCardHost.slotOf(hand)) : null;
        return p.level().getBlockEntity(pos) instanceof FilterHost f ? f : null;
    }
}
