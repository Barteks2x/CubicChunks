package io.github.opencubicchunks.cubicchunks.mixin;

import io.github.opencubicchunks.cubicchunks.mixin.transform.MainTransformer;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

public class ASMConfigPlugin implements IMixinConfigPlugin {

    @Override public void onLoad(String mixinPackage) {

    }

    @Override public String getRefMapperConfig() {
        return null;
    }

    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Nullable
    @Override public List<String> getMixins() {
        return null;
    }

    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (targetClassName.equals("net.minecraft.world.server.ChunkManager$ProxyTicketManager")) {
            MainTransformer.transformProxyTicketManager(targetClass);
        } else if (targetClassName.equals("net.minecraft.world.server.ChunkManager")) {
            MainTransformer.transformChunkManager(targetClass);
        } else if (targetClassName.equals("net.minecraft.world.server.ChunkHolder")) {
            MainTransformer.transformChunkHolder(targetClass);
        }
    }

    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}