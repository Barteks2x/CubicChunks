package io.github.opencubicchunks.cubicchunks.chunk.cube;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.opencubicchunks.cubicchunks.CubicChunks;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.biome.CubeBiomeContainer;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.SurfaceTrackerSection;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.SurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.ChunkSectionAccess;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.utils.MathUtil;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.EmptyTickList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TickList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.opencubicchunks.cubicchunks.utils.Coords.cubeToSection;
import static net.minecraft.world.level.chunk.LevelChunk.EMPTY_SECTION;

public class BigCube implements ChunkAccess, IBigCube {

    private static final TickingBlockEntity NULL_TICKER = new TickingBlockEntity() {
        public void tick() {
        }

        public boolean isRemoved() {
            return true;
        }

        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        public String getType() {
            return "<null>";
        }
    };
    private static final Logger LOGGER = LogManager.getLogger(BigCube.class);

    private final CubePos cubePos;
    private final LevelChunkSection[] sections = new LevelChunkSection[SECTION_COUNT];

    private final HashMap<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final Map<BlockPos, RebindableTickingBlockEntityWrapper> tickersInLevel = new HashMap<>();
    private final ClassInstanceMultiMap<Entity>[] entityLists;
    private final Level level;

    private final Map<Heightmap.Types, SurfaceTrackerSection> heightmaps;

    private final BitSet pendingHeightmapUpdates = new BitSet(DIAMETER_IN_BLOCKS * DIAMETER_IN_BLOCKS);

    private CubeBiomeContainer biomes;

    private boolean dirty = true; // todo: change back to false?
    private boolean loaded = false;

    private volatile boolean lightCorrect;
    private final Map<BlockPos, CompoundTag> deferredTileEntities = Maps.newHashMap();

    private long inhabitedTime;
    @Nullable
    private Consumer<BigCube> postLoadConsumer;

    @Nullable
    private Supplier<ChunkHolder.FullChunkStatus> fullStatus;

    public BigCube(Level worldIn, CubePos cubePosIn, CubeBiomeContainer biomeContainerIn) {
        this(worldIn, cubePosIn, biomeContainerIn, UpgradeData.EMPTY, EmptyTickList.empty(), EmptyTickList.empty(), 0L, null, null);
    }

    public BigCube(Level worldIn, CubePos cubePosIn, CubeBiomeContainer biomeContainerIn, UpgradeData upgradeDataIn, TickList<Block> tickBlocksIn,
                   TickList<Fluid> tickFluidsIn, long inhabitedTimeIn, @Nullable LevelChunkSection[] sectionsIn, @Nullable Consumer<BigCube> postLoadConsumerIn) {
        this.level = worldIn;
        this.cubePos = cubePosIn;
        this.heightmaps = Maps.newEnumMap(Heightmap.Types.class);
//        this.upgradeData = upgradeDataIn;

//        for(Heightmap.Type heightmap$type : Heightmap.Type.values()) {
//            if (ChunkStatus.FULL.getHeightMaps().contains(heightmap$type)) {
//                this.heightMap.put(heightmap$type, new Heightmap(this, heightmap$type));
//            }
//        }

        //noinspection unchecked
        this.entityLists = new ClassInstanceMultiMap[IBigCube.SECTION_COUNT];
        for(int i = 0; i < this.entityLists.length; ++i) {
            this.entityLists[i] = new ClassInstanceMultiMap<>(Entity.class);
        }

        this.biomes = biomeContainerIn;
//        this.blockBiomeArray = biomeContainerIn;
//        this.blocksToBeTicked = tickBlocksIn;
//        this.fluidsToBeTicked = tickFluidsIn;
        this.inhabitedTime = inhabitedTimeIn;
        this.postLoadConsumer = postLoadConsumerIn;

        if(sectionsIn != null) {
            if (sectionsIn.length != SECTION_COUNT) {
                throw new IllegalStateException("Number of Sections must equal BigCube.CUBESIZE");
            }

            for (int i = 0; i < sectionsIn.length; i++) {
                int sectionYPos = cubeToSection(cubePosIn.getY(), Coords.indexToY(i));

                if(sectionsIn[i] != null) {
                    sections[i] = new LevelChunkSection(sectionYPos,
                            ((ChunkSectionAccess) sectionsIn[i]).getNonEmptyBlockCount(),
                            ((ChunkSectionAccess) sectionsIn[i]).getTickingBlockCount(),
                            ((ChunkSectionAccess) sectionsIn[i]).getTickingFluidCount());
                    //noinspection ConstantConditions
                    ((ChunkSectionAccess) sections[i]).setStates(sectionsIn[i].getStates());
                }
            }
        }

//        this.gatherCapabilities();
    }

    public BigCube(Level worldIn, CubePrimer cubePrimerIn, @Nullable Consumer<BigCube> consumer) {
        //TODO: reimplement full BigCube constructor from CubePrimer
//        this(worldIn, cubePrimerIn.getCubePos(), cubePrimerIn.getCubeBiomes(), cubePrimerIn.getUpgradeData(), cubePrimerIn.getBlocksToBeTicked(),
//            cubePrimerIn.getFluidsToBeTicked(), cubePrimerIn.getInhabitedTime(), cubePrimerIn.getSections(), (Consumer<BigCube>)null);
        this(worldIn, cubePrimerIn.getCubePos(), cubePrimerIn.getCubeBiomes(), null, null,
                null, cubePrimerIn.getCubeInhabitedTime(), cubePrimerIn.getCubeSections(), consumer);

        for(CompoundTag compoundnbt : cubePrimerIn.getCubeEntities()) {
            EntityType.loadEntityRecursive(compoundnbt, worldIn, (entity) -> {
                this.addEntity(entity);
                return entity;
            });
        }

        for(BlockEntity tileentity : cubePrimerIn.getCubeTileEntities().values()) {
            this.setBlockEntity(tileentity);
        }

        this.deferredTileEntities.putAll(cubePrimerIn.getDeferredTileEntities());

        //TODO: reimplement missing BigCube methods
//        for(int i = 0; i < cubePrimerIn.getPackedPositions().length; ++i) {
//            this.packedBlockPositions[i] = cubePrimerIn.getPackedPositions()[i];
//        }

        //this.setStructureStarts(cubePrimerIn.getStructureStarts());
        //this.setStructureReferences(cubePrimerIn.getStructureReferences());

//        for(Map.Entry<Heightmap.Type, Heightmap> entry : cubePrimerIn.getHeightmaps()) {
//            if (ChunkStatus.FULL.getHeightMaps().contains(entry.getKey())) {
//                this.getHeightmap(entry.getKey()).setDataArray(entry.getValue().getDataArray());
//            }
//        }

        this.setCubeLight(cubePrimerIn.hasCubeLight());
        this.dirty = true;
    }

    @Deprecated @Override public ChunkPos getPos() {
        throw new UnsupportedOperationException("Not implemented");
    }
    @Override public CubePos getCubePos()
    {
        return this.cubePos;
    }

    @Deprecated @Override public LevelChunkSection[] getSections() {
        return this.sections;
    }
    @Override public LevelChunkSection[] getCubeSections() {
        return this.sections;
    }

    //STATUS
    @Override public void setCubeStatus(ChunkStatus status) { throw new UnsupportedOperationException("BigCube does not have a setter for setCubeStatus"); }
    @Deprecated @Override public ChunkStatus getStatus() { return this.getCubeStatus(); }
    @Override public ChunkStatus getCubeStatus() {
        return ChunkStatus.FULL;
    }

    public ChunkHolder.FullChunkStatus getFullStatus() {
        return this.fullStatus == null ? ChunkHolder.FullChunkStatus.BORDER : this.fullStatus.get();
    }

    public void setFullStatus(Supplier<ChunkHolder.FullChunkStatus> supplier) {
        this.fullStatus = supplier;
    }

    //BLOCK
    @Deprecated @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        return setBlock(pos, state, isMoving);
    }
    @Override @Nullable public BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving) {
        return this.setBlock(Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ()), pos, state, isMoving);
    }
    @Nullable public BlockState setBlock(int sectionIndex, BlockPos pos, BlockState newState, boolean isMoving) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;
        LevelChunkSection chunksection = sections[sectionIndex];

        BlockState oldState = chunksection.setBlockState(
                x, y, z, newState);
        if (oldState == newState) {
            return null;
        }
        Block newBlock = newState.getBlock();
        int localX = Coords.blockToLocal(pos.getX());
        int localZ = Coords.blockToLocal(pos.getZ());



        if (!this.heightmaps.isEmpty()) {
            this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING).markDirty(localX, localZ);
            this.heightmaps.get(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES).markDirty(localX, localZ);
            this.heightmaps.get(Heightmap.Types.OCEAN_FLOOR).markDirty(localX, localZ);
            this.heightmaps.get(Heightmap.Types.WORLD_SURFACE).markDirty(localX, localZ);
        }

        boolean hadBlockEntity = oldState.hasBlockEntity();
        if (!this.level.isClientSide) {
            oldState.onRemove(this.level, pos, newState, isMoving);
        } else if (!oldState.is(newBlock) && hadBlockEntity) {
            this.removeBlockEntity(pos);
        }

        if (chunksection.getBlockState(x, y, z).getBlock() != newBlock) {
            return null;
        }

        if (!this.level.isClientSide) {
            newState.onPlace(this.level, pos, oldState, isMoving);
        }

        if (newState.hasBlockEntity()) {
            BlockEntity blockEntity = this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
            if (blockEntity == null) {
                blockEntity = ((EntityBlock) newBlock).newBlockEntity(pos, newState);
                if (blockEntity != null) {
                    this.addAndRegisterBlockEntity(blockEntity);
                }
            } else {
                blockEntity.setBlockState(newState);
                this.updateBlockEntityTicker(blockEntity);
            }
        }

        this.dirty = true;
        return oldState;
    }

    @Override public BlockState getBlockState(int x, int y, int z) {
        // TODO: crash report generation
        int index = Coords.blockToIndex(x, y, z);
        return LevelChunkSection.isEmpty(this.sections[index]) ?
                Blocks.AIR.defaultBlockState() :
                this.sections[index].getBlockState(x & 15, y & 15, z & 15);
    }

    //ENTITY
    @Deprecated @Override public void addEntity(Entity entityIn) {
        // empty in vanilla too
    }

    public ClassInstanceMultiMap<Entity>[] getCubeEntityLists() {
        return entityLists;
    }
    public ClassInstanceMultiMap<Entity>[] getEntityLists() {
        return this.getCubeEntityLists();
    }

    private int getIndexFromEntity(Entity entityIn) {
        return Coords.blockToIndex((int)entityIn.getX(), (int)entityIn.getY(), (int)entityIn.getZ());
    }

    public void removeEntity(Entity entityIn) {
        this.removeEntityAtIndex(entityIn, this.getIndexFromEntity(entityIn));
    }
    public void removeEntityAtIndex(Entity entityIn, int index) {
        if (index < 0) {
            index = 0;
        }

        if (index >= this.entityLists.length) {
            index = this.entityLists.length - 1;
        }

        this.entityLists[index].remove(entityIn);
        this.setDirty(true);
    }

    //TILEENTITY
    @Deprecated @Override public void setBlockEntityNbt(CompoundTag nbt) {
        this.setCubeBlockEntity(nbt);
    }
    @Override public void setCubeBlockEntity(CompoundTag nbt) {
        this.deferredTileEntities.put(new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z")), nbt);
    }

    @Deprecated @Override public void setBlockEntity(BlockEntity tileEntityIn) { this.setCubeBlockEntity(tileEntityIn); }
    @Override public void setCubeBlockEntity(BlockEntity tileEntityIn) {
        BlockPos pos = tileEntityIn.getBlockPos();
        if (this.getBlockState(pos).hasBlockEntity()) {
            tileEntityIn.setLevel(this.level);
            tileEntityIn.clearRemoved();
            BlockEntity tileentity = this.blockEntities.put(pos.immutable(), tileEntityIn);
            if (tileentity != null && tileentity != tileEntityIn) {
                tileentity.setRemoved();
            }
        }
    }

    public void addAndRegisterBlockEntity(BlockEntity tileEntityIn) {
        this.setBlockEntity(tileEntityIn);
        if (isInLevel()) {
            this.updateBlockEntityTicker(tileEntityIn);
        }
    }

    public void registerAllBlockEntitiesAfterLevelLoad() {
        this.blockEntities.values().forEach(this::updateBlockEntityTicker);
    }

    private <T extends BlockEntity> void updateBlockEntityTicker(T blockEntity) {
        BlockState blockState = blockEntity.getBlockState();
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> blockEntityTicker = (BlockEntityTicker<T>) blockState.getTicker(this.level, blockEntity.getType());
        if (blockEntityTicker == null) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
        } else {
            this.tickersInLevel.compute(blockEntity.getBlockPos(), (blockPos, wrapper) -> {
                TickingBlockEntity tickingBlockEntity = this.createTicker(blockEntity, blockEntityTicker);
                if (wrapper != null) {
                    wrapper.rebind(tickingBlockEntity);
                    return wrapper;
                } else if (this.isInLevel()) {
                    RebindableTickingBlockEntityWrapper newWrapper = new RebindableTickingBlockEntityWrapper(tickingBlockEntity);
                    this.level.addBlockEntityTicker(newWrapper);
                    return newWrapper;
                } else {
                    return null;
                }
            });
        }

    }

    private <T extends BlockEntity> TickingBlockEntity createTicker(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
        return new BoundTickingBlockEntity(blockEntity, blockEntityTicker);
    }

    private void removeBlockEntityTicker(BlockPos blockPos) {
        RebindableTickingBlockEntityWrapper wrapper = this.tickersInLevel.remove(blockPos);
        if (wrapper != null) {
            wrapper.rebind(NULL_TICKER);
        }

    }

    @Deprecated @Override public void removeBlockEntity(BlockPos pos) { this.removeCubeBlockEntity(pos); }
    @Override public void removeCubeBlockEntity(BlockPos pos) {
        if (isInLevel()) {
            BlockEntity tileentity = this.blockEntities.remove(pos);
            if (tileentity != null) {
                tileentity.setRemoved();
            }
        }
        this.removeBlockEntityTicker(pos);
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    @Nullable public BlockEntity getBlockEntity(BlockPos pos, LevelChunk.EntityCreationType creationMode) {
        BlockEntity tileentity = this.blockEntities.get(pos);
        if (tileentity == null) {
            CompoundTag compoundnbt = this.deferredTileEntities.remove(pos);
            if (compoundnbt != null) {
                BlockEntity tileentity1 = this.promotePendingBlockEntity(pos, compoundnbt);
                if (tileentity1 != null) {
                    return tileentity1;
                }
            }
        }

        if (tileentity == null) {
            if (creationMode == LevelChunk.EntityCreationType.IMMEDIATE) {
                tileentity = this.createNewTileEntity(pos);
                if (tileentity != null) {
                    this.addAndRegisterBlockEntity(tileentity);
                }
            }
        } else if (tileentity.isRemoved()) {
            blockEntities.remove(pos);
            return null;
        }

        return tileentity;
    }

    @Nullable private BlockEntity createNewTileEntity(BlockPos pos) {
        BlockState state = this.getBlockState(pos);
        return !state.hasBlockEntity() ? null : ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
    }

    @Nullable private BlockEntity promotePendingBlockEntity(BlockPos pos, CompoundTag compound) {
        BlockEntity tileentity;
        BlockState state = this.getBlockState(pos);
        if ("DUMMY".equals(compound.getString("id"))) {
            if (state.hasBlockEntity()) {
                tileentity = ((EntityBlock) state.getBlock()).newBlockEntity(pos, state);
            } else {
                tileentity = null;
                CubicChunks.LOGGER.warn("Tried to load a DUMMY block entity @ {} but found not block entity block {} at location", pos, this.getBlockState(pos));
            }
        } else {
            tileentity = BlockEntity.loadStatic(pos, state, compound);
        }

        if (tileentity != null) {
            tileentity.setLevel(this.level);
            this.addAndRegisterBlockEntity(tileentity);
        } else {
            CubicChunks.LOGGER.warn("Tried to load a block entity for block {} but failed at location {}", state, pos);
        }

        return tileentity;
    }

    public Map<BlockPos, BlockEntity> getTileEntityMap() {
        return blockEntities;
    }
    public Map<BlockPos, CompoundTag> getDeferredTileEntityMap() {
        return this.deferredTileEntities;
    }

    @Deprecated @Override public Set<BlockPos> getBlockEntitiesPos() {
        return this.getCubeTileEntitiesPos();
    }
    @Override public Set<BlockPos> getCubeTileEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.deferredTileEntities.keySet());
        set.addAll(this.blockEntities.keySet());
        return set;
    }

    @Deprecated @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) { return this.getCubeBlockEntityNbtForSaving(pos); }
    @Nullable @Override public CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos) {
        BlockEntity tileentity = this.getBlockEntity(pos);
        if (tileentity != null && !tileentity.isRemoved()) {
            CompoundTag compoundnbt1 = tileentity.save(new CompoundTag());
            compoundnbt1.putBoolean("keepPacked", false);
            return compoundnbt1;
        } else {
            CompoundTag compoundnbt = this.deferredTileEntities.get(pos);
            if (compoundnbt != null) {
                compoundnbt = compoundnbt.copy();
                compoundnbt.putBoolean("keepPacked", true);
            }

            return compoundnbt;
        }
    }

    @Deprecated @Nullable @Override public CompoundTag getBlockEntityNbt(BlockPos pos) { return this.getCubeDeferredTileEntity(pos); }
    @Nullable @Override public CompoundTag getCubeDeferredTileEntity(BlockPos pos) {
        return this.deferredTileEntities.get(pos);
    }

    //LIGHTING
    @Deprecated @Override public boolean isLightCorrect() { throw new UnsupportedOperationException("Chunk method called on a cube!"); }
    @Override public boolean hasCubeLight() {
        return this.lightCorrect;
    }

    @Deprecated @Override public void setLightCorrect(boolean lightCorrectIn) { throw new UnsupportedOperationException("Chunk method called on a cube!"); }
    @Override public void setCubeLight(boolean lightCorrectIn) {
        this.lightCorrect = lightCorrectIn;
        this.setDirty(true);
    }

    @Deprecated @Override public Stream<BlockPos> getLights() { return this.getCubeLightSources(); }
    @Override public Stream<BlockPos> getCubeLightSources() {
        return StreamSupport
                .stream(BlockPos.betweenClosed(this.cubePos.minCubeX(), this.cubePos.minCubeY(), this.cubePos.minCubeZ(),
                        this.cubePos.maxCubeX(), this.cubePos.maxCubeY(), this.cubePos.maxCubeZ())
                        .spliterator(), false).filter((blockPos) -> this.getBlockState(blockPos).getLightEmission() != 0);
    }

    //MISC
    public boolean isInLevel() {
        return this.loaded || this.level.isClientSide();
    }

    private boolean isTicking(BlockPos blockPos) {
        return (this.level.isClientSide() || this.getFullStatus().isOrAfter(ChunkHolder.FullChunkStatus.TICKING)) && this.level.getWorldBorder().isWithinBounds(blockPos);
    }

    @Deprecated @Override public void setUnsaved(boolean modified) { setDirty(modified); }
    @Override public void setDirty(boolean modified) {
        this.dirty = modified;
    }

    @Deprecated @Override public boolean isUnsaved() { return isDirty(); }
    @Override public boolean isDirty() {
        return this.dirty;
    }

    @Override public boolean isEmptyCube() {
        for(LevelChunkSection section : this.sections) {
            if(section != EMPTY_SECTION && !section.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Deprecated @Override public long getInhabitedTime() { return this.getCubeInhabitedTime(); }
    @Override public long getCubeInhabitedTime() {
        return this.inhabitedTime;
    }

    @Deprecated @Override public void setInhabitedTime(long newInhabitedTime) { this.setCubeInhabitedTime(newInhabitedTime); }
    @Override public void setCubeInhabitedTime(long newInhabitedTime) {
        this.inhabitedTime = newInhabitedTime;
    }

    @Deprecated @Nullable @Override public ChunkBiomeContainer getBiomes() { throw new UnsupportedOperationException("Chunk method called on a cube"); }
    @Nullable @Override public CubeBiomeContainer getCubeBiomes() {
        return this.biomes;
    }

    public int getSize() {
        int size = MathUtil.ceilDiv(sections.length, Byte.SIZE); // exists flags
        for(LevelChunkSection section : this.sections)
        {
            if(section != null)
                size += section.getSerializedSize();
        }
        return size;
    }

    public void write(FriendlyByteBuf buf) {
        BitSet emptyFlags = new BitSet(sections.length);
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] != null && !sections[i].isEmpty()) {
                emptyFlags.set(i);
            }
        }
        byte[] emptyFlagsBytes = emptyFlags.toByteArray();
        byte[] actualFlagsBytes = new byte[MathUtil.ceilDiv(sections.length, Byte.SIZE)];
        System.arraycopy(emptyFlagsBytes, 0, actualFlagsBytes, 0, emptyFlagsBytes.length);
        buf.writeBytes(actualFlagsBytes);
        for (LevelChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                section.write(buf);
            }
        }
    }

    public void read(@Nullable CubeBiomeContainer biomes, FriendlyByteBuf readBuffer, CompoundTag nbtTagIn, boolean cubeExists) {
        if (!cubeExists) {
            Arrays.fill(sections, null);
            return;
        }
        byte[] emptyFlagsBytes = new byte[MathUtil.ceilDiv(sections.length, Byte.SIZE)];
        readBuffer.readBytes(emptyFlagsBytes);
        BitSet emptyFlags = BitSet.valueOf(emptyFlagsBytes);

        if(biomes != null)
            this.biomes = biomes;

        // TODO: support partial updates
        this.blockEntities.values().forEach(this::onBlockEntityRemove);
        this.blockEntities.clear();

        for (int i = 0; i < IBigCube.SECTION_COUNT; i++) {
            boolean exists = emptyFlags.get(i);

            //        byte emptyFlags = 0;
            //        for (int i = 0; i < sections.length; i++) {
            //            if (sections[i] != null && !sections[i].isEmpty()) {
            //                emptyFlags |= 1 << i;
            //            }
            //        }
            //        buf.writeByte(emptyFlags);
            //        for (int i = 0; i < sections.length; i++) {
            //            if (sections[i] != null && !sections[i].isEmpty()) {
            //                sections[i].write(buf);
            //            }
            //        }
            //        return false;

            int dy = Coords.indexToY(i);

            SectionPos sectionPos = getCubePos().asSectionPos();
            int y = sectionPos.getY() + dy;

            readSection(i, y, null, readBuffer, nbtTagIn, exists);
        }
    }

    private void onBlockEntityRemove(BlockEntity blockEntity) {
        blockEntity.setRemoved();
        this.tickersInLevel.remove(blockEntity.getBlockPos());
    }

    private void readSection(int sectionIdx, int sectionY, @Nullable CubeBiomeContainer biomeContainerIn, FriendlyByteBuf packetBufferIn, CompoundTag nbtIn,
                             boolean sectionExists) {

        LevelChunkSection section = this.sections[sectionIdx];
        if (section == EMPTY_SECTION) {
            section = new LevelChunkSection(sectionY << 4);
            this.sections[sectionIdx] = section;
        }
        if (sectionExists) {
            section.read(packetBufferIn);
        }

        if (biomeContainerIn != null) {
            this.biomes = biomeContainerIn;
        }

        for (Heightmap.Types type : Heightmap.Types.values()) {
            String typeId = type.getSerializationKey();
            if (nbtIn.contains(typeId, 12)) { // NBT TAG_LONG_ARRAY
                this.setHeightmap(type, nbtIn.getLongArray(typeId));
            }
        }
    }

    @Deprecated
    public SectionPos getSectionPosition(int index)
    {
        int xPos = Coords.indexToX(index);
        int yPos = Coords.indexToY(index);
        int zPos = Coords.indexToZ(index);

        SectionPos sectionPos = this.cubePos.asSectionPos();
        return SectionPos.of(xPos + sectionPos.getX(), yPos + sectionPos.getY(), zPos + sectionPos.getZ());
    }


    public Level getLevel() {
        return level;
    }

    @Override public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] data) {

    }

    @Override public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types typeIn) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public int getHeight(Heightmap.Types heightmapType, int x, int z) {
        return 0;
    }

    @Deprecated @Override public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Deprecated @Override public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> structureStartsIn) {

    }

    @Override public ShortList[] getPostProcessing() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public TickList<Block> getBlockTicks() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public TickList<Fluid> getLiquidTicks() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public UpgradeData getUpgradeData() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public FluidState getFluidState(BlockPos pos) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // getStructureStart
    @Nullable @Override public StructureStart<?> getStartForFeature(StructureFeature<?> var1) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // putStructureStart
    @Override public void setStartForFeature(StructureFeature<?> structureIn, StructureStart<?> structureStartIn) {

    }

    // getStructureReferences
    @Override public LongSet getReferencesForFeature(StructureFeature<?> structureIn) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // addStructureReference
    @Override public void addReferenceForFeature(StructureFeature<?> structure, long reference) {

    }

    @Override public Map<StructureFeature<?>, LongSet> getAllReferences() {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override public void setAllReferences(Map<StructureFeature<?>, LongSet> p_201606_1_) {

    }

    public void setLoaded(boolean loaded) {
        this.loaded = loaded;
    }
    public boolean getLoaded()
    {
        return this.loaded;
    }
    public void postLoad() {
        if (this.postLoadConsumer != null) {
            this.postLoadConsumer.accept(this);
            this.postLoadConsumer = null;
        }
        // TODO heightmap stuff should probably be elsewhere rather than here.
        ChunkPos pos = this.cubePos.asChunkPos();
        for (int x = 0; x < IBigCube.DIAMETER_IN_SECTIONS; x++) {
            for (int z = 0; z < IBigCube.DIAMETER_IN_SECTIONS; z++) {
                // TODO we really, *really* shouldn't be force-loading columns here.
                //      probably the easiest approach until we get a "columns before cubes" invariant though.
                LevelChunk chunk = this.level.getChunk(pos.x + x, pos.z + z);
                for (Map.Entry<Heightmap.Types, Heightmap> entry : chunk.getHeightmaps()) {
                    Heightmap heightmap = entry.getValue();
                    SurfaceTrackerWrapper tracker = (SurfaceTrackerWrapper) heightmap;
                    tracker.loadCube(this);
                }
            }
        }
    }

    @Override public int getSectionsCount() {
        return this.level.getSectionsCount();
    }

    @Override public int getMinSection() {
        return this.level.getMinSection();
    }

    public void invalidateAllBlockEntities() {
        this.blockEntities.values().forEach(this::onBlockEntityRemove);
    }

    public static class RebindableTickingBlockEntityWrapper implements TickingBlockEntity {
        private TickingBlockEntity ticker;

        private RebindableTickingBlockEntityWrapper(TickingBlockEntity tickingBlockEntity) {
            this.ticker = tickingBlockEntity;
        }

        private void rebind(TickingBlockEntity tickingBlockEntity) {
            this.ticker = tickingBlockEntity;
        }

        public void tick() {
            this.ticker.tick();
        }

        public boolean isRemoved() {
            return this.ticker.isRemoved();
        }

        public BlockPos getPos() {
            return this.ticker.getPos();
        }

        public String getType() {
            return this.ticker.getType();
        }

        public String toString() {
            return this.ticker.toString() + " <wrapped>";
        }
    }

    public class BoundTickingBlockEntity<T extends BlockEntity> implements TickingBlockEntity {
        private final T blockEntity;
        private final BlockEntityTicker<T> ticker;
        private boolean loggedInvalidBlockState;

        private BoundTickingBlockEntity(T blockEntity, BlockEntityTicker<T> blockEntityTicker) {
            this.blockEntity = blockEntity;
            this.ticker = blockEntityTicker;
        }

        public void tick() {
            if (!this.blockEntity.isRemoved() && this.blockEntity.hasLevel()) {
                BlockPos blockPos = this.blockEntity.getBlockPos();
                if (BigCube.this.isTicking(blockPos)) {
                    try {
                        ProfilerFiller profilerFiller = BigCube.this.level.getProfiler();
                        profilerFiller.push(this::getType);
                        BlockState blockState = BigCube.this.getBlockState(blockPos);
                        if (this.blockEntity.getType().isValid(blockState)) {
                            this.ticker.tick(BigCube.this.level, this.blockEntity.getBlockPos(), blockState, this.blockEntity);
                            this.loggedInvalidBlockState = false;
                        } else if (!this.loggedInvalidBlockState) {
                            this.loggedInvalidBlockState = true;
                            BigCube.LOGGER.warn("Block entity {} @ {} state {} invalid for ticking:", new org.apache.logging.log4j.util.Supplier[]{this::getType, this::getPos, () -> {
                                return blockState;
                            }});
                        }

                        profilerFiller.pop();
                    } catch (Throwable var5) {
                        CrashReport crashReport = CrashReport.forThrowable(var5, "Ticking block entity");
                        CrashReportCategory crashReportCategory = crashReport.addCategory("Block entity being ticked");
                        this.blockEntity.fillCrashReportCategory(crashReportCategory);
                        throw new ReportedException(crashReport);
                    }
                }
            }

        }

        public boolean isRemoved() {
            return this.blockEntity.isRemoved();
        }

        public BlockPos getPos() {
            return this.blockEntity.getBlockPos();
        }

        public String getType() {
            return BlockEntityType.getKey(this.blockEntity.getType()).toString();
        }

        public String toString() {
            return "Level ticker for " + this.getType() + "@" + this.getPos();
        }
    }

}