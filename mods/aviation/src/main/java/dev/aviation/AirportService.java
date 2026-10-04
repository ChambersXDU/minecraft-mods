package dev.aviation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class AirportService {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, AviationMod.id("airport"));
    public static final Vec3 ARRIVAL = new Vec3(240.5, 64.1, 203.5);
    private static final Set<UUID> WAITING = new HashSet<>();
    private static boolean building;
    public static AirportData data(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(AirportData.TYPE); }
    public static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(AirportService::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var saved = data(server);
            building = saved.cursor > 0 && saved.cursor < AirportLayout.plan().size();
            if (saved.cursor >= AirportLayout.plan().size()) ensureAirliner(server.getLevel(DIMENSION), saved);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { WAITING.clear(); building = false; });
    }
    public static void enter(ServerPlayer player) {
        var server = player.level().getServer();
        if (player.level().dimension().equals(DIMENSION)) return;
        if (server.getLevel(DIMENSION) == null) { player.sendOverlayMessage(Component.translatable("message.aviation.missing_airport")); return; }
        var saved = data(server);
        saved.returns.put(player.getUUID().toString(), new AirportData.ReturnPoint(player.level().dimension().identifier().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        saved.setDirty();
        if (saved.cursor >= AirportLayout.plan().size()) {
            ensureAirliner(server.getLevel(DIMENSION), saved); arrive(player);
        }
        else {
            WAITING.add(player.getUUID()); building = true;
            player.sendOverlayMessage(Component.translatable("message.aviation.preparing", 0));
        }
    }
    public static void tick(MinecraftServer server) {
        if (!building) return;
        var level = server.getLevel(DIMENSION);
        if (level == null) return;
        var saved = data(server);
        var plan = AirportLayout.plan();
        long budget = System.nanoTime() + 12_000_000L;
        int placed = 0;
        while (saved.cursor < plan.size() && placed++ < 2500) {
            var block = plan.get(saved.cursor++);
            level.setBlock(block.pos(), block.state(), 2);
            if (System.nanoTime() >= budget) break;
        }
        saved.setDirty();
        if (server.getTickCount() % 20 == 0) {
            for (var id : WAITING) {
                var player = server.getPlayerList().getPlayer(id);
                if (player != null) player.sendOverlayMessage(Component.translatable("message.aviation.preparing", saved.cursor * 100 / plan.size()));
            }
        }
        if (saved.cursor >= plan.size()) {
            building = false;
            if (!saved.commissioned) {
                spawnAircraft(level, false, 40, 64, -90);
                spawnAircraft(level, false, 161, 173, 180);
                spawnAircraft(level, true, 422, 165, 180);
                spawnAircraft(level, true, 475, 165, 180);
                saved.commissioned = true; saved.setDirty();
            }
            ensureAirliner(level, saved);
            for (var id : WAITING) { var player = server.getPlayerList().getPlayer(id); if (player != null) arrive(player); }
            WAITING.clear();
        }
    }
    private static void spawnAircraft(ServerLevel level, boolean fighter, int x, int z, float yaw) {
        var entity = (fighter ? AviationMod.FIGHTER : AviationMod.PLANE).create(level, EntitySpawnReason.COMMAND);
        if (entity != null) { entity.setPos(x + 0.5, 64.05, z + 0.5); entity.setYRot(yaw); level.addFreshEntity(entity); }
    }
    private static void ensureAirliner(ServerLevel level, AirportData saved) {
        if (level == null || saved.airlinerCommissioned) return;
        var airliner = AviationMod.AIRLINER.create(level, EntitySpawnReason.COMMAND);
        if (airliner == null) return;
        airliner.setPos(PassengerBoarding.parkingPosition(0)); airliner.setYRot(180);
        if (level.addFreshEntity(airliner)) { saved.airlinerCommissioned = true; saved.setDirty(); }
    }
    private static void arrive(ServerPlayer player) {
        player.teleport(new TeleportTransition(player.level().getServer().getLevel(DIMENSION), ARRIVAL, Vec3.ZERO, 180, 0,
                TeleportTransition.PLACE_PORTAL_TICKET));
        player.sendOverlayMessage(Component.translatable("message.aviation.arrival"));
    }
    public static void leave(ServerPlayer player) {
        var server = player.level().getServer();
        var point = data(server).returns.get(player.getUUID().toString());
        ServerLevel level = point == null ? server.overworld() : server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(point.dimension())));
        if (level == null) level = server.overworld();
        var position = point == null ? Vec3.atBottomCenterOf(server.getRespawnData().pos()).add(0, 1, 0) : new Vec3(point.x(), point.y(), point.z());
        player.teleport(new TeleportTransition(level, position, Vec3.ZERO, point == null ? 0 : point.yaw(), point == null ? 0 : point.pitch(),
                TeleportTransition.PLACE_PORTAL_TICKET));
    }
}
