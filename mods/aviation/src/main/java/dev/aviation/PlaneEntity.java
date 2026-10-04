package dev.aviation;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public final class PlaneEntity extends Entity {
    public static final float TAKEOFF_KMH = 100.0F;
    public static final double KMH_PER_BLOCK_TICK = 72.0;
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> AIRBORNE = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> BANK = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BOARDING = SynchedEntityData.defineId(PlaneEntity.class, EntityDataSerializers.BOOLEAN);
    private float health = 160;
    private int controls, controlAge, cannonCooldown, runwayMessageCooldown;
    private boolean pilotExiting;
    int boardingGate = -1;
    boolean boardingFinished;
    final List<PassengerBoarding.QueuedPassenger> boardingQueue = new ArrayList<>();
    final HashMap<UUID, Integer> missingBoarders = new HashMap<>();

    public PlaneEntity(EntityType<? extends PlaneEntity> type, Level level) { super(type, level); }
    public boolean fighter() { return getType() == AviationMod.FIGHTER; }
    public boolean airliner() { return getType() == AviationMod.AIRLINER; }
    public boolean boarding() { return entityData.get(BOARDING); }
    void setBoarding(boolean value) { entityData.set(BOARDING, value); }
    public int passengerCount() { return (int) getPassengers().stream().filter(passenger -> !(passenger instanceof Player)).count(); }
    public float speedKmh() { return entityData.get(SPEED); }
    public boolean airborne() { return entityData.get(AIRBORNE); }
    public float bank() { return entityData.get(BANK); }
    /** Check the actual supporting surface, so moving onto or off a runway changes eligibility. */
    public boolean hasRunwayBelow() {
        var bounds = getBoundingBox();
        int y = (int) Math.floor(bounds.minY - 0.05);
        boolean pavement = false;
        for (int x = (int) Math.floor(bounds.minX + 0.001); x <= (int) Math.floor(bounds.maxX - 0.001); x++) {
            for (int z = (int) Math.floor(bounds.minZ + 0.001); z <= (int) Math.floor(bounds.maxZ - 0.001); z++) {
                BlockState state = level().getBlockState(new BlockPos(x, y, z));
                if (state.is(AviationMod.RUNWAY) || state.is(AviationMod.WHITE)) pavement = true;
                else if (!state.is(AviationMod.YELLOW) && !state.is(AviationMod.LIGHT)) return false;
            }
        }
        return pavement;
    }
    public boolean canExit() { return !airborne() && speedKmh() < 5; }
    public boolean keepPilotSeated() {
        if (pilotExiting) return false;
        if (!canExit() || (pressed(FlightControls.FORWARD) && pressed(FlightControls.DESCEND))) return true;
        // Native Shift input may arrive before our controls packet, especially at a slow touchdown.
        if (getControllingPassenger() instanceof ServerPlayer pilot) {
            var input = pilot.getLastClientInput();
            return input.forward() && input.shift();
        }
        return false;
    }
    private void exitPlane() {
        controls = 0;
        pilotExiting = true;
        try {
            if (getControllingPassenger() != null) getControllingPassenger().stopRiding();
        } finally { pilotExiting = false; }
    }
    @Override protected boolean isLocalClientAuthoritative() { return false; }
    @Override public boolean isClientAuthoritative() { return false; }
    @Override protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }
    @Override public boolean isFlyingVehicle() { return true; }
    @Override public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        // Aircraft descent is controlled flight, rather than a passenger falling on foot.
        resetFallDistance();
        for (var passenger : getPassengers()) passenger.resetFallDistance();
        return false;
    }
    @Override public boolean isPickable() { return !isRemoved(); }
    @Override public boolean isPushable() { return true; }
    @Override public boolean canBeCollidedWith(Entity other) { return !isRemoved() && !hasPassenger(other); }
    @Override protected boolean canAddPassenger(Entity passenger) {
        if (passenger instanceof Player) return getControllingPassenger() == null;
        return airliner() && passenger instanceof LivingEntity && passengerCount() < PassengerBoarding.CAPACITY;
    }
    @Override public LivingEntity getControllingPassenger() {
        return getPassengers().stream().filter(passenger -> passenger instanceof Player)
                .map(passenger -> (LivingEntity) passenger).findFirst().orElse(null);
    }
    @Override protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        if (airliner()) {
            if (passenger instanceof Player) return new Vec3(0, 1.45, -5.5).yRot((float) Math.toRadians(180 - getYRot()));
            int seat = 0;
            for (var occupant : getPassengers()) {
                if (occupant == passenger) break;
                if (!(occupant instanceof Player)) seat++;
            }
            return new Vec3(seat % 2 == 0 ? -0.65 : 0.65, 1.10, -2.8 + (seat / 2) * 1.15)
                    .yRot((float) Math.toRadians(180 - getYRot()));
        }
        return new Vec3(0, 1.05, 0);
    }
    @Override public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position().add(3, 0.1, 0);
    }
    @Override public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player.isSpectator() || getControllingPassenger() != null) return InteractionResult.PASS;
        if (!level().isClientSide()) {
            if (!player.startRiding(this)) return InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPEED, 0.0F); builder.define(AIRBORNE, false); builder.define(BANK, 0.0F);
        builder.define(BOARDING, false);
    }
    public boolean acceptControls(ServerPlayer pilot, FlightControls packet) {
        if (packet.entityId() != getId() || getControllingPassenger() != pilot) return false;
        controls = packet.buttons() & 255;
        controlAge = 0;
        if ((controls & FlightControls.EXIT) != 0 && canExit()) exitPlane();
        return true;
    }
    private boolean pressed(int mask) { return (controls & mask) != 0; }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (!(getControllingPassenger() instanceof ServerPlayer) || ++controlAge > 10) controls = 0;
        if (airliner()) PassengerBoarding.tick(this);
        if (cannonCooldown > 0) cannonCooldown--;
        if (runwayMessageCooldown > 0) runwayMessageCooldown--;
        boolean landing = pressed(FlightControls.FORWARD) && pressed(FlightControls.DESCEND);
        boolean groundExit = !airborne() && (pressed(FlightControls.EXIT)
                || (pressed(FlightControls.DESCEND) && !pressed(FlightControls.FORWARD)));
        float speed = speedKmh();
        if (boarding()) speed = 0;
        else if (landing || groundExit || pressed(FlightControls.BRAKE)) speed = Math.max(0, speed - (airborne() ? 0.65F : 1.2F));
        else if (pressed(FlightControls.FORWARD)) speed = Math.min(fighter() ? 700 : airliner() ? 340 : 260,
                speed + (fighter() ? 0.7F : airliner() ? 0.4F : 0.45F));
        else speed = Math.max(0, speed - (airborne() ? 0.04F : 0.35F));
        float turn = (pressed(FlightControls.LEFT) ? -1 : 0) + (pressed(FlightControls.RIGHT) ? 1 : 0);
        if (speed > 2) setYRot(getYRot() + turn * (airborne() ? 1.2F : 1.8F));
        entityData.set(BANK, airborne() ? -turn * 16 : 0.0F);
        double vertical = -0.08;
        if (!airborne() && pressed(FlightControls.CLIMB) && onGround()) {
            if (hasRunwayBelow()) {
                if (speed >= TAKEOFF_KMH) entityData.set(AIRBORNE, true);
            } else if (runwayMessageCooldown == 0 && getControllingPassenger() instanceof ServerPlayer pilot) {
                pilot.sendOverlayMessage(Component.translatable("message.aviation.runway_required"));
                runwayMessageCooldown = 40;
            }
        }
        if (airborne()) {
            if (landing) vertical = -0.16;
            else if (speed < 75) vertical = -0.20;
            else if (pressed(FlightControls.CLIMB)) vertical = fighter() ? 0.30 : 0.18;
            else vertical = 0;
        }
        setXRot(airborne() ? (float) (-Math.toDegrees(Math.atan2(vertical, Math.max(0.1, speed / KMH_PER_BLOCK_TICK)))) : 0);
        double radians = Math.toRadians(getYRot());
        var motion = new Vec3(-Math.sin(radians) * speed / KMH_PER_BLOCK_TICK, vertical,
                Math.cos(radians) * speed / KMH_PER_BLOCK_TICK);
        setDeltaMovement(motion);
        move(MoverType.SELF, motion);
        if (horizontalCollision) {
            // Touching runway edges or obstacles stops the aircraft without destroying it.
            speed = 0;
        }
        if (onGround() && airborne() && vertical < 0) {
            entityData.set(AIRBORNE, false); speed = Math.min(speed, 90); setXRot(0);
        }
        entityData.set(SPEED, speed);
        if (groundExit && canExit()) exitPlane();
        if (fighter() && pressed(FlightControls.FIRE) && cannonCooldown == 0 && getControllingPassenger() instanceof ServerPlayer pilot) {
            fireCannon(pilot); cannonCooldown = 4;
        }
        if (isInWater() || isInLava()) health -= 0.5F;
        if (health <= 0 || getY() < level().getMinY() - 20) destroy();
        syncVelocity = true;
    }

    public boolean fireCannon(ServerPlayer pilot) {
        if (!fighter() || getControllingPassenger() != pilot || !(level() instanceof ServerLevel server)) return false;
        var direction = Vec3.directionFromRotation(getXRot(), getYRot());
        var from = position().add(0, 1.15, 0).add(direction.scale(3.0));
        var to = from.add(direction.scale(120));
        var blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) to = blockHit.getLocation();
        var hit = ProjectileUtil.getEntityHitResult(this, from, to, getBoundingBox().expandTowards(to.subtract(position())).inflate(2),
                target -> target != pilot && target.isPickable() && !target.isPassengerOfSameVehicle(this), from.distanceToSqr(to));
        if (hit != null) {
            hit.getEntity().hurtServer(server, damageSources().playerAttack(pilot), 12.0F);
            to = hit.getLocation();
        }
        var line = to.subtract(from);
        int count = Math.max(1, (int) (line.length() / 3));
        for (int i = 0; i <= count; i++) {
            var point = from.add(line.scale(i / (double) count));
            for (var watcher : server.players()) server.sendParticles(watcher, ParticleTypes.CRIT, false, false,
                    point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        return true;
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isInvulnerableToBase(source) || isRemoved()) return false;
        if (source.is(DamageTypeTags.IS_FALL)) { resetFallDistance(); return false; }
        health -= amount;
        if (health <= 0) destroy();
        return true;
    }
    private void destroy() {
        if (!(level() instanceof ServerLevel server) || isRemoved()) return;
        remove(RemovalReason.KILLED);
        ejectPassengers();
        spawnAtLocation(server, fighter() ? AviationMod.FIGHTER_ITEM : airliner() ? AviationMod.AIRLINER_ITEM : AviationMod.PLANE_ITEM);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        health = input.getFloatOr("AircraftHealth", 160);
        entityData.set(SPEED, Math.clamp(input.getFloatOr("SpeedKmh", 0), 0, fighter() ? 700 : airliner() ? 340 : 260));
        entityData.set(AIRBORNE, input.getBooleanOr("Airborne", false));
        boardingGate = input.getIntOr("BoardingGate", -1);
        boardingFinished = input.getBooleanOr("BoardingFinished", false);
        boardingQueue.clear();
        boardingQueue.addAll(input.read("BoardingQueue", PassengerBoarding.QueuedPassenger.CODEC.listOf()).orElse(List.of()));
        setBoarding(airliner() && !boardingFinished && !boardingQueue.isEmpty());
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        output.putFloat("AircraftHealth", health); output.putFloat("SpeedKmh", speedKmh());
        output.putBoolean("Airborne", airborne());
        output.putInt("BoardingGate", boardingGate); output.putBoolean("BoardingFinished", boardingFinished);
        output.store("BoardingQueue", PassengerBoarding.QueuedPassenger.CODEC.listOf(), boardingQueue);
    }
}
