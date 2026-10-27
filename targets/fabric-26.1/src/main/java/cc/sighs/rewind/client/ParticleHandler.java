package cc.sighs.rewind.client;

import cc.sighs.mixin.ClientVisualMixins.ParticleEngineAccess;
import cc.sighs.mixin.ClientVisualMixins.ParticleEngineInvoker;
import net.minecraft.client.Minecraft;

public final class ParticleHandler {
    private ParticleHandler() {
    }

    public static void clear() {
        ParticleEngineAccess access = (ParticleEngineAccess) (Object) Minecraft.getInstance();
        ParticleEngineInvoker particles = (ParticleEngineInvoker) (Object) access.rewind$particleEngine();
        particles.rewind$clearParticles();
    }
}
