package dev.aviation.mixin;

import dev.aviation.AirportService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Disable automatic spawning locally, without changing shared world game rules. */
@Mixin(ServerLevel.class)
public abstract class AirportSpawningMixin {
    private boolean aviation$isAirport() {
        return ((ServerLevel) (Object) this).dimension().equals(AirportService.DIMENSION);
    }

    @Inject(method = "canSpawnEntitiesInChunk", at = @At("HEAD"), cancellable = true)
    private void aviation$disableNaturalSpawning(ChunkPos chunk, CallbackInfoReturnable<Boolean> cir) {
        if (aviation$isAirport()) cir.setReturnValue(false);
    }

    @Inject(method = "tickCustomSpawners", at = @At("HEAD"), cancellable = true)
    private void aviation$disableSpecialSpawners(boolean spawnEnemies, CallbackInfo ci) {
        if (aviation$isAirport()) ci.cancel();
    }

    @Inject(method = {"isSpawnerBlockEnabled", "isSpawningMonsters"}, at = @At("HEAD"), cancellable = true)
    private void aviation$disableSpawnerBlocksAndMonsters(CallbackInfoReturnable<Boolean> cir) {
        if (aviation$isAirport()) cir.setReturnValue(false);
    }

    // A thunderstorm can create a skeleton-horse trap outside the normal spawning loop.
    @Redirect(method = "tickThunder", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object aviation$disableThunderstormSpawning(GameRules rules, GameRule<?> rule) {
        if (aviation$isAirport() && rule == GameRules.SPAWN_MOBS) return false;
        return rules.get(rule);
    }
}
