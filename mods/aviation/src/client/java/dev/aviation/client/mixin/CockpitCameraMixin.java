package dev.aviation.client.mixin;

import dev.aviation.PlaneEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CockpitCameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void aviation$anchorToAircraft(float partialTick, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.getCameraEntity() != client.player
                || !client.options.getCameraType().isFirstPerson()
                || !(client.player.getVehicle() instanceof PlaneEntity plane)) return;
        float yaw = Mth.rotLerp(partialTick, plane.yRotO, plane.getYRot());
        double heading = Math.toRadians(yaw);
        double forward = plane.airliner() ? 6.0 : plane.fighter() ? 0.9 : 1.2;
        setPosition(plane.getPosition(partialTick).add(-Math.sin(heading) * forward,
                plane.airliner() ? 3.1 : 1.95, Math.cos(heading) * forward));
        setRotation(client.player.getViewYRot(partialTick), client.player.getViewXRot(partialTick));
    }
}
