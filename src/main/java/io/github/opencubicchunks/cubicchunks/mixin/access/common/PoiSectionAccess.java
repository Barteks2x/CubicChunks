package io.github.opencubicchunks.cubicchunks.mixin.access.common;

import net.minecraft.world.entity.ai.village.poi.PoiSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PoiSection.class)
public interface PoiSectionAccess {

    @Invoker
    boolean invokeIsValid();
}
