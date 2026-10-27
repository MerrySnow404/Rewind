package cc.sighs.mixin;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

public final class ClientVisualMixins {
    private ClientVisualMixins() {
    }
    @Mixin(net.minecraft.client.Minecraft.class)
    public interface ParticleEngineAccess {
        @Accessor("particleEngine")
        ParticleEngine rewind$particleEngine();
    }

    @Mixin(ParticleEngine.class)
    public interface ParticleEngineInvoker {
        @Invoker("clearParticles")
        void rewind$clearParticles();
    }
}
