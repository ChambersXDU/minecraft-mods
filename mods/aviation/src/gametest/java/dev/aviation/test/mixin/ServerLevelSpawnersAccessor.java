package dev.aviation.test.mixin;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerLevel.class)
public interface ServerLevelSpawnersAccessor {
    @Accessor("customSpawners") List<CustomSpawner> aviation$getCustomSpawners();
    @Mutable @Accessor("customSpawners") void aviation$setCustomSpawners(List<CustomSpawner> spawners);
}
