package com.govno.border;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.Random;

public class Border implements ModInitializer {
    public static final String MOD_ID = "border";
    public static final Logger LOGGER = LoggerFactory.getLogger("border-mod");

    private int tickCounter = 0;
    private final Random random = new Random();
    private final BorderConfig borderConfig = BorderConfig.load();

    @Override
    public void onInitialize() {
        LOGGER.info("BORDER: Initializing BorderMod");

        // Команда /setborder <+x> <-x> <+z> <-z>
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("setborder")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("posX", IntegerArgumentType.integer())
                                .then(CommandManager.argument("negX", IntegerArgumentType.integer())
                                        .then(CommandManager.argument("posZ", IntegerArgumentType.integer())
                                                .then(CommandManager.argument("negZ", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            int posX = IntegerArgumentType.getInteger(context, "posX");
                                                            int negX = IntegerArgumentType.getInteger(context, "negX");
                                                            int posZ = IntegerArgumentType.getInteger(context, "posZ");
                                                            int negZ = IntegerArgumentType.getInteger(context, "negZ");

                                                            borderConfig.setPosX(posX);
                                                            borderConfig.setNegX(negX);
                                                            borderConfig.setPosZ(posZ);
                                                            borderConfig.setNegZ(negZ);

                                                            context.getSource().sendFeedback(
                                                                    () -> Text.literal("Border set: +X=" + posX + ", -X=" + negX +
                                                                            ", +Z=" + posZ + ", -Z=" + negZ), false
                                                            );
                                                            LOGGER.info("BORDER: Border updated: +X={}, -X={}, +Z={}, -Z={}", posX, negX, posZ, negZ);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
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

        int px = borderConfig.getPosX();
        int nx = borderConfig.getNegX();
        int pz = borderConfig.getPosZ();
        int nz = borderConfig.getNegZ();

        boolean outside = x > px || x < nx || z > pz || z < nz;
        boolean outside50 = x > px + 50 || x < nx - 50 || z > pz + 50 || z < nz - 50;
        boolean outside100 = x > px + 100 || x < nx - 100 || z > pz + 100 || z < nz - 100;

        // 1. За границей > тьма
        if (outside) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 220, 0, false, false));
        }

        // 2. +50 > слабость, замедление, урон
        if (outside50) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 220, 1, false, false));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 220, 1, false, false));

            if (tickCounter % 40 == 0) {
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                    player.damage(serverWorld, BorderDamageSource.create(serverWorld), 8.0f);
                }
                spawnFireParticles(world, pos, 10);
            }
        }

        // 3. +100 > вечная тьма + убийство
        if (outside100) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 999999, 0, false, false));
            if (tickCounter % 80 == 0) {
                if (player.getWorld() instanceof ServerWorld serverWorld) {
                    player.damage(serverWorld, BorderDamageSource.create(serverWorld), Float.MAX_VALUE);
                    serverWorld.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.ENTITY_WARDEN_AGITATED,
                            player.getSoundCategory(),
                            1.0f,
                            0.6f
                    );

                    serverWorld.playSound(
                            null,
                            player.getX(),
                            player.getY(),
                            player.getZ(),
                            SoundEvents.AMBIENT_CAVE.value(),
                            player.getSoundCategory(),
                            1.0f,
                            0.5f
                    );
                }
                spawnFireParticles(world, pos, 20);
                spawnAshParticles(world, pos, 70);
            }
        }

        // 4. Ломаем порталы в аду
        if (world.getRegistryKey() == World.NETHER && outside) {
            breakNearbyPortalBlocks(player);
        }

        // 5. Выкидываем игрока из лодки
        if (player.hasVehicle() && outside50 && player.getControllingVehicle() instanceof BoatEntity) {
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
