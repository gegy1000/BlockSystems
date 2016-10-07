package net.gegy1000.blocksystems.client;

import net.gegy1000.blocksystems.BlockSystems;
import net.gegy1000.blocksystems.client.render.blocksystem.BlockSystemRenderHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.gegy1000.blocksystems.server.blocksystem.ServerBlockSystemHandler;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystemPlayerHandler;

public class ClientEventHandler {
    private static final Minecraft MINECRAFT = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        WorldClient world = MINECRAFT.theWorld;
        if (world != null) {
            BlockSystems.PROXY.getBlockSystemHandler(world).update();
            BlockSystemRenderHandler.update();
        }
    }

    @SubscribeEvent
    public void onRightClickAir(PlayerInteractEvent.RightClickEmpty event) {
        EntityPlayer player = event.getEntityPlayer();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld());
        structureHandler.interact(structureHandler.get(structureHandler.getMousedOver(player), player), player, event.getHand());
    }

    @SubscribeEvent
    public void onClickAir(PlayerInteractEvent.LeftClickEmpty event) {
        EntityPlayer player = event.getEntityPlayer();
        BlockSystem mousedOver = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).getMousedOver(player);
        if (mousedOver != null) {
            BlockSystemPlayerHandler mouseOverHandler = BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).get(mousedOver, player);
            if (mouseOverHandler != null) {
                RayTraceResult mouseOver = mouseOverHandler.getMouseOver();
                if (mouseOver != null && mouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                    mouseOverHandler.clickBlock(mouseOver.getBlockPos());
                }
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();
        World world = event.getWorld();
        ServerBlockSystemHandler structureHandler = BlockSystems.PROXY.getBlockSystemHandler(world);
        if (entity instanceof EntityPlayer) {
            structureHandler.addPlayer((EntityPlayer) entity);
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof EntityPlayer) {
            BlockSystems.PROXY.getBlockSystemHandler(entity.worldObj).removePlayer((EntityPlayer) entity);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        BlockSystems.PROXY.getBlockSystemHandler(event.getWorld()).unloadWorld();
        BlockSystemRenderHandler.removeAll();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        EntityPlayer player = MINECRAFT.thePlayer;
        float partialTicks = event.getPartialTicks();
        double playerX = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double playerY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double playerZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;
        BlockSystemRenderHandler.render(player, playerX, playerY, playerZ, partialTicks);
    }
}
