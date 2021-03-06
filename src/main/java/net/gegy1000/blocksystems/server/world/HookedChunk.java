package net.gegy1000.blocksystems.server.world;

import com.google.common.base.Predicate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkPrimer;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.IChunkGenerator;

import java.util.List;

public class HookedChunk extends Chunk {
    public HookedChunk(World world, int x, int z) {
        super(world, x, z);
    }

    public HookedChunk(World world, ChunkPrimer primer, int x, int z) {
        this(world, x, z);
    }

    @Override
    public int getHeightValue(int x, int z) {
        return 0;
    }

    @Override
    public IBlockState getBlockState(int x, int y, int z) {
        return Blocks.AIR.getDefaultState();
    }

    @Override
    public IBlockState setBlockState(BlockPos pos, IBlockState state) {
        return null;
    }

    @Override
    public void addEntity(Entity entity) {
    }

    @Override
    public void removeEntity(Entity entity) {
    }

    @Override
    public TileEntity getTileEntity(BlockPos pos, EnumCreateEntityType type) {
        return null;
    }

    @Override
    public void addTileEntity(TileEntity tile) {
    }

    @Override
    public void addTileEntity(BlockPos pos, TileEntity tile) {
    }

    @Override
    public void removeTileEntity(BlockPos pos) {
    }

    @Override
    public void onLoad() {
    }

    @Override
    public void onUnload() {
    }

    @Override
    public void onTick(boolean quick) {
    }

    @Override
    public void read(PacketBuffer buf, int availableSections, boolean groundUpContinuous) {
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean isEmptyBetween(int startY, int endY) {
        return true;
    }

    @Override
    public void generateHeightMap() {
    }

    @Override
    public void generateSkylightMap() {
    }

    @Override
    public int getBlockLightOpacity(BlockPos pos) {
        return 255;
    }

    @Override
    public int getLightFor(EnumSkyBlock type, BlockPos pos) {
        return type.defaultLightValue;
    }

    @Override
    public void setLightFor(EnumSkyBlock type, BlockPos pos, int value) {
    }

    @Override
    public int getLightSubtracted(BlockPos pos, int amount) {
        return 0;
    }

    @Override
    public void removeEntityAtIndex(Entity entity, int index) {
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return false;
    }

    @Override
    public void markDirty() {
    }

    @Override
    public void getEntitiesWithinAABBForEntity(Entity entity, AxisAlignedBB aabb, List<Entity> entities, Predicate<? super Entity> selector) {
    }

    @Override
    public <T extends Entity> void getEntitiesOfTypeWithinAABB(Class<? extends T> entityClass, AxisAlignedBB aabb, List<T> listToFill, Predicate<? super T> filter) {
    }

    @Override
    public boolean needsSaving(boolean needsModified) {
        return false;
    }

    @Override
    protected void populate(IChunkGenerator generator) {
    }

    @Override
    public void setStorageArrays(ExtendedBlockStorage[] storage) {
    }

    @Override
    public void populate(IChunkProvider chunkProvider, IChunkGenerator chunkGenrator) {
    }
}
