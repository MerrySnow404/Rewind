package cc.sighs.rewind.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ClearVisualNetwork {
    public static final CustomPacketPayload.Type<ClearVisualPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("rewind", "clear_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClearVisualPayload> CODEC =
            StreamCodec.unit(new ClearVisualPayload());

    private ClearVisualNetwork() {
    }

    public static void registerServer() {
        PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.client().execute(ClearVisualNetwork::invokeClientClear));
    }

    public static void send(ServerPlayer player) {
        ServerPlayNetworking.send(player, new ClearVisualPayload());
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

    public record ClearVisualPayload() implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
