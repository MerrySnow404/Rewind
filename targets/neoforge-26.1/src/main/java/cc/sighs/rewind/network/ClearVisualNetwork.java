package cc.sighs.rewind.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class ClearVisualNetwork {
    public static final CustomPacketPayload.Type<ClearVisualPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("rewind", "clear_visual"));
    public static final StreamCodec<ByteBuf, ClearVisualPayload> CODEC = StreamCodec.unit(new ClearVisualPayload());

    private ClearVisualNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, CODEC, (payload, context) ->
                context.enqueueWork(() -> invokeClientClear()));
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ClearVisualPayload());
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
