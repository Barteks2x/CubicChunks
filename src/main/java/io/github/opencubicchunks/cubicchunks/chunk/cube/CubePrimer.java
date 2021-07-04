package io.github.opencubicchunks.cubicchunks.chunk.cube;

import static net.minecraft.world.level.chunk.LevelChunk.EMPTY_SECTION;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.opencubicchunks.cubicchunks.chunk.CubeMapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.IBigCube;
import io.github.opencubicchunks.cubicchunks.chunk.ImposterChunkPos;
import io.github.opencubicchunks.cubicchunks.chunk.LightHeightmapGetter;
import io.github.opencubicchunks.cubicchunks.chunk.biome.CubeBiomeContainer;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerSection;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.LightSurfaceTrackerWrapper;
import io.github.opencubicchunks.cubicchunks.chunk.heightmap.SurfaceTrackerSection;
import io.github.opencubicchunks.cubicchunks.chunk.util.CubePos;
import io.github.opencubicchunks.cubicchunks.mixin.access.common.BiomeContainerAccess;
import io.github.opencubicchunks.cubicchunks.server.CubicLevelHeightAccessor;
import io.github.opencubicchunks.cubicchunks.utils.Coords;
import io.github.opencubicchunks.cubicchunks.world.CubeWorldGenRegion;
import io.github.opencubicchunks.cubicchunks.world.lighting.ISkyLightColumnChecker;
import io.github.opencubicchunks.cubicchunks.world.storage.CubeProtoTickList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkBiomeContainer;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ProtoTickList;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

//ProtoChunk
public class CubePrimer extends ProtoChunk implements IBigCube, CubicLevelHeightAccessor {

    private static final BlockState EMPTY_BLOCK = Blocks.AIR.defaultBlockState();
    private static final FluidState EMPTY_FLUID = Fluids.EMPTY.defaultFluidState();

    private final CubePos cubePos;
    private final LevelChunkSection[] sections;
    private final LevelHeightAccessor levelHeightAccessor;
    private ChunkStatus status = ChunkStatus.EMPTY;

    @Nullable
    private CubeBiomeContainer cubeBiomeContainer;

    private final Map<Heightmap.Types, SurfaceTrackerSection[]> heightmaps;

    private final LightSurfaceTrackerSection[] lightHeightmaps = new LightSurfaceTrackerSection[IBigCube.DIAMETER_IN_SECTIONS * IBigCube.DIAMETER_IN_SECTIONS];


    private final List<CompoundTag> entities = Lists.newArrayList();
    private final Map<BlockPos, BlockEntity> tileEntities = Maps.newHashMap();
    private final Map<BlockPos, CompoundTag> deferredTileEntities = Maps.newHashMap();

    //Structures
    private final Map<StructureFeature<?>, StructureStart<?>> structureStarts;
    private final Map<StructureFeature<?>, LongSet> structuresRefences;
    private final Map<GenerationStep.Carving, BitSet> carvingMasks;
    private final Map<BlockPos, BlockState> featuresStateMap = new HashMap<>();

    private volatile boolean isDirty;

    private volatile boolean modified = true;

    private final List<BlockPos> lightPositions = Lists.newArrayList();
    private volatile boolean hasLight;
    private LevelLightEngine lightManager;

    private long inhabitedTime;

    private final boolean isCubic;
    private final boolean generates2DChunks;
    private final WorldStyle worldStyle;

    private int columnX;
    private int columnZ;

    private int minBuildHeight;
    private int height;

    public CubePrimer(CubePos cubePos, UpgradeData upgradeData, LevelHeightAccessor levelHeightAccessor) {
        this(cubePos, upgradeData, null, new CubeProtoTickList<>((block) -> {
            return block == null || block.defaultBlockState().isAir();
        }, new ImposterChunkPos(cubePos), new CubeProtoTickList.CubeProtoTickListHeightAccess(cubePos, (CubicLevelHeightAccessor) levelHeightAccessor)), new CubeProtoTickList<>((fluid) -> {
            return fluid == null || fluid == Fluids.EMPTY;
        }, new ImposterChunkPos(cubePos), new CubeProtoTickList.CubeProtoTickListHeightAccess(cubePos, (CubicLevelHeightAccessor) levelHeightAccessor)), levelHeightAccessor);
    }

    public CubePrimer(CubePos cubePosIn, UpgradeData upgradeData, @Nullable LevelChunkSection[] sectionsIn, ProtoTickList<Block> blockProtoTickList, ProtoTickList<Fluid> fluidProtoTickList,
                      LevelHeightAccessor levelHeightAccessor) {
        super(cubePosIn.asChunkPos(), upgradeData, sectionsIn, blockProtoTickList, fluidProtoTickList, new FakeSectionCount(levelHeightAccessor, IBigCube.SECTION_COUNT));

        this.heightmaps = Maps.newEnumMap(Heightmap.Types.class);
        this.carvingMasks = new Object2ObjectArrayMap<>();

        this.structureStarts = Maps.newHashMap();
        this.structuresRefences = Maps.newHashMap();

        this.cubePos = cubePosIn;
        this.levelHeightAccessor = levelHeightAccessor;

        if (sectionsIn == null) {
            this.sections = new LevelChunkSection[IBigCube.SECTION_COUNT];
        } else {
            if (sectionsIn.length == IBigCube.SECTION_COUNT) {
                this.sections = sectionsIn;
            } else {
                throw new IllegalStateException("Number of Sections must equal IBigCube.CUBESIZE | " + IBigCube.SECTION_COUNT);
            }
        }
        isCubic = ((CubicLevelHeightAccessor) levelHeightAccessor).isCubic();
        generates2DChunks = ((CubicLevelHeightAccessor) levelHeightAccessor).generates2DChunks();
        worldStyle = ((CubicLevelHeightAccessor) levelHeightAccessor).worldStyle();

        this.minBuildHeight = levelHeightAccessor.getMinBuildHeight();
        this.height = levelHeightAccessor.getHeight();
    }

    public void moveColumns(int newColumnX, int newColumnZ) {
        this.columnX = newColumnX;
        this.columnZ = newColumnZ;
    }

    public void setHeightToCubeBounds(boolean cubeBounds) {
        if (cubeBounds) {
            this.minBuildHeight = this.cubePos.minCubeY();
            this.height = IBigCube.DIAMETER_IN_BLOCKS;
        } else {
            this.minBuildHeight = this.levelHeightAccessor.getMinBuildHeight();
            this.height = this.levelHeightAccessor.getHeight();
        }
    }

    @Override public CubePos getCubePos() {
        return this.cubePos;
    }

    @Override public LevelChunkSection[] getCubeSections() {
        return this.sections;
    }

    private ChunkSource getChunkSource() {
        if (this.levelHeightAccessor instanceof CubeWorldGenRegion) {
            return ((CubeWorldGenRegion) this.levelHeightAccessor).getChunkSource();
        } else {
            return ((ServerLevel) this.levelHeightAccessor).getChunkSource();
        }
    }

    //STATUS
    public void setCubeStatus(ChunkStatus newStatus) {
        this.status = newStatus;

        if (this.status == ChunkStatus.FEATURES) {
            ChunkSource chunkSource = getChunkSource();

            for (int dx = 0; dx < IBigCube.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < IBigCube.DIAMETER_IN_SECTIONS; dz++) {

                    // get the chunk for this section
                    ChunkPos chunkPos = this.cubePos.asChunkPos(dx, dz);
                    ChunkAccess chunk = chunkSource.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.EMPTY, false);

                    // the load order guarantees the chunk being present
                    assert (chunk != null && chunk.getStatus().isOrAfter(ChunkStatus.FEATURES));

                    ((CubeMapGetter) chunk).getCubeMap().markLoaded(this.cubePos.getY());

                    LightSurfaceTrackerWrapper lightHeightmap = ((LightHeightmapGetter) chunk).getServerLightHeightmap();

                    int[] beforeValues = new int[SECTION_DIAMETER * SECTION_DIAMETER];
                    for (int z = 0; z < SECTION_DIAMETER; z++) {
                        for (int x = 0; x < SECTION_DIAMETER; x++) {
                            beforeValues[z * SECTION_DIAMETER + x] = lightHeightmap.getFirstAvailable(x, z);
                        }
                    }

                    lightHeightmap.loadCube(this);

                    for (int z = 0; z < SECTION_DIAMETER; z++) {
                        for (int x = 0; x < SECTION_DIAMETER; x++) {
                            int beforeValue = beforeValues[z * SECTION_DIAMETER + x];
                            int afterValue = lightHeightmap.getFirstAvailable(x, z);
                            if (beforeValue != afterValue) {
                                ((ISkyLightColumnChecker) chunkSource.getLightEngine()).checkSkyLightColumn((CubeMapGetter) chunk,
                                        chunkPos.getBlockX(x), chunkPos.getBlockZ(z), beforeValue, afterValue);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setLightHeightmapSection(LightSurfaceTrackerSection section, int localSectionX, int localSectionZ) {
        int idx = localSectionX + localSectionZ * DIAMETER_IN_SECTIONS;
        this.lightHeightmaps[idx] = section;
    }

    public LightSurfaceTrackerSection[] getLightHeightmaps() {
        return lightHeightmaps;
    }

    @Override public ChunkStatus getCubeStatus() {
        return this.status;
    }

    @Override @Nullable public BlockState setBlock(BlockPos pos, BlockState state, boolean isMoving) {
        int xSection = pos.getX() & 0xF;
        int ySection = pos.getY() & 0xF;
        int zSection = pos.getZ() & 0xF;
        int sectionIdx = Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ());

        LevelChunkSection section = this.sections[sectionIdx];
        if (section == EMPTY_SECTION && state == EMPTY_BLOCK) {
            return state;
        }

        if (section == EMPTY_SECTION) {
            section = new LevelChunkSection(Coords.cubeToMinBlock(this.cubePos.getY() + Coords.sectionToMinBlock(Coords.indexToY(sectionIdx))));
            this.sections[sectionIdx] = section;
        }

        if (state.getLightEmission() > 0) {
            SectionPos sectionPosAtIndex = Coords.sectionPosByIndex(this.cubePos, sectionIdx);
            this.lightPositions.add(new BlockPos(
                    xSection + Coords.sectionToMinBlock(sectionPosAtIndex.getX()),
                    ySection + Coords.sectionToMinBlock(sectionPosAtIndex.getY()),
                    zSection + Coords.sectionToMinBlock(sectionPosAtIndex.getZ()))
            );
        }

        BlockState lastState = section.setBlockState(xSection, ySection, zSection, state, false);
        if (this.status.isOrAfter(ChunkStatus.LIGHT) && state != lastState && (state.getLightBlock(this, pos) != lastState.getLightBlock(this, pos)
                || state.getLightEmission() != lastState.getLightEmission() || state.useShapeForLightOcclusion() || lastState.useShapeForLightOcclusion())) {

            // get the chunk containing the updated block
            ChunkSource chunkSource = getChunkSource();
            ChunkPos chunkPos = Coords.chunkPosByIndex(this.cubePos, sectionIdx);
            BlockGetter chunk = chunkSource.getChunkForLighting(chunkPos.x, chunkPos.z);

            // the load order guarantees the chunk being present
            assert (chunk != null);

            LightSurfaceTrackerWrapper lightHeightmap = ((LightHeightmapGetter) chunk).getServerLightHeightmap();

            int relX = pos.getX() & 15;
            int relZ = pos.getZ() & 15;
            int oldHeight = lightHeightmap.getFirstAvailable(relX, relZ);
            // Light heightmap update needs to occur before the light engine update.
            // Not sure if this is the right blockstate to pass in, but it doesn't actually matter since we don't use it
            lightHeightmap.update(relX, pos.getY(), relZ, state);
            int newHeight = lightHeightmap.getFirstAvailable(relX, relZ);
            if (newHeight != oldHeight) {
                ((ISkyLightColumnChecker) chunkSource.getLightEngine()).checkSkyLightColumn((CubeMapGetter) chunk, pos.getX(), pos.getZ(), oldHeight, newHeight);
            }

            lightManager.checkBlock(pos);
        }

        EnumSet<Heightmap.Types> heightMapsAfter = this.getStatus().heightmapsAfter();

        int xChunk = Coords.blockToCubeLocalSection(pos.getX());
        int zChunk = Coords.blockToCubeLocalSection(pos.getZ());
        int chunkIdx = xChunk + zChunk * DIAMETER_IN_SECTIONS;

        for (Heightmap.Types types : heightMapsAfter) {
            SurfaceTrackerSection surfaceTrackerSection = getHeightmapSections(types)[chunkIdx];
            surfaceTrackerSection.onSetBlock(xSection, pos.getY(), zSection, state);
        }

        return lastState;
    }

    /**
     * Gets the SurfaceTrackerSections for the given Heightmap.Types for all chunks of this cube.
     * Lazily initializes new SurfaceTrackerSections.
     */
    private SurfaceTrackerSection[] getHeightmapSections(Heightmap.Types type) {

        SurfaceTrackerSection[] surfaceTrackerSections = heightmaps.get(type);

        if (surfaceTrackerSections == null) {
            surfaceTrackerSections = new SurfaceTrackerSection[IBigCube.DIAMETER_IN_SECTIONS * IBigCube.DIAMETER_IN_SECTIONS];

            for (int dx = 0; dx < IBigCube.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < IBigCube.DIAMETER_IN_SECTIONS; dz++) {
                    int idx = dx + dz * IBigCube.DIAMETER_IN_SECTIONS;
                    surfaceTrackerSections[idx] = new SurfaceTrackerSection(0, cubePos.getY(), null, this, type);
                    surfaceTrackerSections[idx].loadCube(dx, dz, this, true);
                }
            }

            heightmaps.put(type, surfaceTrackerSections);
        }

        return surfaceTrackerSections;
    }

    @Override public void setFeatureBlocks(BlockPos pos, BlockState state) {
       featuresStateMap.put(pos.immutable(), state);
    }

    public void applyFeatureStates() {
        featuresStateMap.forEach((pos, state) -> {
            setBlock(pos, state, false);
        });
    }

    public Map<BlockPos, BlockState> getFeaturesStateMap() {
        return featuresStateMap;
    }

    private void primeHeightMaps(EnumSet<Heightmap.Types> toInitialize) {
        for (Heightmap.Types type : toInitialize) {
            SurfaceTrackerSection[] surfaceTrackerSections = new SurfaceTrackerSection[IBigCube.DIAMETER_IN_SECTIONS * IBigCube.DIAMETER_IN_SECTIONS];

            for (int dx = 0; dx < IBigCube.DIAMETER_IN_SECTIONS; dx++) {
                for (int dz = 0; dz < IBigCube.DIAMETER_IN_SECTIONS; dz++) {
                    int idx = dx + dz * IBigCube.DIAMETER_IN_SECTIONS;
                    surfaceTrackerSections[idx] = new SurfaceTrackerSection(0, cubePos.getY(), null, this, type);
                    surfaceTrackerSections[idx].loadCube(dx, dz, this, true);
                }
            }
            this.heightmaps.put(type, surfaceTrackerSections);
        }
    }

    public void addCubeEntity(Entity entityIn) {
        CompoundTag compoundnbt = new CompoundTag();
        entityIn.save(compoundnbt);
        this.addCubeEntity(compoundnbt);
    }

    public void addCubeEntity(CompoundTag entityCompound) {
        this.entities.add(entityCompound);
    }

    public List<CompoundTag> getCubeEntities() {
        return this.entities;
    }

    @Override public void setCubeBlockEntity(CompoundTag nbt) {
        this.deferredTileEntities.put(new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z")), nbt);
    }

    @Override public void setCubeBlockEntity(BlockEntity tileEntityIn) {
        this.tileEntities.put(tileEntityIn.getBlockPos(), tileEntityIn);
    }

    @Override public void removeCubeBlockEntity(BlockPos pos) {
        this.tileEntities.remove(pos);
        this.deferredTileEntities.remove(pos);
    }

    @Nullable @Override public BlockEntity getBlockEntity(BlockPos pos) {
        return this.tileEntities.get(pos);
    }

    @Override public Set<BlockPos> getCubeTileEntitiesPos() {
        Set<BlockPos> set = Sets.newHashSet(this.deferredTileEntities.keySet());
        set.addAll(this.tileEntities.keySet());
        return set;
    }

    @Nullable @Override public CompoundTag getCubeBlockEntityNbtForSaving(BlockPos pos) {
        BlockEntity tileEntity = this.getBlockEntity(pos);
        return tileEntity != null ? tileEntity.save(new CompoundTag()) : this.deferredTileEntities.get(pos);
    }

    @Nullable @Override public CompoundTag getCubeDeferredTileEntity(BlockPos pos) {
        return this.deferredTileEntities.get(pos);
    }

    public Map<BlockPos, BlockEntity> getCubeTileEntities() {
        return this.tileEntities;
    }

    public Map<BlockPos, CompoundTag> getDeferredTileEntities() {
        return Collections.unmodifiableMap(this.deferredTileEntities);
    }

    @Override public boolean hasCubeLight() {
        return this.hasLight;
    }

    @Override public void setCubeLight(boolean lightCorrectIn) {
        this.hasLight = lightCorrectIn;
        this.setDirty(true);
    }

    @Override public Stream<BlockPos> getCubeLightSources() {
        return this.lightPositions.stream();
    }

    public void addCubeLightValue(short packedPosition, int yOffset) {
        this.addCubeLightPosition(unpackToWorld(packedPosition, yOffset, this.cubePos));
    }

    public void addCubeLightPosition(BlockPos lightPos) {
        this.lightPositions.add(lightPos.immutable());
    }

    public void setCubeLightManager(LevelLightEngine newLightEngine) {
        this.lightManager = newLightEngine;
    }

    @Nullable private LevelLightEngine getCubeWorldLightManager() {
        return this.lightManager;
    }

    @Override public void setDirty(boolean newUnsaved) {
        this.modified = newUnsaved;
    }

    @Override public boolean isDirty() {
        return modified;
    }

    @Override public boolean isEmptyCube() {
        for (LevelChunkSection section : this.sections) {
            if (!LevelChunkSection.isEmpty(section)) {
                return false;
            }
        }
        return true;
    }

    @Override public void setCubeInhabitedTime(long newInhabitedTime) {
        this.inhabitedTime = newInhabitedTime;
    }

    @Override public long getCubeInhabitedTime() {
        return this.inhabitedTime;
    }


    @Override public FluidState getFluidState(BlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int index = Coords.blockToIndex(x, y, z);
        LevelChunkSection section = this.sections[index];
        if (!LevelChunkSection.isEmpty(section)) {
            return section.getFluidState(x & 15, y & 15, z & 15);
        } else {
            return EMPTY_FLUID;
        }
    }

    @Override public int getCubeLocalHeight(Heightmap.Types type, int x, int z) {
        SurfaceTrackerSection[] surfaceTrackerSections = getHeightmapSections(type);
        if (surfaceTrackerSections == null) {
            primeHeightMaps(EnumSet.of(type));
            surfaceTrackerSections = this.heightmaps.get(type);
        }
        int xSection = Coords.blockToCubeLocalSection(x);
        int zSection = Coords.blockToCubeLocalSection(z);

        int idx = xSection + zSection * DIAMETER_IN_SECTIONS;

        SurfaceTrackerSection surfaceTrackerSection = surfaceTrackerSections[idx];
        return surfaceTrackerSection.getHeight(Coords.blockToLocal(x), Coords.blockToLocal(z));
    }

    @Override public int getHeight(Heightmap.Types types, int x, int z) {
        return getCubeLocalHeight(types, x, z);
    }

    @org.jetbrains.annotations.Nullable
    public StructureStart<?> getStartForFeature(StructureFeature<?> structureFeature) {
        return this.structureStarts.get(structureFeature);
    }

    @Override
    public void setStartForFeature(StructureFeature<?> structureFeature, StructureStart<?> structureStart) {
        this.structureStarts.put(structureFeature, structureStart);
        this.isDirty = true;
    }

    @Override
    public Map<StructureFeature<?>, StructureStart<?>> getAllCubeStructureStarts() {
        return Collections.unmodifiableMap(this.structureStarts);
    }

    @Override
    public LongSet getReferencesForFeature(StructureFeature<?> structureFeature) {
        return this.structuresRefences.computeIfAbsent(structureFeature, (structureFeaturex) -> {
            return new LongOpenHashSet();
        });
    }

    @Override
    public void addReferenceForFeature(StructureFeature<?> structureFeature, long l) {
        this.structuresRefences.computeIfAbsent(structureFeature, (structureFeaturex) -> {
            return new LongOpenHashSet();
        }).add(l);
        this.isDirty = true;
    }

    public Map<StructureFeature<?>, LongSet> getAllReferences() {
        return Collections.unmodifiableMap(this.structuresRefences);
    }

    public static BlockPos unpackToWorld(short sectionRel, int sectionIdx, CubePos cubePosIn) {
        BlockPos pos = Coords.sectionPosToMinBlockPos(Coords.sectionPosByIndex(cubePosIn, sectionIdx));
        int xPos = (sectionRel & 15) + pos.getX();
        int yPos = (sectionRel >>> 4 & 15) + pos.getY();
        int zPos = (sectionRel >>> 8 & 15) + pos.getZ();
        return new BlockPos(xPos, yPos, zPos);
    }

    @Override
    public void setAllReferences(Map<StructureFeature<?>, LongSet> map) {
        this.structuresRefences.clear();
        this.structuresRefences.putAll(map);
        this.isDirty = true;
    }

    @Override public BlockState getBlockState(BlockPos pos) {
        return this.getBlockState(pos.getX(), pos.getY(), pos.getZ());
    }

    // Called from CubeSerializer
    public void setCubeBiomeContainer(CubeBiomeContainer container) {
        this.cubeBiomeContainer = container;
    }

    /*****ChunkPrimer Overrides*****/

    @Override public ShortList[] getPackedLights() {
        return super.getPackedLights();
    }


    @Override public void setBiomes(ChunkBiomeContainer biomes) {
        if (cubeBiomeContainer == null) {
            cubeBiomeContainer = new CubeBiomeContainer(((BiomeContainerAccess) biomes).getBiomeRegistry(), this.levelHeightAccessor);
        }

        cubeBiomeContainer.setContainerForColumn(columnX, columnZ, biomes);
    }

    @Nullable @Override public ChunkBiomeContainer getBiomes() {
        return this.cubeBiomeContainer;
    }

    @Override public void addLight(short chunkSliceRel, int sectionY) {
        this.addCubeLightValue(chunkSliceRel, Coords.sectionToIndex(columnX, sectionY, columnZ));
    }

    @Override public void addLight(BlockPos pos) {
        this.addCubeLightPosition(pos);
    }

    @Override public void addEntity(CompoundTag entityTag) {
        this.addCubeEntity(entityTag);
    }

    @Override public List<CompoundTag> getEntities() {
        return this.getCubeEntities();
    }

    @Override public void setStatus(ChunkStatus status) {
        this.setCubeStatus(status);
    }

    @Override public Map<BlockPos, CompoundTag> getBlockEntityNbts() {
        return this.deferredTileEntities;
    }

    @Override public void setLightEngine(LevelLightEngine lightingProvider) {
        this.setCubeLightManager(lightingProvider);
    }

    @Nullable
    @Override
    public BitSet getCarvingMask(GenerationStep.Carving type) {
        return this.carvingMasks.get(type);
    }

    @Override public BitSet getOrCreateCarvingMask(GenerationStep.Carving type) {
        return this.carvingMasks.computeIfAbsent(type, (carvingx) -> {
            return new BitSet(IBigCube.BLOCK_COUNT);
        });
    }

    @Override
    public void setCarvingMask(GenerationStep.Carving type, BitSet mask) {
        this.carvingMasks.put(type, mask);
    }

    @Override
    public void markPosForPostprocessing(BlockPos pos) {
        if (!this.isOutsideBuildHeight(pos)) {
            ChunkAccess.getOrCreateOffsetList(this.getPostProcessing(), Coords.blockToIndex(pos.getX(), pos.getY(), pos.getZ())).add(packOffsetCoordinates(pos));
        }
    }

    @Override public UpgradeData getUpgradeData() {
        throw new UnsupportedOperationException("For later implementation");
    }

    @Override
    public Map<StructureFeature<?>, StructureStart<?>> getAllStarts() {
        return getAllCubeStructureStarts();
    }

    @Override
    public void setAllStarts(Map<StructureFeature<?>, StructureStart<?>> map) {
        this.structureStarts.clear();
        this.structureStarts.putAll(map);
        this.isDirty = true;
    }

    @Override public Collection<Map.Entry<Heightmap.Types, Heightmap>> getHeightmaps() {
        throw new UnsupportedOperationException("For later implementation");
    }

    @Override public void setHeightmap(Heightmap.Types type, long[] data) {
        throw new UnsupportedOperationException("For later implementation");
    }

    @Override public Heightmap getOrCreateHeightmapUnprimed(Heightmap.Types typeIn) {
        throw new UnsupportedOperationException("For later implementation");
    }

    @Deprecated @Override public long getInhabitedTime() {
        return this.getCubeInhabitedTime();
    }

    @Override public void setInhabitedTime(long newInhabitedTime) {
        this.setCubeInhabitedTime(newInhabitedTime);
    }

    @Deprecated @Override public boolean isUnsaved() {
        return isDirty();
    }

    //MISC
    @Deprecated @Override public void setUnsaved(boolean newUnsaved) {
        setDirty(newUnsaved);
    }

    @Deprecated @Override public Stream<BlockPos> getLights() {
        return getCubeLightSources();
    }

    @Deprecated @Override public void setLightCorrect(boolean lightCorrectIn) {
        throw new UnsupportedOperationException("Chunk method called on a cube!");
    }

    //LIGHTING
    @Deprecated @Override public boolean isLightCorrect() {
        throw new UnsupportedOperationException("Chunk method called on a cube!");
    }

    public Map<BlockPos, BlockEntity> getBlockEntities() {
        return this.getCubeTileEntities();
    }

    @Deprecated @Nullable @Override public CompoundTag getBlockEntityNbt(BlockPos pos) {
        return this.getCubeDeferredTileEntity(pos);
    }

    @Deprecated @Override public Set<BlockPos> getBlockEntitiesPos() {
        return this.getCubeTileEntitiesPos();
    }

    @Deprecated @Nullable @Override public CompoundTag getBlockEntityNbtForSaving(BlockPos pos) {
        return this.getCubeBlockEntityNbtForSaving(pos);
    }

    @Deprecated @Override public void removeBlockEntity(BlockPos pos) {
        this.removeCubeBlockEntity(pos);
    }

    @Deprecated @Override public void setBlockEntity(BlockEntity tileEntityIn) {
        this.setCubeBlockEntity(tileEntityIn);
    }

    @Deprecated @Override public void setBlockEntityNbt(CompoundTag nbt) {
        this.setCubeBlockEntity(nbt);
    }

    @Override public BlockState getBlockState(int x, int y, int z) {
        int index = Coords.blockToIndex(x, y, z);
        LevelChunkSection section = this.sections[index];
        return LevelChunkSection.isEmpty(section) ? EMPTY_BLOCK : section.getBlockState(x & 15, y & 15, z & 15);
    }

    //ENTITY
    @Deprecated @Override public void addEntity(Entity entityIn) {
        this.addCubeEntity(entityIn);
    }

    //BLOCK
    @Deprecated @Nullable @Override public BlockState setBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        return setBlock(pos, state, isMoving);
    }

    @Deprecated @Override public ChunkStatus getStatus() {
        return getCubeStatus();
    }

    @Deprecated @Override public LevelChunkSection[] getSections() {
        return getCubeSections();
    }

    @Override public BlockPos getHeighestPosition(Heightmap.Types type) {
        BlockPos.MutableBlockPos mutableBlockPos = null;

        for (int x = this.cubePos.minCubeX(); x <= this.cubePos.maxCubeX(); ++x) {
            for (int z = this.cubePos.minCubeZ(); z <= this.cubePos.maxCubeZ(); ++z) {
                int heightAtPos = this.getHeight(type, x & 15, z & 15);
                if (mutableBlockPos == null) {
                    mutableBlockPos = new BlockPos.MutableBlockPos().set(x, heightAtPos, z);
                }

                if (mutableBlockPos.getY() < heightAtPos) {
                    mutableBlockPos.set(x, heightAtPos, z);
                }
            }
        }
        return mutableBlockPos != null ? mutableBlockPos.immutable() : new BlockPos.MutableBlockPos().set(this.cubePos.minCubeX(), this.getHeight(type, this.cubePos.minCubeX() & 15,
            this.cubePos.minCubeZ() & 15), this.cubePos.minCubeZ() & 15);

    }

    @Deprecated @Override public ChunkPos getPos() {
        return this.cubePos.asChunkPos(columnX, columnZ);
    }

    @Override public int getHeight() {
        return this.height;
    }

    @Override public int getMinBuildHeight() {
        return this.minBuildHeight;
    }

    @Override public WorldStyle worldStyle() {
        return worldStyle;
    }

    @Override public boolean isCubic() {
        return isCubic;
    }

    @Override public boolean generates2DChunks() {
        return generates2DChunks;
    }

    public static class FakeSectionCount implements LevelHeightAccessor, CubicLevelHeightAccessor {
        private final int height;
        private final int minHeight;
        private final int fakeSectionCount;
        private final boolean isCubic;
        private final boolean generates2DChunks;
        private final WorldStyle worldStyle;

        public FakeSectionCount(LevelHeightAccessor levelHeightAccessor, int sectionCount) {
            this(levelHeightAccessor.getHeight(), levelHeightAccessor.getMinBuildHeight(), sectionCount, ((CubicLevelHeightAccessor) levelHeightAccessor).isCubic(),
                ((CubicLevelHeightAccessor) levelHeightAccessor).generates2DChunks(), ((CubicLevelHeightAccessor) levelHeightAccessor).worldStyle());
        }

        private FakeSectionCount(int height, int minHeight, int sectionCount, boolean isCubic, boolean generates2DChunks, WorldStyle worldStyle) {
            this.height = height;
            this.minHeight = minHeight;
            this.fakeSectionCount = sectionCount;
            this.isCubic = isCubic;
            this.generates2DChunks = generates2DChunks;
            this.worldStyle = worldStyle;
        }

        @Override public int getHeight() {
            return this.height;
        }

        @Override public int getMinBuildHeight() {
            return this.minHeight;
        }

        @Override public int getSectionsCount() {
            return this.fakeSectionCount;
        }

        @Override public WorldStyle worldStyle() {
            return worldStyle;
        }

        @Override public boolean isCubic() {
            return isCubic;
        }

        @Override public boolean generates2DChunks() {
            return generates2DChunks;
        }
    }
}
