package com.mertokan.omnilogistics.compat;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.InfoLines;
import com.mertokan.omnilogistics.core.MachineBlock;
import com.mertokan.omnilogistics.core.TickingBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/** Jade: look at any OmniLogistics block to see its face modes, buffer, energy, cards. Loaded only when Jade is present. */
@WailaPlugin
public class OmniJadePlugin implements IWailaPlugin {
    private static final Provider PROVIDER = new Provider();

    @Override
    public void register(IWailaCommonRegistration r) {
        r.registerBlockDataProvider(PROVIDER, TickingBlockEntity.class);   // Jade wants a BlockEntity class, not an interface; the provider still checks InfoLines
    }

    @Override
    public void registerClient(IWailaClientRegistration r) {
        r.registerBlockComponent(PROVIDER, MachineBlock.class);
    }

    /** Server builds the lines (it owns the state), client just prints them. */
    private static final class Provider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(OmniLogistics.MODID, "info");

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof InfoLines info)) return;
            ListTag list = new ListTag();
            for (Component c : info.infoLines()) list.add(StringTag.valueOf(Component.Serializer.toJson(c, accessor.getLevel().registryAccess())));
            tag.put("omni_lines", list);
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            for (Tag t : accessor.getServerData().getList("omni_lines", Tag.TAG_STRING)) {
                Component c = Component.Serializer.fromJson(t.getAsString(), accessor.getLevel().registryAccess());
                if (c != null) tooltip.add(c);
            }
        }
    }
}
