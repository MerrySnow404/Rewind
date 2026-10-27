package cc.sighs;

import cc.sighs.rewind.client.RewindCommands;
import cc.sighs.rewind.common.spi.RewindPlatforms;
import cc.sighs.rewind.network.ClearVisualNetwork;
import cc.sighs.rewind.server.FabricRewindPlatform;
import cc.sighs.rewind.server.RewindServerConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric 26.1 的入口。
 *
 * <p>装平台实现（common 侧所有需要碰 Minecraft 的能力都经过它）与读服务端配置。客户端入口在
 * {@code cc.sighs.RewindFabricClient}（{@code ClientModInitializer}），本类不引用任何
 * {@code net.minecraft.client.*} / {@code cc.sighs.rewind.client} 的界面类，所以专用服务器上也能加载。
 *
 * <p><b>与 {@code targets/neoforge-26.1} 的差异</b>：那边命令在 mod 构造期订阅
 * {@code RegisterCommandsEvent}，Fabric 没有事件总线，走 {@link CommandRegistrationCallback}
 * （模组初始化期注册，单人的集成服务器与专用服务器都会调到）。命令类本体两边的命令树、权限判定
 * （26.1 的 {@code Permissions.COMMANDS_GAMEMASTER}）与执行流程完全一致。
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
