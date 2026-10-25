package cc.sighs.rewind.client;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import cc.sighs.rewind.api.RewindApi;
import cc.sighs.rewind.api.RewindResult;
import cc.sighs.rewind.server.RewindServerConfig;
import cc.sighs.rewind.snapshot.SnapshotInventory;
import cc.sighs.rewind.snapshot.SnapshotLayout;
import cc.sighs.rewind.snapshot.SnapshotMeta;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /rewind} 命令：模组主要功能的命令入口（单人与多人都是同一条命令）。
 *
 * <pre>
 * /rewind status                列出所有槽位、时间线的头与上一次操作
 * /rewind ui                    打开时间树（= 按 F9；多人模式下禁用）
 * /rewind snapshot [&lt;槽位&gt;]      建立 / 覆盖存档点（= 按 F7；不给槽位就是 quick）
 * /rewind restore [&lt;槽位&gt;]       回溯到存档点（= 按 F8；不给槽位就是 quick）
 * /rewind info &lt;槽位&gt;           看一个槽位的详情
 * /rewind delete &lt;槽位&gt;         删掉一个槽位
 * /rewind rename &lt;槽位&gt; &lt;名字&gt;   给槽位改名
 * /rewind autosave on|off       跟着原版自动保存建点
 * </pre>
 *
 * <p><b>两边都注册</b>：入口在 mod 构造期订阅 {@code RegisterCommandsEvent}，所以单人的集成服务器与
 * 专用服务器上都有这条命令。**多人服务器上只有 OP 能用**（{@code requires} 里的 permission 2）：
 * 多人模式禁用了时间树，回溯只能由 OP 用 {@code /rewind restore <槽位>} 来做。单人（且没开局域网）
 * 里照旧所有人都能用。
 *
 * <p><b>本类不引用任何客户端类</b>：打开时间树、打印客户端状态机这两件事由客户端入口用
 * {@link #installClientActions} 注入；专用服务器上它们是 null，{@code ui} 会回一句「只能在客户端用」。
 *
 * <p>只做命令层的事——参数、权限、把结果念给玩家听；真正的活全部转给 {@link RewindApi} 与
 * {@link RewindServerConfig}。带过渡的那两个动作（{@code snapshot} / {@code restore}）在有客户端时
 * 走 F7 / F8 那条路（{@link RewindApi#requestCheckpoint(String, String)} /
 * {@link RewindApi#requestRollback(String, String)}）；**专用服务器上没有客户端桥**，就退到服务端
 * 线程上的同步入口（{@link RewindApi#createCheckpoint} / {@link RewindApi#rollbackInPlace}）——
 * 命令本身就在服务端线程上执行，那几条的线程要求天然满足（代价是执行期间服务器会卡一下）。
 */
public final class RewindCommands {
    /** 客户端注入的动作：打开时间树。专用服务器上是 null。 */
    private static volatile Runnable openTree;
    /** 客户端注入的动作：返回 {@code status} 末尾那行客户端状态机状态。专用服务器上是 null。 */
    private static volatile Supplier<String> clientStatus;

    /**
     * 由客户端入口在 setup 时安装。专用服务器不会调用它，于是 {@code ui} 与状态行自动缺席，
     * 本类也就不需要（也不能）引用 {@code net.minecraft.client.*}。
     */
    public static void installClientActions(Runnable treeOpener, Supplier<String> statusLine) {
        openTree = treeOpener;
        clientStatus = statusLine;
    }

    private RewindCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("rewind")
                // 加载期（数据包函数解析时，Brigadier 的 canUse 预检）拿到的是 getServer() 为 null 的 source，
                // 这里必须放行，否则任何引用 /rewind 的 .mcfunction 都会 NPE 加载失败。
                // 多人（专用服务器 / 局域网已开放）只给 OP：那会儿回溯是 OP 的动作。
                .requires(source -> source.getServer() == null
                        || (source.getServer().isSingleplayer() && !source.getServer().isPublished())
                        || source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("ui").executes(context -> ui(context.getSource())))
                .then(Commands.literal("snapshot")
                        .executes(context -> snapshot(context.getSource(), RewindApi.DEFAULT_SLOT))
                        .then(slotArgument().executes(context -> snapshot(context.getSource(), slot(context)))))
                .then(Commands.literal("restore")
                        .executes(context -> restore(context.getSource(), RewindApi.DEFAULT_SLOT))
                        .then(slotArgument().executes(context -> restore(context.getSource(), slot(context)))))
                .then(Commands.literal("info")
                        .then(slotArgument().executes(context -> info(context.getSource(), slot(context)))))
                .then(Commands.literal("delete")
                        .then(slotArgument().executes(context -> delete(context.getSource(), slot(context)))))
                .then(Commands.literal("rename")
                        .then(slotArgument().then(
                                Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(context -> rename(context.getSource(), slot(context),
                                                StringArgumentType.getString(context, "name"))))))
                .then(Commands.literal("autosave")
                        .then(Commands.literal("on").executes(context -> autosave(context.getSource(), true)))
                        .then(Commands.literal("off").executes(context -> autosave(context.getSource(), false)))));
    }

    // ------------------------------------------------------------------ 命令树

    /** 槽位参数：一个不含空格的词；补全用界面上那套槽位（自动 / 快速 / 手动槽位）。 */
    private static RequiredArgumentBuilder<CommandSourceStack, String> slotArgument() {
        return Commands.<String>argument("slot", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(RewindApi.slots(), builder));
    }

    private static String slot(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "slot");
    }

    // ------------------------------------------------------------------ 动作

    /** 时间树是纯客户端的东西：专用服务器上没有这个动作。 */
    private static int ui(CommandSourceStack source) {
        Runnable opener = openTree;
        if (opener == null) {
            source.sendFailure(Component.translatable("rewind.cmd.client_only"));
            return 0;
        }
        opener.run();
        return 1;
    }

    /** 建立 / 覆盖存档点，等价于按 F7。 */
    private static int snapshot(CommandSourceStack source, String slot) {
        Component blocker = worldActionBlocker(source);
        if (blocker != null) {
            source.sendFailure(blocker);
            return 0;
        }
        if (RewindApi.requestCheckpoint(slot, "command")) {
            source.sendSuccess(() -> Component.translatable("rewind.cmd.snapshot_started", slot), false);
            return 1;
        }
        // 没有客户端（专用服务器）：同步做一次，命令本来就在服务端线程上
        RewindResult result = RewindApi.createCheckpoint(source.getServer(), slot, "command");
        if (!result.success) {
            source.sendFailure(Component.translatable("rewind.cmd.snapshot_failed",
                    String.valueOf(result.failure)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("rewind.cmd.snapshot_done", slot, result.summary), false);
        return 1;
    }

    /** 回溯到存档点，等价于按 F8；多人服务器上这是 OP 唯一能用的回溯方式。 */
    private static int restore(CommandSourceStack source, String slot) {
        Component blocker = worldActionBlocker(source);
        if (blocker != null) {
            source.sendFailure(blocker);
            return 0;
        }
        Path world = worldRoot(source);
        if (!RewindApi.hasCheckpoint(world, slot)) {
            source.sendFailure(Component.translatable("rewind.cmd.slot_missing", slot));
            return 0;
        }
        if (RewindApi.rollbackCooldownRemainingMillis(world) > 0L) {
            source.sendFailure(Component.translatable("rewind.error.rollback_cooldown"));
            return 0;
        }
        if (RewindApi.requestRollback(slot, "command")) {
            source.sendSuccess(() -> Component.translatable("rewind.cmd.restore_started", slot), false);
            return 1;
        }
        // 没有客户端（专用服务器）：原地回滚直接在服务端线程上跑
        RewindResult result = RewindApi.rollbackInPlace(source.getServer(), slot, "command");
        if (!result.success) {
            source.sendFailure(Component.translatable("rewind.cmd.restore_failed",
                    String.valueOf(result.failure)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("rewind.cmd.restore_done", slot, result.summary), false);
        return 1;
    }

    /** 看一个槽位的详情：一行元数据 + 背包快照装了几格。 */
    private static int info(CommandSourceStack source, String slot) {
        Path world = worldRoot(source);
        if (world == null) {
            source.sendFailure(noWorld());
            return 0;
        }
        SnapshotMeta meta = RewindApi.describe(world, slot);
        if (meta == null) {
            source.sendFailure(Component.translatable("rewind.cmd.slot_missing", slot));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(meta.describe()), false);
        SnapshotInventory inventory = RewindApi.readInventory(world, slot);
        source.sendSuccess(() -> Component.translatable("rewind.cmd.inventory",
                inventory.size(), SnapshotInventory.SLOT_COUNT), false);
        return 1;
    }

    private static int delete(CommandSourceStack source, String slot) {
        Component blocker = busyBlocker();
        if (blocker != null) {
            source.sendFailure(blocker);
            return 0;
        }
        Path world = worldRoot(source);
        if (world == null) {
            source.sendFailure(noWorld());
            return 0;
        }
        if (!RewindApi.deleteCheckpoint(world, slot)) {
            source.sendFailure(Component.translatable("rewind.cmd.slot_missing", slot));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("rewind.cmd.deleted", slot), false);
        return 1;
    }

    private static int rename(CommandSourceStack source, String slot, String name) {
        Component blocker = busyBlocker();
        if (blocker != null) {
            source.sendFailure(blocker);
            return 0;
        }
        Path world = worldRoot(source);
        if (world == null) {
            source.sendFailure(noWorld());
            return 0;
        }
        String trimmed = name.trim();
        if (!SnapshotLayout.isValidDisplayName(trimmed)) {
            source.sendFailure(Component.translatable("rewind.cmd.name_invalid", SnapshotLayout.NAME_MAX_LENGTH));
            return 0;
        }
        if (!RewindApi.renameCheckpoint(world, slot, trimmed)) {
            source.sendFailure(Component.translatable("rewind.cmd.slot_missing", slot));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("rewind.cmd.renamed", slot, trimmed), false);
        return 1;
    }

    /**
     * 开关「跟着原版自动保存建点」（界面上那张「自动存档」卡的设置，同一个配置项）。
     *
     * <p>它只写 COMMON 配置、不碰世界，所以没有别的命令那几道判定。
     */
    private static int autosave(CommandSourceStack source, boolean enabled) {
        RewindServerConfig.setAutoCheckpointEnabled(enabled);
        source.sendSuccess(() -> Component.translatable(
                enabled ? "rewind.cmd.autosave_on" : "rewind.cmd.autosave_off"), false);
        return enabled ? 1 : 2;
    }

    // ------------------------------------------------------------------ 查询与判定

    private static int status(CommandSourceStack source) {
        Path world = worldRoot(source);
        if (world == null) {
            source.sendFailure(noWorld());
            return 0;
        }
        List<SnapshotMeta> metas = RewindApi.listCheckpoints(world);
        source.sendSuccess(() -> Component.literal("head=" + RewindApi.currentSlot(world)), false);
        if (metas.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("rewind.cmd.status_empty"), false);
        } else {
            for (SnapshotMeta meta : metas) {
                source.sendSuccess(() -> Component.literal(meta.describe()), false);
            }
        }
        // 上一次结果在 common 里，客户端状态机那行由客户端入口注入（专用服务器上没有）
        RewindResult last = RewindApi.lastResult();
        Supplier<String> statusLine = clientStatus;
        String extra = statusLine == null ? null : statusLine.get();
        if (last != null || (extra != null && !extra.isEmpty())) {
            String line = ((last == null ? "" : last.describe()) + " "
                    + (extra == null ? "" : extra)).trim();
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return metas.size();
    }

    /** 命令只在世界里有意义：数据包函数解析期的 source 连 server 都没有。 */
    private static Path worldRoot(CommandSourceStack source) {
        return source.getServer() == null ? null : RewindApi.worldRoot(source.getServer());
    }

    /** 忙着的时候什么都别干：槽位管理与建点 / 回溯会写同一张索引。 */
    private static Component busyBlocker() {
        return RewindApi.isBusy() ? Component.translatable("rewind.error.busy") : null;
    }

    /** 建点 / 回溯要额外满足：在世界里；单人时还要求没开局域网（专用服务器本来就是对多人开放的）。 */
    private static Component worldActionBlocker(CommandSourceStack source) {
        Component busy = busyBlocker();
        if (busy != null) {
            return busy;
        }
        MinecraftServer server = source.getServer();
        if (server == null) {
            return noWorld();
        }
        // 「局域网已开放」只对集成服务器有意义：专用服务器本来就对多人开放（isPublished() 恒为 true），
        // 那正是这条命令要用的场合。
        return server.isSingleplayer() && server.isPublished()
                ? Component.translatable("rewind.error.lan_published") : null;
    }

    /** 「不在世界里」与「没有客户端桥」（专用服务器 / 还没 setup）共用一句：两种情况下都只能这么办。 */
    private static Component noWorld() {
        return Component.translatable("rewind.error.no_world");
    }
}
