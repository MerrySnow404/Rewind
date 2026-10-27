package cc.sighs.rewind.client;

import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;

public final class RagdollHandler {
    private static final String MOD_ID = "ragdollified";
    private static final String HANDLER = "com.raiiiden.ragdollified.client.ClientRagdollManager";

    private RagdollHandler() {
    }

    public static void clear() {
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            Class<?> handler = Class.forName(HANDLER, false, RagdollHandler.class.getClassLoader());
            Method clear = handler.getDeclaredMethod("clear");
            clear.setAccessible(true);
            clear.invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
