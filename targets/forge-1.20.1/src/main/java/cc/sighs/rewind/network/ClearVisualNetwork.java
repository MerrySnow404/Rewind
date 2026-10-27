package cc.sighs.rewind.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ClearVisualNetwork {
    private static final ResourceLocation ID = new ResourceLocation("rewind", "clear_visual");
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ID, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ClearVisualNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, ClearVisualPacket.class,
                ClearVisualPacket::encode, ClearVisualPacket::decode, ClearVisualPacket::handle);
    }

    public static void send(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClearVisualPacket());
    }

    private static final class ClearVisualPacket {
        private static void encode(ClearVisualPacket packet, FriendlyByteBuf buffer) {
        }

        private static ClearVisualPacket decode(FriendlyByteBuf buffer) {
            return new ClearVisualPacket();
        }

        private static void handle(ClearVisualPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> {
                try {
                    Class<?> config = Class.forName("cc.sighs.rewind.client.RewindClientConfig");
                    if (!((Boolean) config.getMethod("clearVisual").invoke(null))) {
                        return;
                    }
                    Class.forName("cc.sighs.rewind.client.VisualHandler")
                            .getMethod("clear")
                            .invoke(null);
                } catch (Throwable ignored) {
                }
            });
            context.setPacketHandled(true);
        }
    }
}
