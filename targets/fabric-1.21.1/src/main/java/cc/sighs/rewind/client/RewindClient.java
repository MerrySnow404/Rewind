package cc.sighs.rewind.client;

import com.mojang.blaze3d.platform.InputConstants;
import cc.sighs.rewind.Rewind;
import cc.sighs.rewind.api.RewindApi;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 客户端侧的注册入口。只在物理客户端加载（由 {@code fabric.mod.json} 的 {@code client} 入口
 * {@code cc.sighs.RewindFabricClient} 调进来），所以专用服务器不会因为引用客户端类而炸掉。
 *
 * <p><b>与 NeoForge 1.21.1 的差异——事件换成 Fabric 的路子</b>：
 * <ul>
 *   <li>{@code RegisterKeyMappingsEvent} → {@code KeyBindingHelper.registerKeyBinding(...)}。</li>
 *   <li>{@code ClientTickEvent.Post} → {@link ClientTickEvents#END_CLIENT_TICK}。</li>
 *   <li>{@code RegisterCommandsEvent} 在 mod 主入口用 {@code CommandRegistrationCallback} 注册
 *       （命令两边都要有，不只是客户端）；本类只注入两个客户端专属动作。</li>
 *   <li>{@code RenderFrameEvent.Pre/Post} 与 {@code InputEvent.Key}、{@code ScreenEvent.Opening}
 *       Fabric 都没有对应事件，改由 {@code cc.sighs.mixin} 下的注入提供：
 *       见 {@link #onFramePre} / {@link #onFramePost} / {@link #onRawKey} 与 {@code RewindScreens}。</li>
 * </ul>
 */
public final class RewindClient {
    private static final String CATEGORY = "key.categories.rewind";
    private static final int SNAPSHOT_REQUEST = 1;
    private static final int RESTORE_REQUEST = 2;
    private static final int TREE_REQUEST = 3;

    private static KeyMapping snapshotKey;
    private static KeyMapping restoreKey;
    private static KeyMapping treeKey;
    /** 待执行的热键请求：0 = 没有，见 {@link #flushPendingRequest()}。 */
    private static int pendingRequest;

    private RewindClient() {
    }

    /** 客户端入口初始化时调用一次。 */
    public static void setup() {
        // 过渡参数（所有可调项）落在 config/rewind-client.properties
        RewindClientConfig.register();
        // 把「带过渡的异步入口」接到状态机上：RewindApi.requestCheckpoint/requestRollback 从此等价于按 F7/F8
        CheckpointController.installApiBridge();
        snapshotKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.rewind.snapshot", InputConstants.KEY_F7, CATEGORY));
        restoreKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.rewind.restore", InputConstants.KEY_F8, CATEGORY));
        treeKey = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.rewind.tree", InputConstants.KEY_F9, CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(RewindClient::onClientTick);
        // 命令的注册在 mod 主入口（单人的集成服务器与专用服务器都要有）；这里只把两个客户端专属动作
        // 交给命令层——命令类因此不引用任何客户端类，专用服务器上也能加载。
        RewindCommands.installClientActions(RewindTreeScreen::open, CheckpointController::statusLine);
        Rewind.LOGGER.info("Rewind: registered key mappings (F7=checkpoint, F8=restore, F9=time tree)");
    }

    public static KeyMapping snapshotKey() {
        return snapshotKey;
    }

    public static KeyMapping restoreKey() {
        return restoreKey;
    }

    public static KeyMapping treeKey() {
        return treeKey;
    }

    // ------------------------------------------------------------------ 帧钩子（由 ClientFrameMixins 调进来）

    /** 帧开始（整帧的绘制还没开始）：封面抓帧要在这之前把界面与 HUD 摘掉。 */
    public static void onFramePre(Minecraft minecraft) {
        CoverCapture.onFramePre(minecraft);
    }

    /**
     * 帧结束：这一帧已经画完、但还没 blit 到屏幕。
     *
     * <p>两句的先后顺序是硬约定——封面必须抢在过渡后处理之前读那一帧，否则封面会带上存档过渡的饱和度
     * （NeoForge 那边靠 {@code EventPriority.HIGHEST} 实现同一件事；Fabric 这里就是这两行的书写顺序）。
     */
    public static void onFramePost(Minecraft minecraft) {
        CoverCapture.onFramePost(minecraft);
        RewindTransitionRenderer.onRenderFramePost(minecraft);
    }

    // ------------------------------------------------------------------ 原始按键（由 ClientInputMixins 调进来）

    /**
     * 原始按键：无论有没有界面都会走到这里（原版只在 {@code screen == null} 时给 KeyMapping 累计点击），
     * 所以 F7 / F8 / F9 在世界内任何地方都能生效，包括 GUI 里。
     *
     * <p>用 {@link KeyMapping#matches} 判定，所以玩家在按键设置里改绑依然有效。按下的时机如果正好撞上
     * 上一次过渡或还没结束的操作，就记下来等空下来再执行——等价于原版 {@code KeyMapping} 点击计数
     * 那种「攒着等界面关掉」的语义，而不是把按键直接丢掉。
     */
    public static void onRawKey(long window, int key, int scanCode, int action) {
        if (action != InputConstants.PRESS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getWindow() != null
                && window != minecraft.getWindow().getWindow()) {
            // 不是主窗口的按键（例如别的 GLFW 窗口）不认
            return;
        }
        if (snapshotKey != null && snapshotKey.matches(key, scanCode)) {
            pendingRequest = SNAPSHOT_REQUEST;
        } else if (restoreKey != null && restoreKey.matches(key, scanCode)) {
            pendingRequest = RESTORE_REQUEST;
        } else if (treeKey != null && treeKey.matches(key, scanCode)) {
            pendingRequest = TREE_REQUEST;
        }
    }

    private static void onClientTick(Minecraft minecraft) {
        CheckpointController.tick(minecraft);
        flushPendingRequest();
    }

    private static void flushPendingRequest() {
        if (pendingRequest == 0 || RewindTransition.isActive() || RewindApi.isBusy()) {
            return;
        }
        int request = pendingRequest;
        pendingRequest = 0;
        if (request == SNAPSHOT_REQUEST) {
            RewindApi.requestCheckpoint("key");
        } else if (request == RESTORE_REQUEST) {
            RewindApi.requestRollback("key");
        } else {
            RewindTreeScreen.open();
        }
    }
}
