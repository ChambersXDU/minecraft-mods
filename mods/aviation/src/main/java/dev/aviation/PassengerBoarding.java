package dev.aviation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/** Walk real persistent villagers through the existing jet bridge, then mount the aircraft. */
public final class PassengerBoarding {
    public static final int CAPACITY = 12;
    public static final int[] BRIDGE_CENTERS = {120, 164, 208, 252, 296};
    public record QueuedPassenger(UUID id, int waypoint) {
        public static final Codec<QueuedPassenger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("uuid").forGetter(QueuedPassenger::id),
                Codec.intRange(0, 3).fieldOf("waypoint").forGetter(QueuedPassenger::waypoint)
        ).apply(instance, QueuedPassenger::new));
    }
    public static Vec3 parkingPosition(int gate) { return new Vec3(BRIDGE_CENTERS[gate] + 0.5, 64, 177.5); }
    public static void tick(PlaneEntity plane) {
        if (!plane.airliner() || plane.boardingFinished || !(plane.level() instanceof ServerLevel level)
                || !level.dimension().equals(AirportService.DIMENSION)) return;
        if (plane.boardingGate < 0) {
            if (!plane.onGround() || plane.speedKmh() > 0.5 || Math.abs(Mth.wrapDegrees(plane.getYRot() - 180)) > 20) return;
            for (int gate = 0; gate < BRIDGE_CENTERS.length; gate++) {
                if (plane.position().distanceTo(parkingPosition(gate)) < 2) { begin(level, plane, gate); break; }
            }
            if (plane.boardingGate < 0) return;
        }
        plane.setBoarding(!plane.boardingQueue.isEmpty());
        double bridgeX = BRIDGE_CENTERS[plane.boardingGate] + 0.5;
        Vec3[] route = {
                new Vec3(bridgeX, 64, 188.5),
                new Vec3(bridgeX + 3.2, 64, 188.5),
                new Vec3(bridgeX + 3.2, 64, plane.getZ() + 3.8),
                new Vec3(plane.getX() + 2.5, 64, plane.getZ() + 3.8)
        };
        for (int i = plane.boardingQueue.size() - 1; i >= 0; i--) {
            var entry = plane.boardingQueue.get(i);
            var entity = level.getEntity(entry.id());
            if (!(entity instanceof Villager passenger) || !entity.isAlive()) {
                if (plane.missingBoarders.merge(entry.id(), 1, Integer::sum) > 200) plane.boardingQueue.remove(i);
                continue;
            }
            plane.missingBoarders.remove(entry.id());
            if (passenger.getVehicle() == plane) { plane.boardingQueue.remove(i); continue; }
            var delta = route[entry.waypoint()].subtract(passenger.position());
            double distance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (distance < 0.16) {
                if (entry.waypoint() == route.length - 1) {
                    if (passenger.startRiding(plane)) plane.boardingQueue.remove(i);
                } else plane.boardingQueue.set(i, new QueuedPassenger(entry.id(), entry.waypoint() + 1));
                continue;
            }
            double step = Math.min(0.10, distance);
            var movement = new Vec3(delta.x / distance * step, -0.08, delta.z / distance * step);
            float heading = (float) -Math.toDegrees(Math.atan2(delta.x, delta.z));
            passenger.setYRot(heading); passenger.setYHeadRot(heading); passenger.setYBodyRot(heading);
            passenger.setDeltaMovement(movement); passenger.move(MoverType.SELF, movement);
        }
        if (plane.boardingQueue.isEmpty()) { plane.boardingFinished = true; plane.setBoarding(false); }
    }
    private static void begin(ServerLevel level, PlaneEntity plane, int gate) {
        plane.boardingGate = gate;
        for (int i = plane.passengerCount(); i < CAPACITY; i++) {
            var passenger = EntityTypes.VILLAGER.create(level, EntitySpawnReason.EVENT);
            if (passenger == null) continue;
            passenger.setPos(BRIDGE_CENTERS[gate] + 0.5, 64, 202.5 - i * 0.6);
            passenger.setNoAi(true); passenger.setPersistenceRequired();
            passenger.setCustomName(Component.translatable("entity.aviation.passenger", i + 1));
            passenger.addTag("aviation_airport_passenger");
            if (level.addFreshEntity(passenger)) plane.boardingQueue.add(new QueuedPassenger(passenger.getUUID(), 0));
        }
        plane.setBoarding(!plane.boardingQueue.isEmpty());
    }
}
