package dev.aviation.test.mixin;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.WorldLoader;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** GameTestServer deliberately bakes an empty dimension registry. Load datapack dimensions
 * exactly as the regular server does, so the real airport JSON can be integration tested.
 * This mixin belongs only to the test mod and is never included in the installable JAR. */
@Mixin(GameTestServer.class)
public abstract class AirportTestDimensionsMixin {
    @Redirect(method = "lambda$create$1", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/WorldDimensions;bake(Lnet/minecraft/core/Registry;)Lnet/minecraft/world/level/levelgen/WorldDimensions$Complete;"))
    private static WorldDimensions.Complete aviation$loadAirport(WorldDimensions dimensions,
            Registry<LevelStem> ignored, LevelSettings settings, WorldLoader.DataLoadContext context) {
        return dimensions.bake(context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM));
    }
}
