package dev.aviation.mixin;

import dev.aviation.PlaneEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class AircraftDismountMixin {
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void keepPilotDuringDescent(CallbackInfo ci) {
        Entity rider = (Entity) (Object) this;
        // Passenger packets must apply even before the client's speed update arrives.
        if (!rider.level().isClientSide() && rider.getVehicle() instanceof PlaneEntity plane
                && !plane.isRemoved() && plane.keepPilotSeated()) ci.cancel();
    }
}
