package cc.sighs.mixin;

import cc.sighs.rewind.client.RewindClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 帧开始 / 帧结束两个回调点，顶替 NeoForge 的 {@code RenderFrameEvent.Pre} / {@code Post}。
 *
 * <p>Fabric 没有「整帧渲染」事件，只能自己挂到原版的方法上。26.1 的帧循环是
 * {@code Minecraft.runTick(boolean)} → {@code Minecraft.renderFrame(boolean)}，后者（按 26.1 的字节码核对）：
 *
 * <pre>
 *   deltaTracker.advanceRealTime(...)
 *   pauseIfInactive()
 *   gameRenderer.update(deltaTracker, advanceGameTime)
 *   pick(partialTick)
 *   gameRenderer.extract(deltaTracker, advanceGameTime)
 *   RenderSystem.executePendingTasks()
 *   gameRenderer.render(deltaTracker, advanceGameTime)   ← 整帧画完
 *   mainRenderTarget.blitToScreen()                      ← 之后才上屏
 * </pre>
 *
 * <p>两个注入点就是 NeoForge 在同一个 MC 26.1 上发这两个事件的位置——看
 * {@code minecraft-patched-26.1.2.84} 里 {@code Minecraft.renderFrame} 的 patch：{@code
 * ClientHooks.fireRenderFramePre(deltaTracker)} 正好在这句 {@code gameRenderer.render(...)} 之前，
 * {@code fireRenderFramePost(deltaTracker)} 在它之后（两者都在 {@code blitToScreen()} 之前）。
 * 所以这里用同一条 invoke 的「之前 / 之后」两个位置，语义与主版本逐帧一致：
 * {@code Pre} 是「这一帧还没画」（封面抓帧要赶在这之前摘掉界面与 HUD），
 * {@code Post} 是「这一帧画完了但还没 blit 到屏幕」（过渡后处理要重采样整幅画面）。
 *
 * <p>两个注入都带 {@code require = 0}：万一将来帧循环的形态变了、这条 invoke 对不上，退化成
 * 「过渡与封面都不工作」（画面回到原版行为），而不是启动崩溃。
 *
 * <p>{@code Minecraft.clearLevel(...)} / {@code updateScreenAndTick(...)} 会反复调
 * {@code runTick(false)} 忙等集成服务器线程结束，那条路上照样会走 {@code renderFrame}——
 * 这正是「世界正在被拆掉的那段也要继续出画」所需要的（和主版本走同一条原版循环）。
 */
@Mixin(Minecraft.class)
public abstract class ClientFrameMixins {
    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"),
            require = 0)
    private void rewind$framePre(boolean advanceGameTime, CallbackInfo ci) {
        RewindClient.onFramePre((Minecraft) (Object) this);
    }

    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void rewind$framePost(boolean advanceGameTime, CallbackInfo ci) {
        RewindClient.onFramePost((Minecraft) (Object) this);
    }
}
