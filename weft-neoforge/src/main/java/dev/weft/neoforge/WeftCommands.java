package dev.weft.neoforge;

import com.mojang.brigadier.Command;
import dev.weft.neoforge.profiler.WeftProfiler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@code /weft report} — print the P0 regionizability report to whoever asked
 * (chat in single player, console on a dedicated server) and write
 * {@code weft-report.txt} into the game directory.
 *
 * <p>{@code /weft profile on|off} — toggle profiling at runtime (not
 * persisted; the config default applies on next launch).
 */
public final class WeftCommands {

    private WeftCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("weft")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> usage(ctx.getSource()))
                        .then(Commands.literal("report")
                                .executes(ctx -> report(ctx.getSource()))
                                // WS-7 (RFC-0009 sec. 6): the same window as data.
                                // The text report is unchanged and stays the human
                                // surface; this is additive.
                                .then(Commands.literal("--json")
                                        .executes(ctx -> reportJson(ctx.getSource()))))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("services").executes(ctx -> services(ctx.getSource())))
                        .then(Commands.literal("profile")
                                .executes(ctx -> profileStatus(ctx.getSource()))
                                .then(Commands.literal("on").executes(ctx -> setProfiling(ctx.getSource(), true)))
                                .then(Commands.literal("off").executes(ctx -> setProfiling(ctx.getSource(), false)))));
    }

    private static int usage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "/weft report - regionizability report | /weft profile [on|off] - toggle profiling"
                        + " | /weft services - P1 service status"
                        + " | /weft status - module posture (RFC-0003)"
                        + " | /weft report --json - the same window as weft-report.json"),
                false);
        return Command.SINGLE_SUCCESS;
    }

    /** RFC-0003 R5: the same one-glance posture table the startup log prints. */
    private static int status(CommandSourceStack source) {
        for (String line : dev.weft.neoforge.coexist.WeftModules.resolve()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        // Field-bench line: multi-region topology is NOT parallel fan-out.
        // Operators (and this project's own 2026-08-20 bench notes) kept reading
        // "4 regions" as "workers busy" while owned-parallel sections stayed 0,
        // which is how a tied result gets written up as a win. Its own line, for
        // every module state, so it is also visible when the module is yielded
        // or off - those are the states where the confusion is worst.
        source.sendSuccess(
                () -> Component.literal("  " + dev.weft.neoforge.regiontick.RegionizedTicking.fanOutEvidence()),
                false);
        return Command.SINGLE_SUCCESS;
    }

    private static int services(CommandSourceStack source) {
        for (String line : WeftMod.serviceStatusLines()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int report(CommandSourceStack source) {
        String report = WeftProfiler.get().buildReport();
        for (String line : report.split("\n")) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        try {
            Path gameDir = source.getServer().getServerDirectory();
            Path written = WeftProfiler.get().writeReportFile(gameDir);
            source.sendSuccess(() -> Component.literal("Written to " + written), false);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Could not write weft-report.txt: " + e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * WS-7 (RFC-0009 sec. 6): write {@code weft-report.json} beside the existing
     * text file. Same content, machine-readable, field names pinned by the
     * committed event schema so a consumer can rely on them.
     */
    private static int reportJson(CommandSourceStack source) {
        try {
            Path written = WeftProfiler.get().writeReportJson(
                    source.getServer().getServerDirectory());
            source.sendSuccess(() -> Component.literal("Written to " + written), false);
        } catch (IOException e) {
            source.sendFailure(Component.literal(
                    "Could not write weft-report.json: " + e.getMessage()));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int profileStatus(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Weft profiling is " + (WeftConfig.PROFILING_ENABLED ? "ON" : "OFF")), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setProfiling(CommandSourceStack source, boolean enabled) {
        WeftConfig.PROFILING_ENABLED = enabled;
        source.sendSuccess(() -> Component.literal(
                "Weft profiling " + (enabled ? "ON" : "OFF")
                        + " (runtime only - config default applies on next launch)"), true);
        return Command.SINGLE_SUCCESS;
    }
}
