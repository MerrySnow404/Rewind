package cc.sighs.rewind.client;

import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * 过渡期间的「无界面」处理。
 *
 * <p>回溯要走原版「关世界 → 重开世界」那条路，原版在这条路上会依次挂 {@code GenericMessageScreen}
 * （保存中）、{@code LevelLoadingScreen}（区块网格）、{@code ProgressScreen} 这些加载屏。过渡要求全程
 * 没有任何界面，所以把它们拦掉——画面由 {@link RewindTransitionRenderer} 负责，玩家看到的是
 * 「画面糊住 → 换世界 → 化开」。拦截只在过渡进行期间生效；一旦过渡结束（或失败），原版行为完全恢复。
 *
 * <p>26.1 的加载屏清单与 {@code targets/neoforge-26.1} 一致：{@code ReceivingLevelScreen} 已经没了
 * （下载地形那段现在由 {@code LoadingOverlay} 这种 overlay 承担，它不是 Screen，本来也不会走这条拦截），
 * {@code LevelLoadingScreen} 仍在（构造函数换成了 {@code (LevelLoadTracker, Reason)}，拦截只看类型）。
 *
 * <p>暂停屏也要拦：原版在窗口失去焦点时会自己挂一个 {@code PauseScreen}
 * （{@code Minecraft#pauseIfInactive}，{@code pauseOnLostFocus} 开着时每帧都试），而过渡一开始就把当前
 * 界面摘掉了（见 {@code CheckpointController.closeScreen}），于是「刚摘掉界面」的那一帧正好是它出手的
 * 时机——不拦的话过渡画面上会浮出一层暂停菜单。
 *
 * <p><b>与 {@code targets/fabric-1.20.1} 的差异</b>：那边拦的是 {@code GenericDirtMessageScreen}
 * （1.20.1 的名字），26.1 叫 {@code GenericMessageScreen}；{@code ReceivingLevelScreen} 已经不存在，
 * 列表里相应去掉。注入点与取消语义不变（{@code cc.sighs.mixin.ClientScreenMixins} 挂在
 * {@code Minecraft.setScreen(Screen)} 的 HEAD）。
 */
public final class RewindScreens {
    private RewindScreens() {
    }

    /**
     * 这一次 {@code setScreen} 要不要拦下来。
     *
     * <p>由 {@code ClientScreenMixins} 在 {@code Minecraft.setScreen} 的 HEAD 调用，
     * 返回 true 就取消这次开屏。
     */
    public static boolean blocks(Screen newScreen) {
        if (newScreen == null || !RewindTransition.isActive()) {
            return false;
        }
        // 后处理不可用时不要拦：让原版加载屏照常显示，总比露出一段黑屏好
        if (!RewindTransitionRenderer.isEffectAvailable()) {
            return false;
        }
        return newScreen instanceof PauseScreen
                || newScreen instanceof GenericMessageScreen
                || newScreen instanceof LevelLoadingScreen
                || newScreen instanceof ProgressScreen;
    }
}
