package com.mertokan.omnilogistics.debug;

import com.mertokan.omnilogistics.OmniLogistics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** {@code /omni showcase} (ops): builds {@link Showcase} around the caller. Flattens the area, so use open ground. */
@EventBusSubscriber(modid = OmniLogistics.MODID)
public final class OmniCommand {
    @SubscribeEvent
    static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("omni").requires(s -> s.hasPermission(2))
            .then(Commands.literal("showcase").executes(ctx -> {
                CommandSourceStack s = ctx.getSource();
                Showcase.build(s.getLevel(), BlockPos.containing(s.getPosition()), s.getPlayer());
                s.sendSuccess(() -> Component.literal("OmniLogistics showcase built: rows north of you, supplies behind you"), true);
                return 1;
            })));
    }
}
