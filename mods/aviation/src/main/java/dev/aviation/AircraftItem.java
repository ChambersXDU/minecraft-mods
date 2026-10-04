package dev.aviation;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

public final class AircraftItem extends Item {
    private final EntityType<PlaneEntity> type;
    public AircraftItem(Properties properties, boolean fighter) { this(properties, fighter ? AviationMod.FIGHTER : AviationMod.PLANE); }
    public AircraftItem(Properties properties, EntityType<PlaneEntity> type) { super(properties); this.type = type; }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        var plane = type.create(context.getLevel(), EntitySpawnReason.SPAWN_ITEM_USE);
        if (plane == null) return InteractionResult.FAIL;
        var pos = context.getClickedPos().relative(context.getClickedFace());
        plane.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        plane.setYRot(context.getPlayer() == null ? 0 : context.getPlayer().getYRot());
        if (!context.getLevel().noCollision(plane, plane.getBoundingBox())) return InteractionResult.FAIL;
        if (!context.getLevel().addFreshEntity(plane)) return InteractionResult.FAIL;
        if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.SUCCESS;
    }
}
