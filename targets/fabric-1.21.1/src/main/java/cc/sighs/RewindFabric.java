package cc.sighs;

import cc.sighs.rewind.client.RewindCommands;
import cc.sighs.rewind.common.spi.RewindPlatforms;
import cc.sighs.rewind.network.ClearVisualNetwork;
import cc.sighs.rewind.server.FabricRewindPlatform;
import cc.sighs.rewind.server.RewindServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric 1.21.1 的入口。
 *
 * <p>装平台实现（common 侧所有需要碰 Minecraft 的能力都经过它）与读服务端配置。客户端入口在
 * {@code cc.sighs.RewindFabricClient}（{@code ClientModInitializer}），本类不引用任何
 * {@code net.minecraft.client.*}，所以专用服务器上也能加载。
 *
 * <p>命令也在这里注册（{@code CommandRegistrationCallback} 在模组初始化期由加载器调用，单人的集成
 * 服务器与专用服务器都会走一遍）。{@code RewindCommands} 虽然在 {@code rewind.client} 包里，但它
 * 只碰 Brigadier 与原版命令类型，客户端专属动作（打开时间树、打印状态机）由 {@code RewindClient}
 * 注入，所以专用服务器上加载它不会碰到客户端类——这也是「命令类不引用客户端类」那条边界的落点。
 */
public final class RewindFabric implements ModInitializer {
    public static final String MOD_ID = "rewind";

    @Override
    public void onInitialize() {
        ClearVisualNetwork.registerServer();
        // 必须在任何 RewindApi 调用之前装好
        RewindPlatforms.install(new FabricRewindPlatform());
        // 服务端行为的配置两边都要有，所以放在主入口而不是客户端入口里
        RewindServerConfig.register();
        // 命令两边都要注册（单人走集成服务器、多人走专用服务器；多人里只有 OP 能触发）
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> RewindCommands.register(dispatcher));
    }
}
