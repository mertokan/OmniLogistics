package com.mertokan.omnilogistics.core;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.MenuProvider;

/** MenuProvider that also writes the client-side menu constructor data. */
public interface MenuHost extends MenuProvider {
    void writeMenuData(RegistryFriendlyByteBuf buf);
}
