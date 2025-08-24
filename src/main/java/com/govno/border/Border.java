package com.govno.border;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class Border implements ModInitializer {
    public static final String MOD_ID = "border";
    public static final Logger LOGGER = LoggerFactory.getLogger("border-mod");

    private int tickCounter = 0; // для периодических эффектов
    private final Random random = new Random();

    private final BorderConfig borderConfig = BorderConfig.load();

    @Override
    public void onInitialize() {
        LOGGER.info("BORDER: Initializing BorderMod");

        // Команда /setborder
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("setborder")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("distance", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int distance = IntegerArgumentType.getInteger(context, "distance");
                                    borderConfig.setDistance(distance);
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("Border set to " + distance + " blocks."), false
                                    );
                                    LOGGER.info("BORDER: Border set to: {} blocks.", distance);
                                    return 1;
                                })
                        )
                )
        );

        // Тик сервера
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
        int distance = borderConfig.getDistance();
        int playerDistance = Math.max(Math.abs(x), Math.abs(z));

        // 1. За границей бордера > тьма
        if (playerDistance > distance) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 220, 0, false, false));
        }

        // 2. +50 блоков > слабость, замедление, урон каждые 2 секунды
        if (playerDistance > distance + 50) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 220, 1, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 220, 1, false, false));

            if (tickCounter % 40 == 0) { // каждые 2 секунды
                player.damage(player.getWorld(), player.getDamageSources().magic(), 8.0f);
                spawnFireParticles(world, pos, 10);
            }
        }

        // 3. +100 блоков > бесконечная тьма и убийство каждые 4 секунды
        if (playerDistance > distance + 100) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 999999, 0, false, false));
            if (tickCounter % 80 == 0) { // каждые 4 секунды
                player.damage(player.getWorld(), player.getDamageSources().magic(), Float.MAX_VALUE);
                spawnFireParticles(world, pos, 20);
                spawnAshParticles(world, pos, 70);
            }
        }

        // 4. Поломка портала в аду
        if (world.getRegistryKey() == World.NETHER && playerDistance > distance / 8) {
            breakNearbyPortalBlocks(player);
        }

        // 5. Если игрок на лодке > вытаскиваем его
        if (player.hasVehicle() && player.getControllingVehicle() instanceof BoatEntity) {
            Entity vehicle = player.getControllingVehicle();
            vehicle.updatePosition(pos.getX(), pos.getY() - 1, pos.getZ());
            player.dismountVehicle();
        }
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
                            spawnFireParticles(world, offsetPos, 15); // частицы огня при поломке портала
                            LOGGER.info("BORDER: Portal block at {} broken", offsetPos.toShortString());
                        }
                    }
                }
            }
        }
    }

    private BlockPos findNearestPortalBlock(ServerWorld world, BlockPos playerPos) {
        BlockPos nearestPortalPos = null;
        double nearestDistance = (double) borderConfig.getDistance() / 8;

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
