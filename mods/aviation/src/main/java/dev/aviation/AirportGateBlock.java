package dev.aviation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class AirportGateBlock extends Block {
    public AirportGateBlock(Properties properties) { super(properties); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player) AirportService.enter(player);
    }
    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (level.dimension().equals(AirportService.DIMENSION)) AirportService.leave(serverPlayer);
            else AirportService.enter(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
