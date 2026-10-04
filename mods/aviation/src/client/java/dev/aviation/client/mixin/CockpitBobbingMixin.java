package dev.aviation.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.aviation.PlaneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CockpitBobbingMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void aviation$stableCockpit(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.player != null && client.getCameraEntity() == client.player
                && client.options.getCameraType().isFirstPerson()
                && client.player.getVehicle() instanceof PlaneEntity) ci.cancel();
    }
}
