package cc.sighs.mixin;

import cc.sighs.rewind.client.RewindClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原始按键输入，顶替 NeoForge 的 {@code InputEvent.Key}。
 *
 * <p>为什么非要原始按键：原版只在 {@code screen == null} 时给 {@code KeyMapping} 累计点击
 * （{@code KeyboardHandler.keyPress} 里 {@code handlesGameInput = screen == null}），所以在 GUI 里按
 * F7/F8/F9 靠 {@code KeyMapping.consumeClick()} 是收不到的。挂在 {@code keyPress} 的 HEAD（也就是原版
 * 自己处理这一下按键之前）就绕开了这条限制，和主版本拿到的时机一致。
 *
 * <p>26.1 的签名与 1.20.1 不同：{@code keyPress(long window, int action, KeyEvent event)}——键值与 scancode
 * 打成了一个 {@link KeyEvent}（还有修饰键），不再是四个散参数。这里把 {@code event} 拆开喂给
 * {@link RewindClient#onRawKey}，保持那边「窗口 / 键值 / scancode / 动作」的口径不变。
 * 只有 {@code action == PRESS} 才会真正处理（和 {@code InputEvent.Key} 一样只认按下），窗口号也在那边核对。
 *
 * <p>{@code require = 0}：挂不上就退化成「只有没开界面时热键才生效」。
 */
@Mixin(KeyboardHandler.class)
public abstract class ClientInputMixins {
    @Inject(method = "keyPress", at = @At("HEAD"), require = 0)
    private void rewind$rawKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        RewindClient.onRawKey(window, event.key(), event.scancode(), action);
    }
}
