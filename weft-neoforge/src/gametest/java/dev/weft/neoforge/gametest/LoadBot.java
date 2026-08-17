package dev.weft.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * WS-8 (RFC-0002): the headless load-generator bot. A NeoForge
 * {@link FakePlayer} (no-op connection, invulnerable, untickable) added to
 * the level's player list, so every "nearest player" computation — WS-1
 * activation distances, despawn ranges, spawn density — sees it exactly as
 * it would a real player.
 *
 * <p>Three load shapes, matching the RFC-0002 WS-8 scope line:
 * <ul>
 *   <li><b>join</b> — {@link #join}</li>
 *   <li><b>movement</b> — {@link #tickCircle} walks a circle at vanilla
 *       walking speed (~4.3 blocks/s)</li>
 *   <li><b>chunk loading</b> — {@link #loadChunkAt} teleports the bot and
 *       synchronously full-loads the chunk under it</li>
 * </ul>
 */
public final class LoadBot {

    /** Vanilla walking speed in blocks per tick. */
    private static final double WALK_SPEED = 4.317 / 20.0;

    private final ServerLevel level;
    private final FakePlayer player;
    private final BlockPos center;
    private int moveTicks;

    private LoadBot(ServerLevel level, FakePlayer player, BlockPos center) {
        this.level = level;
        this.player = player;
        this.center = center;
    }

    /** Joins a bot at {@code center} ground level. Deterministic identity. */
    public static LoadBot join(ServerLevel level, BlockPos center, String name) {
        GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
        FakePlayer player = new FakePlayer(level, profile);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, center.getX(), center.getZ());
        player.moveTo(center.getX() + 0.5, y, center.getZ() + 0.5, 0.0f, 0.0f);
        level.addNewPlayer(player);
        return new LoadBot(level, player, center);
    }

    /** One movement step along a circle of {@code radius} around the center. */
    public void tickCircle(double radius) {
        moveTicks++;
        double angle = (WALK_SPEED / radius) * moveTicks;
        double x = center.getX() + 0.5 + Math.cos(angle) * radius;
        double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
        player.setPos(x, player.getY(), z);
    }

    /**
     * Teleports the bot to the center of chunk ({@code chunkX}, {@code
     * chunkZ}) and synchronously loads it to FULL status — the "player walks
     * into fresh terrain" load, without waiting on ticket propagation.
     */
    public void loadChunkAt(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        player.setPos(pos.getMiddleBlockX() + 0.5, player.getY(), pos.getMiddleBlockZ() + 0.5);
        level.getChunk(chunkX, chunkZ);
    }

    public ServerPlayer player() {
        return player;
    }

    /** Removes the bot from the level (the "player disconnects" half). */
    public void leave() {
        level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }
}
