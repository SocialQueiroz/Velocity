package dev.velocity.vehicle;

import dev.velocity.core.VelocityRegistries;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Item de colocação do veículo (Fase 1). Será substituído por concessionária/garagem na Fase 5. */
public class VehicleSpawnerItem extends Item {
    private final String definitionId;

    public VehicleSpawnerItem(Settings settings, String definitionId) {
        super(settings);
        this.definitionId = definitionId;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        if (!(world instanceof ServerWorld server)) return ActionResult.SUCCESS;
        BlockPos pos = ctx.getBlockPos().offset(ctx.getSide());
        VehicleEntity vehicle = VelocityRegistries.VEHICLE.create(server);
        if (vehicle == null) return ActionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYaw() : 0f;
        vehicle.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        vehicle.setDefinitionId(definitionId);
        vehicle.fillTank();
        if (!server.isSpaceEmpty(vehicle, vehicle.getBoundingBox())) return ActionResult.FAIL;
        server.spawnEntity(vehicle);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().creativeMode) ctx.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
