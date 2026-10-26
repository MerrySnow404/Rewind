package cc.sighs.mixin;

import cc.sighs.rewind.client.CheckpointController;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「推进来的世界是从哪来的」——快速重启要复用的注册表 / 数据包资源从这儿抄一份。
 *
 * <p>NeoForge 1.21.1 那边是在关世界前从活着的集成服务器上抄：
 * {@code server.registries()} + {@code server.getServerResources().managers()} + {@code server.getWorldData()}。
 * 后两样在 Fabric 上拿不到——{@code getServerResources()} 是 NeoForge 加给 {@code MinecraftServer}
 * 的方法（原版只有私有字段 {@code resources}），而且它返回的那条记录
 * {@code MinecraftServer$ReloadableResources} 在原版里还是包私有的。
 *
 * <p>所以这里走 fabric-1.20.1 那条老路：在 {@code Minecraft.doWorldLoad(...)} 打开世界那一刻从
 * {@link WorldStem} 上抄。{@code MinecraftServer} 的构造本来就是把这三样从 {@code WorldStem} 里取出来的
 * （{@code resourceManager} / {@code dataPackResources} / {@code registries} / {@code worldData}），
 * 值完全等价，而它们在 {@code stopServer} 里都不会被关，可以跨两次世界生命周期复用。
 *
 * <p>{@code require = 0}：挂不上时那三样保持 null，{@code tryFastRestart} 会直接放弃、
 * 退回原版 {@code WorldOpenFlows.openWorld} 那条路（功能少一块，不崩游戏）。
 */
@Mixin(Minecraft.class)
public abstract class ClientWorldLoadMixins {
    @Inject(method = "doWorldLoad", at = @At("HEAD"), require = 0)
    private void rewind$captureWorldStem(LevelStorageSource.LevelStorageAccess access,
            PackRepository packs, WorldStem stem, boolean isNewWorld, CallbackInfo ci) {
        CheckpointController.captureWorldStem(stem);
    }
}
