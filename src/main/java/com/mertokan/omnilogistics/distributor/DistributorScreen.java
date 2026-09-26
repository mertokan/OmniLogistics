package com.mertokan.omnilogistics.distributor;

import com.mertokan.omnilogistics.OmniLogistics;
import com.mertokan.omnilogistics.core.DarkScreen;
import com.mertokan.omnilogistics.core.ToggleButton;
import com.mertokan.omnilogistics.router.LogisticsCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import static com.mertokan.omnilogistics.distributor.DistributorBlockEntity.*;
import static com.mertokan.omnilogistics.distributor.DistributorMenu.*;

/** Two columns of lanes: card, buffer, the destination machine's icon, a status pip. Locked lanes are dimmed. */
public class DistributorScreen extends DarkScreen<DistributorMenu> {
    private static final int[] PIP = {0xFF3A414B, 0xFFD9A832, 0xFF1F7A8C, 0xFF1F8C4A, 0xFFC04A16, 0xFF23272E, 0xFF3FD3FF};
    private static final int ON_CLUSTER = 0xFF2E7A8C;
    private ItemStack ghost = ItemStack.EMPTY, ghostUpgrade = ItemStack.EMPTY;
    private ToggleButton mode, bind;

    public DistributorScreen(DistributorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title, "distributor", 176, HEIGHT);
        inventoryLabelY = INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        ghost = new ItemStack(OmniLogistics.CARD.get());
        ghostUpgrade = new ItemStack(OmniLogistics.PARALLEL_UPGRADE.get());
        mode = addRenderableWidget(new ToggleButton(leftPos + MODE_X, topPos + MODE_Y, MODE_W, 16,
            () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new DistributorModePayload(menu.pos, 0))));
        mode.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.distributor_mode")));
        bind = addRenderableWidget(new ToggleButton(leftPos + BIND_X, topPos + MODE_Y, BIND_W, 16,
            () -> net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new DistributorModePayload(menu.pos, 1))));
        bind.setMessage(Component.translatable("gui.omnilogistics.distributor_bind"));
        bind.setTooltip(Tooltip.create(Component.translatable("tooltip.omnilogistics.distributor_bind", DistributorBlockEntity.BIND_RADIUS)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        mode.on = menu.cluster();
        mode.onColor = ON_CLUSTER;
        mode.setMessage(Component.translatable(menu.cluster() ? "gui.omnilogistics.distributor_cluster" : "gui.omnilogistics.distributor_split"));
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < LANES; i++) {
            int x = leftPos + laneX(i), y = topPos + laneY(i);
            if (i >= menu.activeLanes()) {   // locked until the next Parallel Upgrade
                g.fill(x - 1, y - 1, x + laneX(0) + PIP_X - CARD_X + 6, y + 17, 0xB0101317);
                continue;
            }
            if (!menu.getSlot(i).hasItem()) ghost(g, ghost, x, y);
            ItemStack icon = targetIcon(menu.getSlot(i).getItem());
            if (!icon.isEmpty()) g.fakeItem(icon, x + (ICON_X - CARD_X), y);
            int s = Math.max(0, Math.min(PIP.length - 1, menu.status(i)));
            g.fill(x + (PIP_X - CARD_X), y + 5, x + (PIP_X - CARD_X) + 6, y + 11, PIP[s]);
        }
        if (!menu.getSlot(UPGRADE_SLOT).hasItem()) ghost(g, ghostUpgrade, leftPos + UPG_X, topPos + UPG_Y);
    }

    private static void ghost(GuiGraphicsExtractor g, ItemStack s, int x, int y) {
        g.fakeItem(s, x, y);
        g.nextStratum();
        g.fill(x, y, x + 16, y + 16, 0xA0101317);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        for (int i = 0; i < LANES; i++) {
            int x = laneX(i), y = laneY(i);
            if (mx < x + (ICON_X - CARD_X) || mx >= x + (PIP_X - CARD_X) + 8 || my < y || my >= y + 16) continue;
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("gui.omnilogistics.distributor_lane_n", i + 1));
            if (i >= menu.activeLanes()) tip.add(Component.translatable("tooltip.omnilogistics.distributor_status.5"));
            else {
                ItemStack card = menu.getSlot(i).getItem();
                if (LogisticsCardItem.isFilterCard(card)) tip.add(Component.literal(target(card)));
                tip.add(Component.translatable("tooltip.omnilogistics.distributor_status." + menu.status(i)));
            }
            g.setComponentTooltipForNextFrame(font, tip, mx, my);
            return;
        }
    }

    /** The block a lane's card points at, as an item to draw; empty when unbound or that chunk is not loaded. */
    private static ItemStack targetIcon(ItemStack card) {
        BlockPos p = LogisticsCardItem.targetPos(card);
        var level = Minecraft.getInstance().level;
        if (p == null || level == null || !level.isLoaded(p)) return ItemStack.EMPTY;
        return new ItemStack(level.getBlockState(p).getBlock());
    }

    private static String target(ItemStack card) {
        BlockPos p = LogisticsCardItem.targetPos(card);
        if (p == null) return Component.translatable("tooltip.omnilogistics.card_unbound").getString();
        var level = Minecraft.getInstance().level;
        String side = " " + LogisticsCardItem.targetSide(card).getName();
        String dir = LogisticsCardItem.extractMode(card) ? "< " : "> ";
        if (level == null || !level.isLoaded(p)) return dir + p.toShortString() + side;
        return dir + level.getBlockState(p).getBlock().getName().getString() + side;
    }
}
