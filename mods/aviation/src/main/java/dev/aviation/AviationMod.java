package dev.aviation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class AviationMod implements ModInitializer {
    public static final String ID = "aviation";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(ID, path); }
    public static Item.Properties itemProperties(String path) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(path)));
    }
    private static BlockBehaviour.Properties blockProperties(String path) {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).strength(3).setId(ResourceKey.create(Registries.BLOCK, id(path)));
    }
    public static final Block RUNWAY = new Block(blockProperties("runway_asphalt"));
    public static final Block WHITE = new Block(blockProperties("runway_white"));
    public static final Block YELLOW = new Block(blockProperties("taxiway_yellow"));
    public static final Block APRON = new Block(blockProperties("apron_concrete"));
    public static final Block LIGHT = new Block(blockProperties("airfield_light").lightLevel(state -> 15));
    public static final Block GATE = new AirportGateBlock(blockProperties("airport"));
    public static final EntityType<PlaneEntity> PLANE = EntityType.Builder.<PlaneEntity>of(PlaneEntity::new, MobCategory.MISC)
            .sized(3.0F, 2.0F).clientTrackingRange(14).updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("plane")));
    public static final EntityType<PlaneEntity> FIGHTER = EntityType.Builder.<PlaneEntity>of(PlaneEntity::new, MobCategory.MISC)
            .sized(3.0F, 2.0F).clientTrackingRange(16).updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("fighter")));
    public static final Item PLANE_ITEM = new AircraftItem(itemProperties("plane").stacksTo(1), false);
    public static final EntityType<PlaneEntity> AIRLINER = EntityType.Builder.<PlaneEntity>of(PlaneEntity::new, MobCategory.MISC)
            .sized(4.0F, 3.4F).clientTrackingRange(18).updateInterval(1)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, id("airliner")));
    public static final Item AIRLINER_ITEM = new AircraftItem(itemProperties("airliner").stacksTo(1), AIRLINER);
    public static final Item FIGHTER_ITEM = new AircraftItem(itemProperties("fighter").stacksTo(1), true);

    private void registerBlock(String name, Block block) {
        Registry.register(BuiltInRegistries.BLOCK, id(name), block);
        var item = new BlockItem(block, itemProperties(name));
        Registry.register(BuiltInRegistries.ITEM, id(name), item);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(item));
    }
    @Override public void onInitialize() {
        registerBlock("runway_asphalt", RUNWAY); registerBlock("runway_white", WHITE);
        registerBlock("taxiway_yellow", YELLOW); registerBlock("apron_concrete", APRON);
        registerBlock("airfield_light", LIGHT); registerBlock("airport", GATE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("plane"), PLANE);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("fighter"), FIGHTER);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, id("airliner"), AIRLINER);
        Registry.register(BuiltInRegistries.ITEM, id("plane"), PLANE_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("fighter"), FIGHTER_ITEM);
        Registry.register(BuiltInRegistries.ITEM, id("airliner"), AIRLINER_ITEM);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            entries.accept(PLANE_ITEM); entries.accept(FIGHTER_ITEM); entries.accept(AIRLINER_ITEM);
        });
        PayloadTypeRegistry.serverboundPlay().register(FlightControls.TYPE, FlightControls.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(FlightControls.TYPE, (payload, context) -> {
            if (context.player().getVehicle() instanceof PlaneEntity plane) plane.acceptControls(context.player(), payload);
        });
        AirportService.initialize();
    }
}
