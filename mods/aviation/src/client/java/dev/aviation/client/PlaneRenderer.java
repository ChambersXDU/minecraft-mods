package dev.aviation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aviation.AviationMod;
import dev.aviation.PlaneEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

public final class PlaneRenderer extends EntityRenderer<PlaneEntity, PlaneRenderState> {
    private final PlaneModel model;
    private final Identifier texture;
    public PlaneRenderer(EntityRendererProvider.Context context, boolean fighter) {
        this(context, fighter ? 1 : 0);
    }
    public PlaneRenderer(EntityRendererProvider.Context context, int kind) {
        super(context); model = new PlaneModel(kind);
        texture = AviationMod.id("textures/entity/" + (kind == 2 ? "airliner" : kind == 1 ? "fighter" : "plane") + ".png");
        shadowRadius = kind == 2 ? 4 : 2.5F;
    }
    @Override public PlaneRenderState createRenderState() { return new PlaneRenderState(); }
    @Override protected AABB getBoundingBoxForCulling(PlaneEntity entity, float partialTick) {
        // Wings, nose and tail extend well beyond the compact physical collision box.
        double reach = entity.airliner() ? 7 : 4;
        return super.getBoundingBoxForCulling(entity, partialTick).inflate(reach, 6, reach);
    }
    @Override public void extractRenderState(PlaneEntity entity, PlaneRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.yaw = Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
        state.pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot()); state.bank = entity.bank();
        var client = Minecraft.getInstance();
        state.cockpitView = client.player != null && client.getCameraEntity() == client.player
                && client.player.getVehicle() == entity && client.options.getCameraType().isFirstPerson();
        state.propeller = state.ageInTicks * (entity.speedKmh() > 1 ? 1.7F : 0);
    }
    @Override public void submit(PlaneRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        // The pilot needs an unobstructed forward view at every pitch and bank angle.
        // Hiding only the cockpit glass leaves the nose/fuselage visible from inside.
        if (state.cockpitView) return;
        pose.pushPose(); pose.rotateDegrees(Axis.YP, 180 - state.yaw);
        pose.rotateDegrees(Axis.XP, -state.pitch); pose.rotateDegrees(Axis.ZP, state.bank); pose.scale(1, -1, 1);
        collector.submitModel(model, state, pose, texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose(); super.submit(state, pose, collector, camera);
    }
}
