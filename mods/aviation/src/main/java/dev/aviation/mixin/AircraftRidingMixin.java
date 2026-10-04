package dev.aviation.mixin;

import dev.aviation.PlaneEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keep the whole passenger tick running when Shift is used as a flight control. */
@Mixin(Player.class)
public abstract class AircraftRidingMixin {
    @Shadow protected abstract boolean wantsToStopRiding();

    @Redirect(method = "rideTick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/player/Player;wantsToStopRiding()Z"))
    private boolean aviation$continueUpdatingPilotPosition(Player player) {
        if (player.getVehicle() instanceof PlaneEntity plane && !plane.isRemoved() && plane.keepPilotSeated()) return false;
        return wantsToStopRiding();
    }
}
