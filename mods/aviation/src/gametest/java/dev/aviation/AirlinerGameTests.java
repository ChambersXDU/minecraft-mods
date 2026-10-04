package dev.aviation;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class AirlinerGameTests {
    private void require(GameTestHelper h, boolean value, String message) {
        if (!value) throw h.assertionException(Component.literal(message));
    }

    @GameTest(maxTicks = 3000)
    public void realPassengersWalkFromJetBridgeAndResumeAfterSaving(GameTestHelper h) {
        var pilot = h.makeMockServerPlayerInLevel(); pilot.setGameMode(GameType.SURVIVAL);
        pilot.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        AirportService.enter(pilot);
        var server = h.getLevel().getServer();
        PlaneEntity[] craft = {null}; Vec3[] firstStart = {null};
        java.util.UUID[] firstPassenger = {null};
        @SuppressWarnings("unchecked") List<PassengerBoarding.QueuedPassenger>[] savedQueue = new List[]{null};
        h.startSequence().thenWaitUntil(() -> require(h, pilot.level().dimension().equals(AirportService.DIMENSION), "Airport still preparing"))
                .thenExecute(() -> {
                    var airport = server.getLevel(AirportService.DIMENSION);
                    airport.setChunkForced(10, 11, true); airport.setChunkForced(10, 12, true);
                    pilot.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
                    craft[0] = AviationMod.AIRLINER.create(airport, EntitySpawnReason.COMMAND);
                    craft[0].setPos(PassengerBoarding.parkingPosition(1)); craft[0].setYRot(180);
                    require(h, airport.addFreshEntity(craft[0]), "Airliner creation failed");
                }).thenWaitUntil(() -> require(h, craft[0].boardingQueue.size() == 12, "Twelve passengers must spawn inside the jet bridge"))
                .thenExecute(() -> {
                    var airport = server.getLevel(AirportService.DIMENSION);
                    firstPassenger[0] = craft[0].boardingQueue.getFirst().id();
                    var passenger = airport.getEntity(firstPassenger[0]);
                    require(h, passenger != null && passenger.getZ() >= 188 && passenger.getZ() <= 203
                            && Math.abs(passenger.getX() - 164.5) < 0.2, "Passenger must start inside the real bridge");
                    firstStart[0] = passenger.position();
                    require(h, craft[0].boarding() && craft[0].getControllingPassenger() == null, "Passengers must not take the pilot seat");
                }).thenIdle(20).thenExecute(() -> {
                    var airport = server.getLevel(AirportService.DIMENSION);
                    var passenger = airport.getEntity(firstPassenger[0]);
                    double walked = passenger.position().distanceTo(firstStart[0]);
                    require(h, walked > 0.5 && walked < 8 && passenger.getVehicle() == null,
                            "Passengers must actually walk along the bridge rather than teleport into the cabin");
                    savedQueue[0] = List.copyOf(craft[0].boardingQueue);
                    var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, airport.registryAccess());
                    craft[0].saveWithoutId(output);
                    var replacement = AviationMod.AIRLINER.create(airport, EntitySpawnReason.LOAD);
                    replacement.load(TagValueInput.create(ProblemReporter.DISCARDING, airport.registryAccess(), output.buildResult()));
                    require(h, replacement.boardingGate == 1 && replacement.boardingQueue.equals(savedQueue[0]) && replacement.boarding(),
                            "Native save/load must preserve the same passengers and walking progress");
                    craft[0].discard(); require(h, airport.addFreshEntity(replacement), "Reloaded airliner creation failed");
                    craft[0] = replacement;
                }).thenWaitUntil(() -> require(h, craft[0].passengerCount() == 12 && !craft[0].boarding(), "Passengers must finish walking and board"))
                .thenExecute(() -> {
                    var plane = craft[0];
                    var ids = plane.getPassengers().stream().map(entity -> entity.getUUID()).collect(java.util.stream.Collectors.toSet());
                    require(h, ids.equals(savedQueue[0].stream().map(PassengerBoarding.QueuedPassenger::id)
                            .collect(java.util.stream.Collectors.toSet())), "Reload must board the original passengers without duplicates");
                    require(h, plane.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()
                            && plane.getControllingPassenger() == pilot && plane.getPassengers().size() == 13,
                            "Player must enter the pilot seat even after twelve passengers board first");
                    require(h, plane.acceptControls(pilot, new FlightControls(plane.getId(), FlightControls.EXIT)), "Pilot controls rejected");
                    require(h, !pilot.isPassenger() && plane.passengerCount() == 12, "Pilot exit must not eject the passengers");
                    plane.discard(); plane.ejectPassengers();
                    var airport = server.getLevel(AirportService.DIMENSION);
                    airport.setChunkForced(10, 11, false); airport.setChunkForced(10, 12, false);
                    AirportService.leave(pilot);
                }).thenSucceed();
    }

    @GameTest
    public void airlinerSeatsRotateAndReserveIndependentPilotSeat(GameTestHelper h) {
        var level = h.getLevel();
        var plane = AviationMod.AIRLINER.create(level, EntitySpawnReason.COMMAND);
        plane.setPos(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 2)))); level.addFreshEntity(plane);
        for (int i = 0; i < 12; i++) {
            var passenger = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND); level.addFreshEntity(passenger);
            require(h, passenger.startRiding(plane), "Twelve passenger seats must be available");
        }
        var extra = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND); level.addFreshEntity(extra);
        require(h, !extra.startRiding(plane), "A thirteenth NPC passenger must be rejected");
        var pilot = h.makeMockServerPlayerInLevel();
        pilot.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        require(h, plane.interact(pilot, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction()
                && plane.getControllingPassenger() == pilot, "Pilot seat must remain free when the passenger cabin is full");
        var first = plane.getPassengers().getFirst();
        plane.setYRot(0); var firstOffset = plane.getPassengerRidingPosition(first).subtract(plane.position());
        plane.setYRot(180); var turnedOffset = plane.getPassengerRidingPosition(first).subtract(plane.position());
        require(h, firstOffset.add(turnedOffset).horizontalDistance() < 0.001, "Seats must turn with the aircraft");
        require(h, plane.getPassengers().stream().map(plane::getPassengerRidingPosition).distinct().count() == 13,
                "Pilot and passengers must occupy distinct seats");
        require(h, plane.acceptControls(pilot, new FlightControls(plane.getId(), FlightControls.FORWARD)), "Passenger order must not prevent pilot controls");
        h.succeed();
    }
}
