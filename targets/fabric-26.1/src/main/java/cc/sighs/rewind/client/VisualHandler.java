package cc.sighs.rewind.client;

public final class VisualHandler {
    private VisualHandler() {
    }

    public static void clear() {
        ParticleHandler.clear();
        RagdollHandler.clear();
    }
}
