package dev.velocity.core;

import dev.velocity.vehicle.VehicleEntity;
import dev.velocity.vehicle.VehicleSpawnerItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class VelocityRegistries {
    private VelocityRegistries() {}

    public static final EntityType<VehicleEntity> VEHICLE = Registry.register(Registries.ENTITY_TYPE, VelocityMod.id("vehicle"),
            EntityType.Builder.<VehicleEntity>create(VehicleEntity::new, SpawnGroup.MISC)
                    .dimensions(1.9f, 1.25f)      // caixa de colisão do protótipo (largura × altura)
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)       // replicação a cada tick para movimento suave
                    .build("vehicle"));

    public static final Item VEHICLE_SPAWNER = Registry.register(Registries.ITEM, VelocityMod.id("aurion_vx_prototype"),
            new VehicleSpawnerItem(new Item.Settings().maxCount(1), VehicleEntity.DEFAULT_DEFINITION));

    public static void init() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(VEHICLE_SPAWNER));
    }
}
