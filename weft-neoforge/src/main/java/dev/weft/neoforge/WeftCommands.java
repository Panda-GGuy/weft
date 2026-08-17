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
 */
public final class WeftCommands {

    private WeftCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("weft")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("report").executes(ctx -> report(ctx.getSource()))));
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
}
