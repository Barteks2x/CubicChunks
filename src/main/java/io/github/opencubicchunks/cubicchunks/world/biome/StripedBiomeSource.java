package io.github.opencubicchunks.cubicchunks.world.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryLookupCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

public class StripedBiomeSource extends BiomeSource {

    public static final Codec<StripedBiomeSource> CODEC = RecordCodecBuilder.create(
        (instance) -> instance.group(RegistryLookupCodec.create(Registry.BIOME_REGISTRY).forGetter((theEndBiomeSource) -> theEndBiomeSource.biomeRegistry))
            .apply(instance, instance.stable(StripedBiomeSource::new)));

    private final Registry<Biome> biomeRegistry;

    private final Biome[] biomeArray;

    public StripedBiomeSource(Registry<Biome> registry) {
        super(registry.stream().map(x -> () -> x));
        this.biomeRegistry = registry;
        this.biomeArray =
            registry.stream().filter(biome -> biome.getBiomeCategory() != Biome.BiomeCategory.NETHER && biome.getBiomeCategory() != Biome.BiomeCategory.THEEND).toArray(Biome[]::new);
    }

    @Override
    protected Codec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    public BiomeSource withSeed(long l) {
        return new StripedBiomeSource(this.biomeRegistry);
    }

    @Override
    public Biome getNoiseBiome(int x, int y, int z) {
        return biomeArray[Math.floorMod(Math.floorDiv(x, 160), biomeArray.length)];
    }
}
