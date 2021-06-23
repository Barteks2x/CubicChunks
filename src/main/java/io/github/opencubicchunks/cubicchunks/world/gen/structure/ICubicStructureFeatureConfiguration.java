package io.github.opencubicchunks.cubicchunks.world.gen.structure;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface ICubicStructureFeatureConfiguration {

    Optional<CubicStructureConfiguration> getVerticalSettings();

    void setVerticalSettings(Optional<CubicStructureConfiguration> cubicStructureConfiguration);
}
