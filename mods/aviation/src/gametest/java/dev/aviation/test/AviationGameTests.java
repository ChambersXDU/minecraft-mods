package dev.aviation.test;

import com.mojang.serialization.JsonOps;
import dev.aviation.*;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class AviationGameTests {
    @GameTest
    public void descentKeepsUpdatingPilotPositionAndTracking(GameTestHelper h) {
        for (var type : List.of(AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER)) {
            var f = fixture(h, type);
            f.player().connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(true, false, false, false, false, true, false)));
            for (int i = 1; i <= 12; i++) {
                f.plane().setPos(f.origin().add(i * 30, 12, 0));
                f.player().rideTick();
                require(h, f.player().getVehicle() == f.plane(), "Shift+W must retain the pilot during the native passenger tick");
                require(h, f.player().position().distanceTo(f.plane().position()) < 8,
                        "Native passenger ticking must keep moving the pilot with the aircraft, so tracking and chunk streaming cannot leave it behind");
            }
            f.plane().acceptControls(f.player(), new FlightControls(f.plane().getId(), FlightControls.EXIT));
            require(h, !f.player().isPassenger(), "Explicit exit must remain usable after retained passenger ticks");
        }
        h.succeed();
    }

    @GameTest
    public void slowTouchdownInputOrderingDoesNotEjectPilot(GameTestHelper h) {
        for (var type : List.of(AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER)) {
            var f = fixture(h, type);
            // Native input is delivered first; no custom flight packet is needed to retain the pilot.
            f.player().connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(true, false, false, false, false, true, false)));
            f.player().stopRiding();
            require(h, f.player().getVehicle() == f.plane(), "Native Shift+W must not eject a stopped pilot before the controls packet arrives");
            for (int i = 0; i < 12; i++) f.plane().tick();
            f.player().stopRiding();
            require(h, f.player().getVehicle() == f.plane(), "A controls timeout must not turn Shift+W into an unintended dismount");
            f.plane().acceptControls(f.player(), new FlightControls(f.plane().getId(), FlightControls.EXIT));
            require(h, !f.player().isPassenger(), "Explicit V must remain available even with native Shift+W held");
            var parked = fixture(h, type);
            parked.player().connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(false, false, false, false, false, true, false)));
            parked.player().stopRiding();
            require(h, !parked.player().isPassenger(), "Shift without W must still allow a parked pilot to dismount");
        }
        h.succeed();
    }

    @GameTest
    public void landingCollisionStopsAircraftWithoutDeletingIt(GameTestHelper h) {
        for (var type : List.of(AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER)) {
            var f = fixture(h, type);
            // Use an already damaged aircraft: ordinary touchdown must not silently finish it off.
            f.plane().hurtServer(h.getLevel(), f.plane().damageSources().generic(), 159);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
            f.plane().saveWithoutId(output); output.putFloat("SpeedKmh", 260); output.putBoolean("Airborne", true);
            f.plane().load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
            f.plane().setPos(f.origin().add(0, 0.1, 0));
            for (int y = 121; y < 127; y++) for (int z = -4; z <= 4; z++)
                h.getLevel().setBlock(BlockPos.containing(f.origin().add(4, y - 121, z)), Blocks.STONE.defaultBlockState(), 2);
            drive(h, f, FlightControls.FORWARD | FlightControls.DESCEND);
            require(h, f.plane().horizontalCollision && f.plane().speedKmh() == 0, "Landing obstacle must bring the aircraft to a stop");
            require(h, !f.plane().isRemoved() && f.player().getVehicle() == f.plane(), "A landing collision must not delete a damaged aircraft or drop its pilot");
            require(h, !f.plane().hurtServer(h.getLevel(), f.plane().damageSources().fall(), 1000) && !f.plane().isRemoved(),
                    "Fall damage must not destroy an aircraft during descent");
            // Ordinary combat still uses aircraft health.
            f.plane().hurtServer(h.getLevel(), f.plane().damageSources().generic(), 2);
            require(h, f.plane().isRemoved(), "Combat damage must retain normal aircraft destruction");
        }
        h.succeed();
    }

    @GameTest
    public void landingAcrossUnloadedTerrainKeepsAircraftAndPilot(GameTestHelper h) {
        var airport = h.getLevel().getServer().getLevel(AirportService.DIMENSION);
        for (var type : List.of(AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER)) {
            int chunkX = 62000 + ++arenaCounter * 20;
            int chunkZ = 62000;
            airport.getChunk(chunkX, chunkZ);
            // The far edge of the aircraft's footprint will enter an ungenerated chunk.
            require(h, !airport.getChunkSource().hasChunk(chunkX + 1, chunkZ), "Regression needs an unloaded destination chunk");
            var plane = type.create(airport, EntitySpawnReason.COMMAND);
            plane.setPos(chunkX * 16 + 12.5, 64.1, chunkZ * 16 + 8.5); plane.setYRot(-90);
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, airport.registryAccess());
            plane.saveWithoutId(output); output.putFloat("SpeedKmh", 260); output.putBoolean("Airborne", true);
            plane.load(TagValueInput.create(ProblemReporter.DISCARDING, airport.registryAccess(), output.buildResult()));
            var p = player(h);
            p.teleport(new net.minecraft.world.level.portal.TeleportTransition(airport, plane.position(), Vec3.ZERO, -90, 0,
                    net.minecraft.world.level.portal.TeleportTransition.PLACE_PORTAL_TICKET));
            require(h, airport.addFreshEntity(plane) && plane.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction(), "Regression plane must have a real pilot");
            require(h, plane.acceptControls(p, new FlightControls(plane.getId(), FlightControls.FORWARD | FlightControls.DESCEND)), "Landing controls rejected");
            plane.tick();
            require(h, plane.getY() >= 64 && plane.onGround() && !plane.airborne(),
                    "Landing across an unloaded chunk must collide with the real flat ground, not pass through it");
            require(h, !plane.isRemoved() && p.getVehicle() == plane, "Aircraft and pilot must survive touchdown");
            for (int i = 0; i < 100; i++) {
                plane.acceptControls(p, new FlightControls(plane.getId(), FlightControls.FORWARD | FlightControls.DESCEND));
                plane.tick();
            }
            require(h, !plane.isRemoved() && plane.getY() >= 64 && p.getVehicle() == plane,
                    "Aircraft must remain after braking through subsequent chunks");
            plane.discard(); p.stopRiding();
            p.teleport(new net.minecraft.world.level.portal.TeleportTransition(h.getLevel(), Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))),
                    Vec3.ZERO, 0, 0, net.minecraft.world.level.portal.TeleportTransition.PLACE_PORTAL_TICKET));
        }
        h.succeed();
    }

    @GameTest
    public void airportSuppressesAutomaticSpawningWithoutChangingOtherDimensions(GameTestHelper h) {
        var outside = h.getLevel();
        var airport = outside.getServer().getLevel(AirportService.DIMENSION);
        require(h, airport != null, "Airport must be loaded");
        var originalRules = outside.getGameRules();
        boolean spawnMobs = originalRules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS);
        boolean spawnersWork = originalRules.get(net.minecraft.world.level.gamerules.GameRules.SPAWNER_BLOCKS_WORK);
        var outsideChunk = net.minecraft.world.level.ChunkPos.containing(h.absolutePos(new BlockPos(1500, 0, 1500)));
        var airportChunk = new net.minecraft.world.level.ChunkPos(28, 16);
        outside.setChunkForced(outsideChunk.x(), outsideChunk.z(), true);
        airport.setChunkForced(airportChunk.x(), airportChunk.z(), true);
        h.startSequence().thenIdle(10).thenExecute(() -> {
            var airportAccess = (dev.aviation.test.mixin.ServerLevelSpawnersAccessor) airport;
            var outsideAccess = (dev.aviation.test.mixin.ServerLevelSpawnersAccessor) outside;
            var airportSpawners = airportAccess.aviation$getCustomSpawners();
            var outsideSpawners = outsideAccess.aviation$getCustomSpawners();
            try {
                require(h, !airport.canSpawnEntitiesInChunk(airportChunk), "Airport must reject native natural spawning in a ticking chunk");
                require(h, outside.canSpawnEntitiesInChunk(outsideChunk), "Other dimensions must retain native natural-spawn eligibility");
                require(h, !airport.isSpawnerBlockEnabled() && !airport.isSpawningMonsters(), "Airport must disable spawners and monsters");
                require(h, outside.isSpawnerBlockEnabled() == spawnersWork, "Overworld spawners must retain their game rule");
                var calls = new int[2];
                airportAccess.aviation$setCustomSpawners(List.of((level, enemies) -> calls[0]++));
                outsideAccess.aviation$setCustomSpawners(List.of((level, enemies) -> calls[1]++));
                airport.tickCustomSpawners(true); outside.tickCustomSpawners(true);
                require(h, calls[0] == 0 && calls[1] == 1, "Special spawners must be suppressed only in the airport");
                require(h, airport.getGameRules() == originalRules && originalRules.get(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS) == spawnMobs,
                        "Airport suppression must not replace or modify shared game rules");
                var passenger = EntityTypes.VILLAGER.create(airport, EntitySpawnReason.EVENT);
                passenger.setPos(450.5, 64, 260.5);
                require(h, airport.addFreshEntity(passenger), "Passenger creation must remain allowed");
                passenger.discard();
            } finally {
                airportAccess.aviation$setCustomSpawners(airportSpawners);
                outsideAccess.aviation$setCustomSpawners(outsideSpawners);
                outside.setChunkForced(outsideChunk.x(), outsideChunk.z(), false);
                airport.setChunkForced(airportChunk.x(), airportChunk.z(), false);
            }
        }).thenSucceed();
    }

    private static int arenaCounter;
    private record Fixture(ServerPlayer player, PlaneEntity plane, Vec3 origin) {}
    private void require(GameTestHelper h, boolean value, String message) {
        if (!value) throw h.assertionException(Component.literal(message));
    }
    private ServerPlayer player(GameTestHelper h) {
        var p = h.makeMockServerPlayerInLevel(); p.setGameMode(GameType.SURVIVAL);
        p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket()); return p;
    }
    private Fixture fixture(GameTestHelper h, boolean fighter) {
        return fixture(h, fighter ? AviationMod.FIGHTER : AviationMod.PLANE);
    }
    private Fixture fixture(GameTestHelper h, net.minecraft.world.entity.EntityType<PlaneEntity> type) {
        var origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2000, 0, 3000 + ++arenaCounter * 1000)));
        origin = new Vec3(origin.x, 121, origin.z);
        var p = player(h); p.setPos(origin);
        var plane = type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        plane.setPos(origin); plane.setYRot(-90); h.getLevel().addFreshEntity(plane);
        surface(h, plane);
        require(h, plane.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO).consumesAction() && p.getVehicle() == plane,
                "Right-click must mount the real aircraft");
        require(h, !plane.isClientAuthoritative() && plane.isLocalInstanceAuthoritative() && plane.canSimulateMovement(),
                "A mounted aircraft must retain server authority, instead of allowing competing client movement");
        return new Fixture(p, plane, origin);
    }
    private void surface(GameTestHelper h, PlaneEntity plane) {
        surface(h, plane, AviationMod.RUNWAY);
    }
    private void surface(GameTestHelper h, PlaneEntity plane, Block block) {
        int x = (int) Math.floor(plane.getX()), z = (int) Math.floor(plane.getZ());
        for (int dx = -4; dx <= 12; dx++) for (int dz = -4; dz <= 4; dz++)
            h.getLevel().setBlock(new BlockPos(x + dx, 120, z + dz), block.defaultBlockState(), 2);
    }
    private void tick(GameTestHelper h, Fixture f, int flags) {
        surface(h, f.plane());
        drive(h, f, flags);
    }
    private void drive(GameTestHelper h, Fixture f, int flags) {
        require(h, f.plane().acceptControls(f.player(), new FlightControls(f.plane().getId(), flags)), "Pilot controls rejected");
        f.plane().tick();
    }

    @GameTest
    public void bothAircraftNeedRunwayEvenAboveTakeoffSpeed(GameTestHelper h) {
        for (var type : List.of(AviationMod.PLANE, AviationMod.FIGHTER, AviationMod.AIRLINER)) {
            for (Block ground : new Block[]{Blocks.STONE, Blocks.GRASS_BLOCK, AviationMod.APRON}) {
                var f = fixture(h, type);
                for (int i = 0; i < 300; i++) {
                    surface(h, f.plane(), ground);
                    drive(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
                    require(h, !f.plane().airborne() && f.plane().getY() == 121,
                            "Ordinary ground or apron must not permit takeoff, even with W+Space");
                }
                require(h, f.plane().speedKmh() >= 100 && !f.plane().hasRunwayBelow(), "Negative test must reach takeoff speed without a runway");
                tick(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
                require(h, f.plane().airborne() && f.plane().getY() > 121, "Taxiing onto a real runway must allow takeoff");
                for (int i = 0; i < 10; i++) {
                    surface(h, f.plane(), Blocks.GRASS_BLOCK);
                    drive(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
                }
                require(h, f.plane().airborne(), "Runway restriction must not interrupt an aircraft already in flight");
            }
        }
        h.succeed();
    }

    @GameTest
    public void runwayMarkingsWorkButOneIsolatedBlockDoesNot(GameTestHelper h) {
        var f = fixture(h, false);
        for (int i = 0; i < 230; i++) tick(h, f, FlightControls.FORWARD);
        f.plane().setPos(f.origin());
        surface(h, f.plane(), Blocks.STONE);
        var center = BlockPos.containing(f.origin()).below();
        h.getLevel().setBlock(center, AviationMod.RUNWAY.defaultBlockState(), 2);
        require(h, !f.plane().hasRunwayBelow(), "One runway block surrounded by stone must not qualify");
        drive(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        require(h, !f.plane().airborne(), "Partial runway support must not permit takeoff");
        f.plane().setPos(f.origin()); surface(h, f.plane());
        h.getLevel().setBlock(center, AviationMod.LIGHT.defaultBlockState(), 2);
        h.getLevel().setBlock(center.east(), AviationMod.YELLOW.defaultBlockState(), 2);
        h.getLevel().setBlock(center.west(), AviationMod.WHITE.defaultBlockState(), 2);
        require(h, f.plane().hasRunwayBelow(), "Runway lights and painted markings must not break runway support");
        drive(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        require(h, f.plane().airborne(), "A marked runway must allow takeoff");
        h.succeed();
    }

    @GameTest
    public void accelerateTakeOffAtHundredAndLandUsingShiftW(GameTestHelper h) {
        var f = fixture(h, false); var plane = f.plane();
        for (int i = 0; i < 200; i++) tick(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        require(h, plane.speedKmh() < 100 && !plane.airborne(), "Space must not launch below 100 km/h");
        while (!plane.airborne()) tick(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        require(h, plane.speedKmh() >= 100 && plane.getY() > 121, "100 km/h plus Space must actually lift the plane");
        for (int i = 0; i < 30; i++) tick(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        double height = plane.getY(); float yaw = plane.getYRot();
        tick(h, f, FlightControls.FORWARD | FlightControls.RIGHT);
        require(h, plane.getYRot() > yaw, "D must turn the aircraft");
        // Native Shift tries to dismount: our mixin must keep the pilot seated in flight.
        f.player().stopRiding();
        require(h, f.player().getVehicle() == plane, "Shift must not eject the pilot while airborne");
        tick(h, f, FlightControls.FORWARD | FlightControls.DESCEND);
        require(h, plane.getY() < height, "Shift+W must descend");
        for (int i = 0; i < 150; i++) tick(h, f, FlightControls.FORWARD | FlightControls.DESCEND);
        require(h, !plane.airborne() && Math.abs(plane.getY() - 121) < 0.01 && plane.canExit(), "Descent must land and stop on the runway");
        require(h, f.player().getHealth() == 20, "A normal landing must not damage the pilot");
        plane.acceptControls(f.player(), new FlightControls(plane.getId(), FlightControls.EXIT));
        require(h, !f.player().isPassenger(), "Stopped aircraft must allow V to exit");
        h.succeed();
    }

    @GameTest
    public void nativeShiftBrakesAndExitsWhileShiftWRetainsPilot(GameTestHelper h) {
        for (boolean fighter : new boolean[]{false, true}) {
            var f = fixture(h, fighter);
            for (int i = 0; i < 100; i++) tick(h, f, FlightControls.FORWARD);
            require(h, f.plane().speedKmh() > 30 && !f.plane().airborne(), "Regression requires a moving grounded plane");
            f.player().connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(false, false, false, false, false, true, false)));
            for (int i = 0; i < 80 && f.player().isPassenger(); i++) {
                tick(h, f, FlightControls.DESCEND);
                f.player().rideTick();
            }
            require(h, !f.player().isPassenger() && f.plane().canExit(), "Shift alone must brake then dismount both aircraft");
            var landing = fixture(h, fighter);
            landing.player().connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                    new Input(true, false, false, false, false, true, false)));
            tick(h, landing, FlightControls.FORWARD | FlightControls.DESCEND);
            landing.player().rideTick();
            require(h, landing.player().getVehicle() == landing.plane(), "Shift+W must retain the pilot even at a stopped landing");
            landing.plane().acceptControls(landing.player(), new FlightControls(landing.plane().getId(),
                    FlightControls.FORWARD | FlightControls.DESCEND | FlightControls.EXIT));
            require(h, !landing.player().isPassenger(), "Explicit V exit must still work after a landing");
        }
        h.succeed();
    }

    @GameTest
    public void fighterIsFasterAndUnauthorizedControlsAreRejected(GameTestHelper h) {
        var civilian = fixture(h, false); var fighter = fixture(h, true);
        for (int tick = 0; tick < 1050; tick++) {
            civilian.plane().setPos(civilian.origin()); fighter.plane().setPos(fighter.origin());
            tick(h, civilian, FlightControls.FORWARD); tick(h, fighter, FlightControls.FORWARD);
        }
        require(h, Math.abs(civilian.plane().speedKmh() - 260) < 0.1, "Civil aircraft top speed must be 260 km/h");
        require(h, Math.abs(fighter.plane().speedKmh() - 700) < 0.1, "Fighter must have the higher 700 km/h top speed");
        var stranger = player(h);
        require(h, !fighter.plane().acceptControls(stranger, new FlightControls(fighter.plane().getId(), FlightControls.FIRE)),
                "A non-pilot must not control or fire another player's aircraft");
        require(h, !fighter.plane().acceptControls(fighter.player(), new FlightControls(-1, FlightControls.FIRE)),
                "An incorrect entity ID must not control the plane");
        h.succeed();
    }

    @GameTest
    public void fighterCannonHitsTargetsAndStopsAtSolidWalls(GameTestHelper h) {
        var f = fixture(h, true); var from = f.plane().position();
        var target = EntityTypes.IRON_GOLEM.create(h.getLevel(), EntitySpawnReason.COMMAND);
        target.setPos(from.add(16, 0, 0)); h.getLevel().addFreshEntity(target);
        float health = target.getHealth();
        require(h, f.plane().fireCannon(f.player()) && target.getHealth() == health - 12, "Fighter cannon must deal 12 damage along its aim");
        target.damageCooldownTime = 0;
        for (int y = 121; y <= 125; y++) for (int z = -3; z <= 3; z++)
            h.getLevel().setBlock(BlockPos.containing(from.add(9, y - 121, z)), Blocks.STONE.defaultBlockState(), 2);
        health = target.getHealth(); f.plane().fireCannon(f.player());
        require(h, target.getHealth() == health, "Cannon must not hit through a solid wall");
        var civilian = fixture(h, false);
        require(h, !civilian.plane().fireCannon(civilian.player()), "Civil aircraft must not have a cannon");
        h.succeed();
    }

    @GameTest
    public void planeStateSurvivesVanillaSaveAndReload(GameTestHelper h) {
        var f = fixture(h, true);
        for (int i = 0; i < 180; i++) tick(h, f, FlightControls.FORWARD | FlightControls.CLIMB);
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess());
        f.plane().saveWithoutId(output);
        var copy = AviationMod.FIGHTER.create(h.getLevel(), EntitySpawnReason.LOAD);
        copy.load(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), output.buildResult()));
        require(h, copy.fighter() && copy.speedKmh() == f.plane().speedKmh() && copy.airborne() == f.plane().airborne(),
                "Saved aircraft must retain type, speed and airborne state");
        h.succeed();
    }

    @GameTest
    public void aircraftItemSpawnsAndRecipesMatch(GameTestHelper h) {
        var p = player(h); var pos = h.absolutePos(new BlockPos(3000, 120, 2000));
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
            h.getLevel().setBlock(pos.offset(dx, 0, dz), AviationMod.RUNWAY.defaultBlockState(), 2);
        p.setPos(Vec3.atBottomCenterOf(pos).add(0, 1, 0));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AviationMod.PLANE_ITEM));
        var context = new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        require(h, AviationMod.PLANE_ITEM.useOn(context).consumesAction() && p.getMainHandItem().isEmpty(), "Placing a plane must consume the item");
        var iron = new ItemStack(Items.IRON_INGOT); var glass = new ItemStack(Items.GLASS);
        var input = CraftingInput.of(3, 3, List.of(iron, iron, iron, glass, new ItemStack(Items.FURNACE), glass,
                iron, new ItemStack(Items.REDSTONE), iron));
        var recipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        require(h, recipe.value().assemble(input).is(AviationMod.PLANE_ITEM), "Civil plane crafting recipe is incorrect");
        var fighterInput = CraftingInput.of(3, 3, List.of(iron, new ItemStack(Items.DIAMOND), iron, glass, new ItemStack(Items.FURNACE), glass,
                iron, new ItemStack(Items.REDSTONE), iron));
        var fighterRecipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, fighterInput, h.getLevel()).orElseThrow();
        require(h, fighterRecipe.value().assemble(fighterInput).is(AviationMod.FIGHTER_ITEM), "Fighter recipe is incorrect");
        var airlinerInput = CraftingInput.of(3, 3, List.of(iron, iron, iron, iron, new ItemStack(AviationMod.PLANE_ITEM), iron,
                iron, new ItemStack(Items.REDSTONE), iron));
        var airlinerRecipe = h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, airlinerInput, h.getLevel()).orElseThrow();
        require(h, airlinerRecipe.value().assemble(airlinerInput).is(AviationMod.AIRLINER_ITEM), "Airliner recipe is incorrect");
        h.succeed();
    }

    @GameTest(maxTicks = 6000)
    public void placingGatewayBuildsAirportTeleportsAndReturns(GameTestHelper h) {
        var p = player(h); var origin = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        p.setPos(origin); var server = h.getLevel().getServer();
        require(h, server.getLevel(AirportService.DIMENSION) != null, "Custom airport dimension did not load");
        // Place the real BlockItem: this must invoke setPlacedBy and queue teleport automatically.
        var support = p.blockPosition().offset(3, -2, 0);
        h.getLevel().setBlock(support, Blocks.STONE.defaultBlockState(), 2);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AviationMod.GATE));
        var placement = new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false));
        require(h, AviationMod.GATE.asItem().useOn(placement).consumesAction()
                && h.getLevel().getBlockState(support.above()).is(AviationMod.GATE), "Real airport block placement failed");
        h.startSequence().thenWaitUntil(() -> {
            require(h, p.level().dimension().equals(AirportService.DIMENSION), "Airport construction/teleport is still pending");
        }).thenExecute(() -> {
            var airport = server.getLevel(AirportService.DIMENSION);
            require(h, airport.getBlockState(new BlockPos(200, 63, 55)).is(AviationMod.RUNWAY), "Real custom runway block is missing");
            require(h, airport.getBlockState(new BlockPos(200, 63, 49)).is(AviationMod.WHITE), "Runway edge marking is missing");
            require(h, airport.getBlockState(new BlockPos(200, 63, 148)).is(AviationMod.YELLOW), "Taxiway centerline is missing");
            require(h, airport.getBlockState(new BlockPos(100, 70, 230)).is(Blocks.STAINED_GLASS.lightBlue()), "Terminal glass facade is missing");
            require(h, airport.getBlockState(new BlockPos(362, 98, 218)).is(Blocks.SMOOTH_QUARTZ), "Control tower roof is missing");
            require(h, airport.getBlockState(new BlockPos(0, 63, 0)).is(Blocks.GRASS_BLOCK), "Airport ground must align with runway at Y=63");
            for (var sample : new BlockPos[]{new BlockPos(80, 63, 150), new BlockPos(510, 63, 300), new BlockPos(16, 63, 20)})
                require(h, airport.getBlockState(sample).is(Blocks.GRASS_BLOCK) && airport.getBlockState(sample.above()).isAir(),
                        "Airport open ground must be flat at Y=63");
            var saved = AirportService.data(server);
            var json = AirportData.CODEC.encodeStart(JsonOps.INSTANCE, saved).getOrThrow();
            var loaded = AirportData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            require(h, loaded.cursor == AirportLayout.plan().size() && loaded.commissioned && loaded.airlinerCommissioned
                    && loaded.returns.containsKey(p.getUUID().toString()), "Build progress and return location must survive saves");
            var runway = new BlockPos(60, 63, 64);
            // Mock players do not stream chunks like real clients. Make the test's runway
            // chunk entity-ticking before querying the aircraft created by the real item.
            airport.setChunkForced(3, 4, true);
            p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            p.setPos(60.5, 64, 68.5); p.setYRot(-90);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AviationMod.PLANE_ITEM));
            var planePlacement = new UseOnContext(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(runway), Direction.UP, runway, false));
            require(h, planePlacement.getLevel() == airport, "Plane placement context must use the airport dimension");
            require(h, AviationMod.PLANE_ITEM.useOn(planePlacement).consumesAction(), "Plane item must place on the actual airport runway");
        }).thenIdle(10).thenExecute(() -> {
            var airport = server.getLevel(AirportService.DIMENSION);
            var aircraft = airport.getEntitiesOfClass(PlaneEntity.class, new AABB(59, 64, 63, 62, 66, 66));
            if (aircraft.size() != 1) {
                var visible = new java.util.ArrayList<String>();
                for (var entity : airport.getAllEntities()) if (entity instanceof PlaneEntity)
                    visible.add(entity.position().toString());
                require(h, false, "Placed runway aircraft was not found; count=" + aircraft.size() + "; visible aircraft=" + visible);
            }
            var plane = aircraft.getFirst(); plane.interact(p, InteractionHand.MAIN_HAND, Vec3.ZERO);
            for (int i = 0; i < 240; i++) {
                require(h, plane.acceptControls(p, new FlightControls(plane.getId(), FlightControls.FORWARD | FlightControls.CLIMB)),
                        "Airport pilot controls rejected");
                plane.tick();
            }
            require(h, plane.airborne() && plane.getY() > 64, "Plane placed on the generated airport runway must actually take off");
            plane.discard(); p.stopRiding();
            airport.setChunkForced(3, 4, false);
            AirportService.leave(p);
            require(h, p.level() == h.getLevel() && p.position().distanceTo(origin) < 0.01, "Airport return must restore original dimension and position");
        }).thenSucceed();
    }
}
