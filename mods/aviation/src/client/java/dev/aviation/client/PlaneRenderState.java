package dev.aviation.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class PlaneRenderState extends EntityRenderState {
    public float yaw, pitch, bank, propeller;
    public boolean cockpitView;
}
