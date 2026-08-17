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
                        .then(Commands.literal("report").executes(ctx -> report(ctx.getSource())))
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
                        + " | /weft status - module posture (RFC-0003)"), false);
        return Command.SINGLE_SUCCESS;
    }

    /** RFC-0003 R5: the same one-glance posture table the startup log prints. */
    private static int status(CommandSourceStack source) {
        for (String line : dev.weft.neoforge.coexist.WeftModules.resolve()) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
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
