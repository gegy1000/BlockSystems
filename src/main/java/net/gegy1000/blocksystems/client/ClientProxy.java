package net.gegy1000.blocksystems.client;

import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.client.blocksystem.ClientBlockSystemHandler;
import net.gegy1000.blocksystems.client.render.RenderRegistry;
import net.gegy1000.blocksystems.server.ServerProxy;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemHandler;
import net.gegy1000.blocksystems.server.message.BaseMessage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ClientProxy extends ServerProxy {
    public static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    private static ClientBlockSystemHandler blockSystemHandler;
    private static BlockSystem mouseOverSystem;
    private static RayTraceResult mouseOver;

    @Override
    public void onPreInit() {
        super.onPreInit();
        RenderRegistry.onPreInit();
    }

    @Override
    public void onInit() {
        super.onInit();
    }

    @Override
    public void onPostInit() {
        super.onPostInit();
    }

    @Override
    public BlockSystem createBlockSystem(World mainWorld, int id) {
        return mainWorld.isRemote ? new BlockSystemClient(mainWorld, id) : super.createBlockSystem(mainWorld, id);
    }

    @Override
    public void playSound(ISound sound) {
        MINECRAFT.getSoundHandler().playSound(sound);
    }

    @Override
    public void pickBlock(EntityPlayer player, RayTraceResult mouseOver, World world, IBlockState state) {
        TileEntity tile = null;
        if (player.capabilities.isCreativeMode && GuiScreen.isCtrlKeyDown() && state.getBlock().hasTileEntity(state)) {
            tile = world.getTileEntity(mouseOver.getBlockPos());
        }
        ItemStack result = state.getBlock().getPickBlock(state, mouseOver, world, mouseOver.getBlockPos(), player);
        if (tile != null) {
            MINECRAFT.storeTEInStack(result, tile);
        }
        if (player.capabilities.isCreativeMode) {
            player.inventory.setPickedItemStack(result);
            MINECRAFT.playerController.sendSlotPacket(player.getHeldItem(EnumHand.MAIN_HAND), player.inventory.currentItem + 36);
        } else {
            int slot = player.inventory.getSlotFor(result);
            if (slot != -1) {
                if (InventoryPlayer.isHotbar(slot)) {
                    player.inventory.currentItem = slot;
                } else {
                    MINECRAFT.playerController.pickItem(slot);
                }
            }
        }
    }

    @Override
    public BlockSystemHandler getBlockSystemHandler(World world) {
        if (world.isRemote) {
            if (blockSystemHandler == null) {
                blockSystemHandler = new ClientBlockSystemHandler(MINECRAFT.player);
            }
            return blockSystemHandler;
        } else {
            return super.getBlockSystemHandler(world);
        }
    }

    @Override
    public void unload(World world) {
        if (world.isRemote) {
            if (blockSystemHandler != null) {
                blockSystemHandler.unload();
                blockSystemHandler = null;
            }
        } else {
            super.unload(world);
        }
    }

    @Override
    public void scheduleTask(MessageContext context, Runnable runnable) {
        if (context.side.isClient()) {
            MINECRAFT.addScheduledTask(runnable);
        } else {
            super.scheduleTask(context, runnable);
        }
    }

    @Override
    public void handleMessage(BaseMessage message, MessageContext context) {
        if (context.side.isClient()) {
            this.scheduleTask(context, () -> message.onReceiveClient(MINECRAFT, MINECRAFT.world, MINECRAFT.player, context));
        } else {
            super.handleMessage(message, context);
        }
    }

    @Override
    public boolean isClientPlayer(EntityPlayer player) {
        return player == MINECRAFT.player;
    }

    @Override
    public boolean isPaused(World world) {
        return MINECRAFT.isGamePaused();
    }

    public static void resetMouseOver() {
        ClientProxy.mouseOverSystem = null;
        ClientProxy.mouseOver = null;
    }

    public static void updateMouseOver(BlockSystem mouseOverSystem, RayTraceResult mouseOver) {
        ClientProxy.mouseOverSystem = mouseOverSystem;
        ClientProxy.mouseOver = mouseOver;
    }

    public static BlockSystem getMouseOverSystem() {
        return mouseOverSystem;
    }

    public static RayTraceResult getMouseOver() {
        return mouseOver;
    }
}
