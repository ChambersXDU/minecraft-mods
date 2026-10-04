package dev.aviation.test.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aviation.AviationMod;
import dev.aviation.PlaneEntity;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

public final class AviationClientTests implements FabricClientGameTest {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                client.options.renderDistance().set(5); client.options.simulationDistance().set(5);
            });
            world.getServer().runOnServer(server -> {
                var level = world.getConnection().getServerLevel();
                for (int x = 20; x <= 28; x++) for (int z = 0; z <= 2500; z++)
                    level.setBlock(new BlockPos(x, 63, z), AviationMod.RUNWAY.defaultBlockState(), 2);
            });
            for (var type : new EntityType[]{AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER}) {
                var spawned = new PlaneEntity[1];
                context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer(); var level = player.level();
                    player.teleport(new TeleportTransition(level, new Vec3(24.5, 64, 10.5), Vec3.ZERO, 0, 0,
                            TeleportTransition.PLACE_PORTAL_TICKET));
                    var plane = (PlaneEntity) type.create(level, EntitySpawnReason.COMMAND);
                    plane.setPos(24.5, 64, 14.5); plane.setYRot(0); level.addFreshEntity(plane);
                    spawned[0] = plane;
                });
                world.getConnection().waitForChunksDownload();
                world.getConnection().waitForClientboundEntityUpdates(type);
                context.waitTicks(5);
                context.getInput().lookAt(new BlockPos(24, 65, 14));
                context.waitTicks(3);
                context.takeScreenshot("before-mount-" + type.toShortString());
                context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
                context.waitFor(client -> client.player.getVehicle() instanceof PlaneEntity);
                context.waitTicks(5);
                context.runOnClient(client -> {
                    var plane = (PlaneEntity) client.player.getVehicle();
                    require(client.options.getCameraType() == CameraType.FIRST_PERSON, "Right-click must automatically enter cockpit view");
                    require(!plane.isClientAuthoritative() && !plane.canSimulateMovement(), "Client must not compete with server aircraft physics");
                    require(plane.getInterpolation() != InterpolationHandler.NO_OP, "Client aircraft must use real movement interpolation");
                    var camera = client.gameRenderer.mainCamera();
                    double partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
                    var offset = camera.position().subtract(plane.getPosition((float) partial));
                    require(Math.abs(offset.y - (plane.airliner() ? 3.1 : 1.95)) < 0.2, "Camera must be fixed at the cockpit height");
                    require(Math.abs(offset.z - (plane.airliner() ? 6.0 : plane.fighter() ? 0.9 : 1.2)) < 0.2,
                            "Camera must sit at the front of the cockpit, rather than wobble with passenger position");
                });
                String name = context.computeOnClient(client -> ((PlaneEntity) client.player.getVehicle()).airliner() ? "airliner"
                        : ((PlaneEntity) client.player.getVehicle()).fighter() ? "fighter" : "plane");
                context.takeScreenshot("cockpit-" + name);
                context.getInput().holdKey(options -> options.keyUp);
                double previous = context.computeOnClient(client -> client.player.getVehicle().getZ());
                for (int i = 0; i < 100; i++) {
                    context.waitTick();
                    double position = context.computeOnClient(client -> client.player.getVehicle().getZ());
                    require(position >= previous - 0.02, "Aircraft movement must not repeatedly jump backwards while accelerating");
                    previous = position;
                }
                context.getInput().holdKey(options -> options.keyJump);
                context.waitFor(client -> ((PlaneEntity) client.player.getVehicle()).airborne());
                // A long climb makes touchdown happen after the descent has slowed to near zero.
                // The old short flight never exercised a steep model pitch or stopped touchdown.
                context.waitTicks(350);
                context.getInput().releaseKey(options -> options.keyJump);
                context.waitTicks(10);
                context.takeScreenshot("cockpit-in-flight-" + name);
                context.getInput().holdShift();
                for (int tick = 0; tick < 1200; tick++) {
                    if (context.computeOnClient(client -> {
                        require(client.player.getVehicle() instanceof PlaneEntity, "Descent must retain the pilot on the client");
                        var plane = (PlaneEntity) client.player.getVehicle();
                        return plane.airborne() && plane.getXRot() > 30;
                    })) break;
                    context.waitTick();
                }
                context.runOnClient(client -> require(client.player.getVehicle() instanceof PlaneEntity plane
                        && plane.airborne() && plane.getXRot() > 30, "Long descent must reach a steep pitch before touching down"));
                context.takeScreenshot("steep-descent-" + name);
                context.waitFor(client -> !((PlaneEntity) client.player.getVehicle()).airborne(), 1200);
                context.runOnClient(client -> require(client.player.isPassenger(), "Shift+W landing must retain the pilot"));
                world.getServer().runOnServer(server -> {
                    require(!spawned[0].isRemoved(), "Aircraft must not disappear during a long descent");
                    require(world.getConnection().getServerPlayer().getVehicle() == spawned[0],
                            "A slow touchdown must not drop the pilot");
                    require(world.getConnection().getServerPlayer().getHealth() == 20,
                            "The pilot must remain unharmed by the landing");
                    require(world.getConnection().getServerPlayer().position().distanceTo(spawned[0].position()) < 8,
                            "The pilot must follow the aircraft throughout Shift+W descent and landing");
                });
                context.takeScreenshot("landed-in-cockpit-" + name);
                context.getInput().releaseKey(options -> options.keyUp);
                context.waitFor(client -> !client.player.isPassenger());
                context.getInput().releaseShift(); context.waitTicks(3);
                context.runOnClient(client -> require(client.options.getCameraType() == CameraType.THIRD_PERSON_BACK,
                        "Leaving the cockpit must restore the previous camera view"));
                context.runOnClient(client -> require(client.level.getEntity(spawned[0].getId()) instanceof PlaneEntity,
                        "The parked aircraft must remain present on the client after dismount"));
                world.getServer().runOnServer(server -> {
                    var player = world.getConnection().getServerPlayer();
                    player.teleport(new TeleportTransition(player.level(), spawned[0].position().add(14, 0, -12), Vec3.ZERO,
                            0, 0, TeleportTransition.PLACE_PORTAL_TICKET));
                });
                context.waitTicks(5);
                context.getInput().lookAt(BlockPos.containing(spawned[0].position().add(0, 1, 0)));
                context.waitTicks(3);
                context.takeScreenshot("parked-after-landing-" + name);
                world.getServer().runOnServer(server -> {
                    require(!spawned[0].isRemoved(), "Dismount must not destroy the parked aircraft");
                    spawned[0].discard();
                });
            }
        }
    }
}
