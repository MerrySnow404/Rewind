package cc.sighs.rewind.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ClearVisualNetwork {
    public static final ResourceLocation ID = new ResourceLocation("rewind", "clear_visual");

    private ClearVisualNetwork() {
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ID, (client, handler, buffer, responseSender) ->
                client.execute(ClearVisualNetwork::invokeClientClear));
    }

    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, ID, PacketByteBufs.empty());
    }

    private static void invokeClientClear() {
        try {
            Class<?> config = Class.forName("cc.sighs.rewind.client.RewindClientConfig");
            if (!((Boolean) config.getMethod("clearVisual").invoke(null))) {
                return;
            }
            Class.forName("cc.sighs.rewind.client.VisualHandler").getMethod("clear").invoke(null);
        } catch (Throwable ignored) {
        }
    }
}
