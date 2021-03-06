package net.gegy1000.blocksystems.client.render.blocksystem;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import net.gegy1000.blocksystems.client.ClientProxy;
import net.gegy1000.blocksystems.client.blocksystem.BlockSystemClient;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.BlockSystemRenderChunkContainer;
import net.gegy1000.blocksystems.client.render.blocksystem.chunk.VBORenderChunkContainer;
import net.gegy1000.blocksystems.server.blocksystem.BlockSystem;
import net.gegy1000.blocksystems.server.util.math.Matrix;
import net.gegy1000.blocksystems.server.util.math.QuatRotation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockSign;
import net.minecraft.block.BlockSkull;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.ListedRenderChunk;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IWorldEventListener;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector3f;

import javax.vecmath.Point3d;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@SideOnly(Side.CLIENT)
public class BlockSystemRenderer extends RenderGlobal implements IWorldEventListener {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final TextureManager TEXTURE_MANAGER = MC.getTextureManager();
    private static final BlockRendererDispatcher BLOCK_RENDERER_DISPATCHER = MC.getBlockRendererDispatcher();
    private static final TextureAtlasSprite[] DESTROY_STAGES = new TextureAtlasSprite[10];

    private final BlockSystemClient blockSystem;

    private double lastViewEntityX = Double.MIN_VALUE;
    private double lastViewEntityY = Double.MIN_VALUE;
    private double lastViewEntityZ = Double.MIN_VALUE;
    private double lastViewEntityPitch = Double.MIN_VALUE;
    private double lastViewEntityYaw = Double.MIN_VALUE;

    private double frustumUpdatePosX = Double.MIN_VALUE;
    private double frustumUpdatePosY = Double.MIN_VALUE;
    private double frustumUpdatePosZ = Double.MIN_VALUE;
    private int frustumUpdatePosChunkX = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkY = Integer.MIN_VALUE;
    private int frustumUpdatePosChunkZ = Integer.MIN_VALUE;

    private BlockSystemViewFrustum viewFrustum;

    private Set<RenderChunk> queuedChunkUpdates = Sets.newLinkedHashSet();
    private List<ChunkRenderInformation> chunkRenderInformation = Lists.newArrayListWithCapacity(0x11040);

    private boolean displayListEntitiesDirty = true;

    private BlockSystemRenderChunkContainer chunkContainer;
    private ChunkRenderDispatcher dispatcher;

    private int viewDistance;
    private boolean vbosEnabled;

    private double prevRenderSortX;
    private double prevRenderSortY;
    private double prevRenderSortZ;

    private int frameCount;

    static {
        TextureMap textureMap = MC.getTextureMapBlocks();
        for (int i = 0; i < DESTROY_STAGES.length; i++) {
            DESTROY_STAGES[i] = textureMap.getAtlasSprite("minecraft:blocks/destroy_stage_" + i);
        }
    }

    public BlockSystemRenderer(BlockSystemClient blockSystem) {
        super(MC);
        this.blockSystem = blockSystem;
        this.viewDistance = -1;
        this.chunkContainer = new VBORenderChunkContainer();
        this.dispatcher = MC.renderGlobal.renderDispatcher;
        this.updateFrustrum(this.toLocalPos(MC.player), MC.gameSettings.renderDistanceChunks, OpenGlHelper.useVbo());
        blockSystem.addEventListener(this);
    }

    public void renderBlockSystem(Entity viewEntity, double x, double y, double z, QuatRotation rotation, float partialTicks, long finishTimeNano) {
        Point3d untransformed = this.toLocalPos(viewEntity);

        this.setup(viewEntity, partialTicks, untransformed);
        this.updateChunks(finishTimeNano);
        MC.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        GlStateManager.multMatrix(Matrix.toBuffer(rotation.getMatrix()));
        GlStateManager.enableBlend();
        GlStateManager.enableCull();

        GlStateManager.translate(-0.5, 0.0, -0.5);

        if (MC.getRenderManager().isDebugBoundingBox()) {
            GlStateManager.pushMatrix();
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            AxisAlignedBB bounds = this.blockSystem.getMaximumBounds();
            RenderGlobal.drawBoundingBox(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0F, 0.0F, 1.0F, 1.0F);
            for (int chunkX = (int) bounds.minX; chunkX < bounds.maxX; chunkX += 16) {
                for (int chunkZ = (int) bounds.minZ; chunkZ < bounds.maxZ; chunkZ += 16) {
                    Chunk chunk = this.blockSystem.getChunkFromChunkCoords(chunkX >> 4, chunkZ >> 4);
                    if (!chunk.isEmpty()) {
                        RenderGlobal.drawBoundingBox(chunkX, bounds.minY, chunkZ, chunkX + 16, Math.min(bounds.maxY, chunk.getTopFilledSegment() + 1 << 4), chunkZ + 16, 1.0F, 0.0F, 0.0F, 1.0F);
                    }
                }
            }
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.popMatrix();
        }

        MC.entityRenderer.enableLightmap();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.pushMatrix();
        this.renderBlockLayer(BlockRenderLayer.SOLID, untransformed);
        GlStateManager.enableAlpha();
        this.renderBlockLayer(BlockRenderLayer.CUTOUT_MIPPED, untransformed);
        MC.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
        this.renderBlockLayer(BlockRenderLayer.CUTOUT, untransformed);
        MC.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
        this.renderBlockLayer(BlockRenderLayer.TRANSLUCENT, untransformed);
        GlStateManager.popMatrix();

        MC.entityRenderer.disableLightmap();

        this.renderEntities(partialTicks);
        this.renderBlockSelection(viewEntity);

        // TODO: Sync breaking data to client
        Map<BlockPos, Integer> breaking = new HashMap<>();

        if (breaking.size() > 0) {
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();
            GlStateManager.enableBlend();
            GlStateManager.depthMask(false);
            TEXTURE_MANAGER.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 0.5F);
            GlStateManager.doPolygonOffset(-3.0F, -3.0F);
            GlStateManager.enablePolygonOffset();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.enableAlpha();
            GlStateManager.pushMatrix();
            RenderHelper.disableStandardItemLighting();
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            builder.noColor();

            for (Map.Entry<BlockPos, Integer> entry : breaking.entrySet()) {
                BlockPos pos = entry.getKey();
                IBlockState state = this.blockSystem.getBlockState(pos);
                Block block = state.getBlock();
                TileEntity tile = this.blockSystem.getTileEntity(pos);
                boolean hasBreak = block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockSign || block instanceof BlockSkull;
                if (!hasBreak) {
                    hasBreak = tile != null && tile.canRenderBreaking();
                }
                if (!hasBreak) {
                    BLOCK_RENDERER_DISPATCHER.renderBlockDamage(state, pos, DESTROY_STAGES[entry.getValue()], this.blockSystem);
                }
            }

            tessellator.draw();
            GlStateManager.doPolygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            GlStateManager.depthMask(true);
            RenderHelper.enableStandardItemLighting();
            GlStateManager.popMatrix();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            TEXTURE_MANAGER.getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableAlpha();
        }

        GlStateManager.popMatrix();
    }

    private void renderBlockSelection(Entity viewEntity) {
        RenderHelper.enableStandardItemLighting();
        BlockSystem mouseOverSystem = ClientProxy.getMouseOverSystem();
        if (viewEntity instanceof EntityPlayer && this.blockSystem.equals(mouseOverSystem)) {
            RayTraceResult mouseOver = ClientProxy.getMouseOver();
            if (mouseOver != null) {
                BlockPos pos = mouseOver.getBlockPos();
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
                GlStateManager.glLineWidth(2.0F);
                GlStateManager.disableTexture2D();
                GlStateManager.depthMask(false);
                IBlockState state = this.blockSystem.getBlockState(pos);
                if (state.getMaterial() != Material.AIR) {
                    RenderGlobal.drawSelectionBoundingBox(state.getSelectedBoundingBox(this.blockSystem, pos).grow(0.002), 0.0F, 0.0F, 0.0F, 0.4F);
                }
                GlStateManager.depthMask(true);
                GlStateManager.enableTexture2D();
                GlStateManager.disableBlend();
            }
        }
    }

    private void renderEntities(float partialTicks) {
        RenderHelper.enableStandardItemLighting();

        TileEntityRendererDispatcher.instance.preDrawBatch();

        for (ChunkRenderInformation information : this.chunkRenderInformation) {
            List<TileEntity> blockEntities = information.chunk.getCompiledChunk().getTileEntities();
            for (TileEntity blockEntity : blockEntities) {
//                    if (!camera.isBoundingBoxInFrustum(blockEntity.getRenderBoundingBox())) {
//                        continue;
//                    }
                BlockPos chunkPosition = information.chunk.getPosition();

                BlockPos partition = blockEntity.getPos();
                int localX = (partition.getX() & 0xF) + chunkPosition.getX();
                int localY = (partition.getY() & 0xF) + chunkPosition.getY();
                int localZ = (partition.getZ() & 0xF) + chunkPosition.getZ();
                TileEntityRendererDispatcher.instance.render(blockEntity, localX, localY, localZ, partialTicks);
            }
        }

        TileEntityRendererDispatcher.instance.drawBatch(0);
    }

    private void setup(Entity viewEntity, float partialTicks, Point3d untransformed) {
        int frameCount = this.frameCount++;

        int viewDistance = MC.gameSettings.renderDistanceChunks;
        boolean vbos = OpenGlHelper.useVbo();
        this.updateFrustrum(untransformed, viewDistance, vbos);

        double deltaX = untransformed.x - this.frustumUpdatePosX;
        double deltaY = untransformed.y - this.frustumUpdatePosY;
        double deltaZ = untransformed.z - this.frustumUpdatePosZ;
        double delta = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;

        int chunkX = (int) (untransformed.x) >> 4;
        int chunkY = (int) (untransformed.y) >> 4;
        int chunkZ = (int) (untransformed.z) >> 4;

        if (this.frustumUpdatePosChunkX != chunkX || this.frustumUpdatePosChunkY != chunkY || this.frustumUpdatePosChunkZ != chunkZ || delta > 16.0D) {
            this.frustumUpdatePosX = untransformed.x;
            this.frustumUpdatePosY = untransformed.y;
            this.frustumUpdatePosZ = untransformed.z;
            this.frustumUpdatePosChunkX = chunkX;
            this.frustumUpdatePosChunkY = chunkY;
            this.frustumUpdatePosChunkZ = chunkZ;
            this.viewFrustum.updateChunkPositions(untransformed.x, untransformed.z);
        }

        Point3d untransformedLastTick = this.toLocalPos(viewEntity.lastTickPosX, viewEntity.lastTickPosY, viewEntity.lastTickPosZ);

        double viewRenderX = untransformedLastTick.x + (untransformed.x - untransformedLastTick.x) * partialTicks;
        double viewRenderY = untransformedLastTick.y + (untransformed.y - untransformedLastTick.y) * partialTicks;
        double viewRenderZ = untransformedLastTick.z + (untransformed.z - untransformedLastTick.z) * partialTicks;
        this.chunkContainer.initialize();

        BlockPos eyePosition = new BlockPos(viewRenderX, viewRenderY + viewEntity.getEyeHeight(), viewRenderZ);
        RenderChunk eyeChunk = this.viewFrustum.getChunk(eyePosition);
        BlockPos viewChunkCorner = new BlockPos(MathHelper.floor(viewRenderX / 16.0D) * 16, MathHelper.floor(viewRenderY / 16.0D) * 16, MathHelper.floor(viewRenderZ / 16.0D) * 16);
        this.displayListEntitiesDirty = this.displayListEntitiesDirty || !this.queuedChunkUpdates.isEmpty() || untransformed.x != this.lastViewEntityX || untransformed.y != this.lastViewEntityY || untransformed.z != this.lastViewEntityZ || (double) viewEntity.rotationPitch != this.lastViewEntityPitch || (double) viewEntity.rotationYaw != this.lastViewEntityYaw;
        this.lastViewEntityX = untransformed.x;
        this.lastViewEntityY = untransformed.y;
        this.lastViewEntityZ = untransformed.z;
        this.lastViewEntityPitch = (double) viewEntity.rotationPitch;
        this.lastViewEntityYaw = (double) viewEntity.rotationYaw;
        if (this.displayListEntitiesDirty) {
            this.displayListEntitiesDirty = false;
            this.chunkRenderInformation = Lists.newArrayList();
            Queue<ChunkRenderInformation> queue = Queues.newArrayDeque();
            Entity.setRenderDistanceWeight(MathHelper.clamp(this.viewDistance / 8.0D, 1.0D, 2.5D));
            boolean renderChunksMany = MC.renderChunksMany;
            if (eyeChunk != null) {
                ChunkRenderInformation chunkInformation = new ChunkRenderInformation(eyeChunk, null, 0);
                if (this.blockSystem.getBlockState(eyePosition).isOpaqueCube()) {
                    renderChunksMany = false;
                }
                eyeChunk.setFrameIndex(frameCount);
                queue.add(chunkInformation);
            } else {
                int y = eyePosition.getY() > 0 ? 248 : 8;
                for (int x = -this.viewDistance; x <= this.viewDistance; ++x) {
                    for (int z = -this.viewDistance; z <= this.viewDistance; ++z) {
                        RenderChunk chunk = this.viewFrustum.getChunk(new BlockPos((x << 4) + 8, y, (z << 4) + 8));
                        if (chunk != null && !chunk.compiledChunk.isEmpty()/* && ((ICamera) camera).isBoundingBoxInFrustum(chunk.boundingBox)*/) { //TODO Camera Culling
                            chunk.setFrameIndex(frameCount);
                            queue.add(new ChunkRenderInformation(chunk, null, 0));
                        }
                    }
                }
            }
            while (!queue.isEmpty()) {
                ChunkRenderInformation renderInformation = queue.poll();
                RenderChunk chunk = renderInformation.chunk;
                EnumFacing facing = renderInformation.facing;
                this.chunkRenderInformation.add(renderInformation);
                for (EnumFacing offset : EnumFacing.values()) {
                    RenderChunk offsetChunk = this.getOffsetChunk(viewChunkCorner, chunk, offset);
                    if ((!renderChunksMany || !renderInformation.hasDirection(offset.getOpposite())) && (!renderChunksMany || facing == null || chunk.getCompiledChunk().isVisible(facing.getOpposite(), offset)) && offsetChunk != null && offsetChunk.setFrameIndex(frameCount)/* && ((ICamera) camera).isBoundingBoxInFrustum(offsetChunk.boundingBox)*/) { //TODO Camera culling
                        ChunkRenderInformation offsetRenderInformation = new ChunkRenderInformation(offsetChunk, offset, renderInformation.index + 1);
                        offsetRenderInformation.setDirection(renderInformation.setFacing, offset);
                        queue.add(offsetRenderInformation);
                    }
                }
            }
        }

        Set<RenderChunk> newQueuedChunkUpdates = this.queuedChunkUpdates;
        this.queuedChunkUpdates = Sets.newLinkedHashSet();
        for (ChunkRenderInformation renderInformation : this.chunkRenderInformation) {
            RenderChunk chunk = renderInformation.chunk;
            if (chunk.needsUpdate() || newQueuedChunkUpdates.contains(chunk)) {
                this.displayListEntitiesDirty = true;
                BlockPos centerPos = chunk.getPosition().add(8, 8, 8);
                boolean distance = centerPos.distanceSq(eyePosition) < 768.0D;
                if (!chunk.needsImmediateUpdate() && !distance) {
                    this.queuedChunkUpdates.add(chunk);
                } else {
                    this.dispatcher.updateChunkNow(chunk);
                    chunk.clearNeedsUpdate();
                }
            }
        }
        this.queuedChunkUpdates.addAll(newQueuedChunkUpdates);
    }

    private void updateFrustrum(Point3d untransformed, int viewDistance, boolean vbos) {
        if (this.viewDistance != viewDistance || this.vbosEnabled != vbos) {
            this.viewDistance = viewDistance;
            this.vbosEnabled = vbos;
            if (this.viewFrustum != null) {
                this.viewFrustum.delete();
            }
            this.displayListEntitiesDirty = true;
            this.chunkRenderInformation.clear();
            this.queuedChunkUpdates.clear();
            this.viewFrustum = new BlockSystemViewFrustum(this, this.blockSystem, viewDistance, this, vbos ? RenderChunk::new : ListedRenderChunk::new);
            this.viewFrustum.updateChunkPositions(untransformed.x, untransformed.z);
        }
    }

    public void renderBlockLayer(BlockRenderLayer layer, Point3d untransformed) {
        RenderHelper.disableStandardItemLighting();
        if (layer == BlockRenderLayer.TRANSLUCENT) {
            double deltaX = untransformed.x - this.prevRenderSortX;
            double deltaY = untransformed.y - this.prevRenderSortY;
            double deltaZ = untransformed.z - this.prevRenderSortZ;
            if (deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ > 1.0D) {
                this.prevRenderSortX = untransformed.x;
                this.prevRenderSortY = untransformed.y;
                this.prevRenderSortZ = untransformed.z;
                int count = 0;
                for (ChunkRenderInformation renderInformation : this.chunkRenderInformation) {
                    if (renderInformation.chunk.compiledChunk.isLayerStarted(layer) && count++ < 15) {
                        this.dispatcher.updateTransparencyLater(renderInformation.chunk);
                    }
                }
            }
        }
        boolean isTranslucent = layer == BlockRenderLayer.TRANSLUCENT;
        int start = isTranslucent ? this.chunkRenderInformation.size() - 1 : 0;
        int target = isTranslucent ? -1 : this.chunkRenderInformation.size();
        int increment = isTranslucent ? -1 : 1;
        for (int i = start; i != target; i += increment) {
            RenderChunk chunk = this.chunkRenderInformation.get(i).chunk;
            if (!chunk.getCompiledChunk().isLayerEmpty(layer)) {
                this.chunkContainer.addChunk(chunk);
            }
        }
        this.renderBlockLayer(layer);
    }

    private void renderBlockLayer(BlockRenderLayer layer) {
        if (OpenGlHelper.useVbo()) {
            GlStateManager.glEnableClientState(32884);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(32888);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.glEnableClientState(32888);
            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.glEnableClientState(32886);
        }
        this.chunkContainer.renderLayer(layer);
        if (OpenGlHelper.useVbo()) {
            for (VertexFormatElement element : DefaultVertexFormats.BLOCK.getElements()) {
                VertexFormatElement.EnumUsage usage = element.getUsage();
                int index = element.getIndex();
                switch (usage) {
                    case POSITION:
                        GlStateManager.glDisableClientState(32884);
                        break;
                    case UV:
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit + index);
                        GlStateManager.glDisableClientState(32888);
                        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit);
                        break;
                    case COLOR:
                        GlStateManager.glDisableClientState(32886);
                        GlStateManager.resetColor();
                }
            }
        }
    }

    public void queueRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean requiresUpdate) {
        this.viewFrustum.queueRenderUpdate(minX, minY, minZ, maxX, maxY, maxZ, requiresUpdate);
    }

    public void deleteChunk(int xPosition, int zPosition) {
        int x = xPosition << 4;
        int z = zPosition << 4;
        this.queueRenderUpdate(x, 0, z, x + 16, 256, z + 16, true);
    }

    @Override
    public void updateChunks(long finishTimeNano) {
        this.displayListEntitiesDirty |= this.dispatcher.runChunkUploads(finishTimeNano);
        if (!this.queuedChunkUpdates.isEmpty()) {
            Iterator<RenderChunk> iterator = this.queuedChunkUpdates.iterator();
            while (iterator.hasNext()) {
                RenderChunk chunk = iterator.next();
                boolean updated;
                if (chunk.needsImmediateUpdate()) {
                    updated = this.dispatcher.updateChunkNow(chunk);
                } else {
                    updated = this.dispatcher.updateChunkLater(chunk);
                }
                if (!updated) {
                    break;
                }
                chunk.clearNeedsUpdate();
                iterator.remove();
                long timeLeft = finishTimeNano - System.nanoTime();
                if (timeLeft < 0) {
                    break;
                }
            }
        }
    }

    public void delete() {
        this.viewFrustum.delete();
    }

    private RenderChunk getOffsetChunk(BlockPos pos, RenderChunk chunk, EnumFacing facing) {
        BlockPos offset = chunk.getBlockPosOffset16(facing);
        if (!(MathHelper.abs(pos.getX() - offset.getX()) > this.viewDistance * 16) && offset.getY() >= 0 && offset.getY() < 256) {
            return this.viewFrustum.getChunk(offset);
        } else {
            return null;
        }
    }

    @Override
    protected Vector3f getViewVector(Entity entity, double partialTicks) {
        float pitch = (float) (entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * partialTicks);
        float yaw = (float) (entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * partialTicks);
        if (MC.gameSettings.thirdPersonView == 2) {
            pitch += 180.0F;
        }
        float z = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float x = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float xzScale = -MathHelper.cos(-pitch * 0.017453292F);
        float y = MathHelper.sin(-pitch * 0.017453292F);
        Vec3d vec = this.blockSystem.getTransform().toGlobalVector(new Vec3d(x * xzScale, y, z * xzScale));
        return new Vector3f((float) vec.x, (float) vec.y, (float) vec.z);
    }

    private Point3d toLocalPos(Entity entity) {
        return this.toLocalPos(entity.posX, entity.posY, entity.posZ);
    }

    private Point3d toLocalPos(double x, double y, double z) {
        return this.blockSystem.getTransform().toLocalPos(new Point3d(x, y, z));
    }

    @Override
    public void notifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
    }

    @Override
    public void notifyLightSet(BlockPos pos) {
    }

    @Override
    public void markBlockRangeForRenderUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.queueRenderUpdate(minX - 1, minY - 1, minZ - 1, maxX + 1, maxY + 1, maxZ + 1, false);
    }

    @Override
    public void playSoundToAllNearExcept(EntityPlayer player, SoundEvent sound, SoundCategory category, double x, double y, double z, float volume, float pitch) {
    }

    @Override
    public void playRecord(SoundEvent sound, BlockPos pos) {
    }

    @Override
    public void spawnParticle(int particleID, boolean ignoreRange, double xCoord, double yCoord, double zCoord, double xSpeed, double ySpeed, double zSpeed, int... parameters) {
    }

    @Override
    public void onEntityAdded(Entity entityIn) {
    }

    @Override
    public void onEntityRemoved(Entity entityIn) {
    }

    @Override
    public void broadcastSound(int soundID, BlockPos pos, int data) {
    }

    @Override
    public void playEvent(EntityPlayer player, int type, BlockPos pos, int data) {
    }

    @Override
    public void sendBlockBreakProgress(int breakerId, BlockPos pos, int progress) {
    }

    public BlockSystemClient getBlockSystem() {
        return this.blockSystem;
    }

    @SideOnly(Side.CLIENT)
    private static class ChunkRenderInformation {
        private final RenderChunk chunk;
        private final EnumFacing facing;
        private byte setFacing;
        private final int index;

        private ChunkRenderInformation(RenderChunk chunk, EnumFacing facing, int index) {
            this.chunk = chunk;
            this.facing = facing;
            this.index = index;
        }

        public void setDirection(byte setFacing, EnumFacing facing) {
            this.setFacing = (byte) (this.setFacing | setFacing | 1 << facing.ordinal());
        }

        public boolean hasDirection(EnumFacing facing) {
            return (this.setFacing & 1 << facing.ordinal()) > 0;
        }
    }
}
