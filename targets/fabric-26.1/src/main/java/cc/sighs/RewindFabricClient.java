package cc.sighs;

import cc.sighs.rewind.client.RewindClient;
import cc.sighs.rewind.network.ClearVisualNetwork;
import net.fabricmc.api.ClientModInitializer;

/**
 * Fabric 26.1 的客户端入口。
 *
 * <p>{@code fabric.mod.json} 里的 {@code client} 入口点，只在物理客户端上执行；专用服务器永远不会
 * 加载本类，也就不会连带加载 {@code cc.sighs.rewind.client.*} 那些碰 {@code net.minecraft.client.*}
 * 的类——这正是 {@code targets/neoforge-26.1} 用 {@code FMLEnvironment.getDist()} 判定守住的那条边界。
 *
 * <p>这里只做一件事：把 {@link RewindClient#setup()} 叫起来（配置、热键、tick、以及 {@code RewindApi}
 * 的客户端桥）。
 *
 * <p><b>与 {@code targets/fabric-1.20.1} 的差异</b>：那边还有一个
 * {@code cc.sighs.mixin.AuiFabricAnnotationScanMixins}（绕开 AUI fabric 的 SPI provider 只有 private
 * 构造器的 bug）。AUI {@code fabric-26.1:1.2.7} 里 {@code AnnotationScanUtil} 已经没有别的类引用
 * （{@code javap} 核过：整份 jar 里只有它自己），jar 里也不再有 {@code FabricAnnotationScanner} 这个
 * provider，所以那个 mixin 既没有挂点意义、也救不了任何东西，本 target 直接不带它。
 */
public final class RewindFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClearVisualNetwork.registerClient();
        RewindClient.setup();
    }
}
