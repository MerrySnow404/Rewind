package cc.sighs.rewind.server;

import java.nio.file.Path;
import cc.sighs.rewind.common.spi.FlushOutcome;
import cc.sighs.rewind.common.spi.RewindPlatform;
import cc.sighs.rewind.common.spi.RollbackOutcome;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Fabric 26.1 的 {@link RewindPlatform} 实现：把 common 需要知道的全部平台能力接进来。
 *
 * <p>这里是「版本差异」的唯一落点——common 侧的建点 / 查询 / 索引编排完全不知道自己在哪个
 * Minecraft 版本上跑。换一个版本时，改的就是这个类加上 {@code cc.sighs.mixin} 与 {@code client} 包。
 *
 * <p>参数在入口处统一转型：common 只把服务器对象当不透明句柄，传回来的就是这里的 {@link MinecraftServer}。
 *
 * <p><b>原地回滚已实现</b>：{@link #supportsInPlaceRollback()} 返回 true，{@link #rollbackInPlace}
 * 接到 {@link InPlaceRollback}（26.1 版，与 {@code targets/neoforge-26.1} 的引擎逐字节同源，
 * 只去掉了 NeoForge 的等级数据附件那一步，差异写在那两个类的类注释里）。
 * 失败时 {@code RewindApi} 照样会把错误交回客户端，客户端按约定退回「关世界 → 覆盖 → 重开」那条路。
 *
 * <p><b>与 {@code targets/neoforge-26.1} 的差异（只有 loader 侧）</b>：
 * <ul>
 *   <li>没有事件总线，所以 {@code beforeRollback} / {@code afterRollback} 用 SPI 的 default 实现
 *       （放行 + 什么都不发），{@code cc.sighs.rewind.event.*} 那两个事件类在本 target 里不存在。</li>
 *   <li>{@link #modClassLoader}：Fabric 只有一个 {@code KnotClassLoader}，所有模组的类都在它里面，
 *       所以返回本类自己的类加载器（那边要经 FML 的 {@code ModuleLayer} 按 modid 查）。</li>
 * </ul>
 */
public final class FabricRewindPlatform implements RewindPlatform {
    @Override
    public String modVersion() {
        return RewindVersion.of();
    }

    @Override
    public Path worldRoot(Object server) {
        return server(server).getWorldPath(LevelResource.LEVEL_DATA_FILE).getParent();
    }

    @Override
    public boolean isSameThread(Object server) {
        // 原版自己就是靠这个判线程（BlockableEventLoop.isSameThread）
        return server(server).isSameThread();
    }

    @Override
    public boolean isDedicatedServer(Object server) {
        return server(server).isDedicatedServer();
    }

    @Override
    public boolean isPublished(Object server) {
        return server(server).isPublished();
    }

    @Override
    public boolean hasPlayers(Object server) {
        return !server(server).getPlayerList().getPlayers().isEmpty();
    }

    @Override
    public FlushOutcome flushForCheckpoint(Object server, String slot, String source) {
        WorldFlush.Result flushed = WorldFlush.flush(server(server), slot, source);
        return new FlushOutcome(flushed.worldRoot, flushed.meta, flushed.inventory);
    }

    @Override
    public boolean supportsInPlaceRollback() {
        return true;
    }

    @Override
    public RollbackOutcome rollbackInPlace(Object server, Path worldRoot, String slot) throws Exception {
        InPlaceRollback.Result rolled = InPlaceRollback.run(server(server), worldRoot, slot);
        return new RollbackOutcome(rolled.summary(), rolled.mirrorCopied, rolled.mirrorSkipped,
                rolled.mirrorFiles, rolled.totalMs);
    }

    /**
     * 某个模组的类加载器：Fabric 只有一个 KnotClassLoader，所有模组的类都在它里面，所以返回它自己那个。
     * 跨模组反射要用它，见 {@code cc.sighs.rewind.common.compat.SophisticatedCoreCompat}。
     */
    @Override
    public ClassLoader modClassLoader(String modId) {
        return FabricRewindPlatform.class.getClassLoader();
    }

    private static MinecraftServer server(Object server) {
        return (MinecraftServer) server;
    }
}
