package dev.aviation.client;

import dev.aviation.AviationMod;
import dev.aviation.FlightControls;
import dev.aviation.PlaneEntity;
import dev.aviation.PassengerBoarding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

public final class AviationClient implements ClientModInitializer {
    private CameraType previousCameraType;
    private PlaneEntity cockpitPlane;
    private float lastPlaneYaw;

    private void updateCockpit(Minecraft client) {
        var plane = client.player != null && client.player.getVehicle() instanceof PlaneEntity aircraft ? aircraft : null;
        if (plane == null) {
            if (previousCameraType != null) client.options.setCameraType(previousCameraType);
            previousCameraType = null; cockpitPlane = null;
            return;
        }
        if (cockpitPlane != plane) {
            if (previousCameraType == null) previousCameraType = client.options.getCameraType();
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.player.setYRot(plane.getYRot()); client.player.yRotO = plane.getYRot();
            client.player.setXRot(0); client.player.xRotO = 0;
            client.player.setYHeadRot(plane.getYRot());
            cockpitPlane = plane; lastPlaneYaw = plane.getYRot();
        } else {
            float turn = Mth.wrapDegrees(plane.getYRot() - lastPlaneYaw);
            client.player.setYRot(client.player.getYRot() + turn);
            client.player.yRotO += turn;
            client.player.setYHeadRot(client.player.getYHeadRot() + turn);
            lastPlaneYaw = plane.getYRot();
        }
    }

    @Override public void onInitializeClient() {
        EntityRendererRegistry.register(AviationMod.PLANE, context -> new PlaneRenderer(context, false));
        EntityRendererRegistry.register(AviationMod.FIGHTER, context -> new PlaneRenderer(context, true));
        EntityRendererRegistry.register(AviationMod.AIRLINER, context -> new PlaneRenderer(context, 2));
        var category = KeyMapping.Category.register(AviationMod.id("flight"));
        var fire = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aviation.cannon", InputConstants.KEY_G, category));
        var exit = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.aviation.exit", InputConstants.KEY_V, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateCockpit(client);
            if (client.player == null || !(client.player.getVehicle() instanceof PlaneEntity plane)) return;
            int flags = 0;
            if (client.gui.screen() == null) {
                if (client.options.keyUp.isDown()) flags |= FlightControls.FORWARD;
                if (client.options.keyDown.isDown()) flags |= FlightControls.BRAKE;
                if (client.options.keyLeft.isDown()) flags |= FlightControls.LEFT;
                if (client.options.keyRight.isDown()) flags |= FlightControls.RIGHT;
                if (client.options.keyJump.isDown()) flags |= FlightControls.CLIMB;
                if (client.options.keyShift.isDown()) flags |= FlightControls.DESCEND;
                if (fire.isDown()) flags |= FlightControls.FIRE;
                if (exit.consumeClick()) flags |= FlightControls.EXIT;
            }
            ClientPlayNetworking.send(new FlightControls(plane.getId(), flags));
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, AviationMod.id("flight_hud"), (graphics, delta) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || !(client.player.getVehicle() instanceof PlaneEntity plane)) return;
            int x = graphics.guiWidth() / 2, y = graphics.guiHeight() - 82;
            graphics.fill(x - 151, y - 4, x + 151, y + 36, 0xC0101822);
            String status = plane.airborne() ? "hud.aviation.airborne" : !plane.hasRunwayBelow() ? "hud.aviation.no_runway"
                    : plane.speedKmh() >= 100 ? "hud.aviation.ready" : "hud.aviation.ground";
            graphics.centeredText(client.font, Component.translatable("hud.aviation.flight", (int) plane.speedKmh(), (int) plane.getY(),
                    Component.translatable(status)), x, y, 0xFFFFFFFF);
            graphics.centeredText(client.font, Component.translatable("hud.aviation.keys"), x, y + 12, 0xFFACE2EF);
            if (plane.fighter()) graphics.centeredText(client.font, Component.translatable("hud.aviation.cannon", fire.getTranslatedKeyMessage()), x, y + 24, 0xFFFFDC9C);
            if (plane.airliner()) graphics.centeredText(client.font, Component.translatable("hud.aviation.passengers", plane.passengerCount(),
                    PassengerBoarding.CAPACITY, Component.translatable(plane.boarding() ? "hud.aviation.boarding" : "hud.aviation.boarding_ready")), x, y + 24, 0xFFFFDC9C);
        });
    }
}
