package com.govno.border;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class Border implements ModInitializer {
    public static final String MOD_ID = "border";
    public static final Logger LOGGER = LoggerFactory.getLogger("border-mod");

    private int tickCounter = 0;
    private final Random random = new Random();

    @Override
    public void onInitialize() {
        LOGGER.info("BORDER: Initializing BorderMod");

        // Команда /border reload
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("border")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("reload")
                                .executes(context -> {
                                    BorderConfig.reload();
                                    context.getSource().sendFeedback(() -> Text.literal("§aBorder config reloaded!"), false);
                                    return 1;
                                })
                        )
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            checkPlayerPosition(player);
        }
    }

    private void checkPlayerPosition(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();
        BlockPos pos = player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();

        boolean outside = isOutsidePolygon(x, z, BorderConfig.get().getPolygon());
        boolean outside50 = isOutsidePolygon(x, z, expandPolygon(BorderConfig.get().getPolygon(), 50));
        boolean outside100 = isOutsidePolygon(x, z, expandPolygon(BorderConfig.get().getPolygon(), 100));

        // 1. За границей > тьма
        if (outside) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 220, 0, false, false));
        }

        // 2. +50 > слабость, замедление, урон
        if (outside50) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 220, 1, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 220, 1, false, false));

            if (tickCounter % 40 == 0) {
                player.damage(world, BorderDamageSource.create(world), 8.0f);
                spawnFireParticles(world, pos, 10);
            }
        }

        // 3. +100 > вечная тьма + убийство + звуки
        if (outside100) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 999999, 0, false, false));
            if (tickCounter % 80 == 0) {
                player.damage(world, BorderDamageSource.create(world), Float.MAX_VALUE);

                world.playSound(
                        null, // null = звук слышат все игроки в мире
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.ENTITY_WARDEN_AGITATED, // звук
                        player.getSoundCategory(),          // категория
                        1.0f,                               // громкость
                        0.6f                                // pitch
                );

                world.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.AMBIENT_CAVE,
                        player.getSoundCategory(),
                        1.0f,
                        0.5f
                );

                spawnFireParticles(world, pos, 20);
                spawnAshParticles(world, pos, 70);
            }
        }

        // 4. Ломаем порталы в аду
        if (world.getRegistryKey() == World.NETHER && outside) {
            breakNearbyPortalBlocks(player);
        }

        // 5. Выкидываем игрока из лодки
        if (player.hasVehicle() && outside50 && player.getControllingVehicle() instanceof BoatEntity vehicle) {
            vehicle.updatePosition(pos.getX(), pos.getY() - 1, pos.getZ());
            player.dismountVehicle();
        }
    }

    // Проверка точки в многоугольнике
    private boolean isOutsidePolygon(int x, int z, List<int[]> polygon) {
        boolean inside = false;

        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            int xi = polygon.get(i)[0], zi = polygon.get(i)[1];
            int xj = polygon.get(j)[0], zj = polygon.get(j)[1];

            boolean intersect = ((zi > z) != (zj > z)) &&
                    (x < (xj - xi) * (z - zi) / (double) (zj - zi) + xi);
            if (intersect) inside = !inside;
        }

        return !inside; // если не внутри → значит снаружи
    }

    // смещение каждой точки наружу
    private List<int[]> expandPolygon(List<int[]> polygon, int dist) {
        return polygon.stream()
                .map(p -> new int[]{p[0] + (p[0] >= 0 ? dist : -dist), p[1] + (p[1] >= 0 ? dist : -dist)})
                .toList();
    }

    private void breakNearbyPortalBlocks(ServerPlayerEntity player) {
        ServerWorld world = player.getWorld();
        BlockPos playerPos = player.getBlockPos();
        BlockPos nearestPortalPos = findNearestPortalBlock(world, playerPos);

        if (nearestPortalPos != null && playerPos.isWithinDistance(nearestPortalPos, 2.5)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 3, false, false));

            for (int x = -1; x <= 3; x++) {
                for (int y = -1; y <= 3; y++) {
                    for (int z = -1; z <= 3; z++) {
                        BlockPos offsetPos = nearestPortalPos.add(x, y, z);
                        if (world.getBlockState(offsetPos).getBlock() instanceof NetherPortalBlock ||
                                world.getBlockState(offsetPos).getBlock() == Blocks.OBSIDIAN) {
                            world.setBlockState(offsetPos, Blocks.AIR.getDefaultState());
                            if (random.nextBoolean()) {
                                world.setBlockState(offsetPos, Blocks.CRYING_OBSIDIAN.getDefaultState());
                            }
                            spawnFireParticles(world, offsetPos, 15);
                            LOGGER.info("BORDER: Portal block at {} broken", offsetPos.toShortString());
                        }
                    }
                }
            }
        }
    }

    private BlockPos findNearestPortalBlock(ServerWorld world, BlockPos playerPos) {
        BlockPos nearestPortalPos = null;
        double nearestDistance = 16; // радиус поиска

        for (int x = -10; x <= 10; x++) {
            for (int y = -11; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (world.getBlockState(pos).getBlock() instanceof NetherPortalBlock) {
                        double distance = playerPos.getSquaredDistance(pos);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestPortalPos = pos;
                        }
                    }
                }
            }
        }
        return nearestPortalPos;
    }

    private void spawnFireParticles(ServerWorld world, BlockPos pos, int count) {
        for (int i = 0; i < count; i++) {
            double offsetX = random.nextDouble() - 0.5;
            double offsetY = random.nextDouble();
            double offsetZ = random.nextDouble() - 0.5;
            world.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + 0.5 + offsetX,
                    pos.getY() + 1.0 + offsetY,
                    pos.getZ() + 0.5 + offsetZ,
                    2,
                    1, 1, 1, 0.01
            );
        }
    }

    private void spawnAshParticles(ServerWorld world, BlockPos pos, int count) {
        for (int i = 0; i < count; i++) {
            double offsetX = random.nextDouble() - 0.5;
            double offsetY = random.nextDouble();
            double offsetZ = random.nextDouble() - 0.5;
            world.spawnParticles(
                    ParticleTypes.ASH,
                    pos.getX() + 0.5 + offsetX,
                    pos.getY() + 1.0 + offsetY,
                    pos.getZ() + 0.5 + offsetZ,
                    3,
                    3, 3, 3, 0.1
            );
        }
    }
}
