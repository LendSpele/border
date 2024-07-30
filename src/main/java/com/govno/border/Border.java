package com.govno.border;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.network.PacketByteBuf;
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
    private int darkness;
    private int freezing;
    private final Random random = new Random();
    private int distance;


    @Override
    public void onInitialize() {
        LOGGER.info("BORDER: Initializing BorderMod");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("setborder")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("distance", IntegerArgumentType.integer())
                                .executes(context -> {
                                    int distance = IntegerArgumentType.getInteger(context, "distance");
                                    MinecraftServer server = context.getSource().getServer();
                                    StateSaverAndLoader serverState = StateSaverAndLoader.getServerState(server);
                                    serverState.setDistance(distance);
                                    PacketByteBuf data = PacketByteBufs.create();
                                    data.writeInt(serverState.getDistance());
                                    context.getSource().sendFeedback(() -> Text.literal("Border set to " + distance + " blocks."), false);
                                    for (int i = 0; i < 3; i++) {
                                        LOGGER.info("BORDER: Border set to: {} blocks.", distance);
                                    }
                                    return 1;
                                })
                        )
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            this.darkness++;
            if(this.darkness >= 200){
                this.darkness = 0;
            }
            this.freezing++;
            if(this.freezing >= 400){
                this.freezing = 0;
            }
            StateSaverAndLoader serverState = StateSaverAndLoader.getServerState(server);
            int distance = serverState.getDistance();
            this.distance = distance;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                checkPlayerPosition(player, distance);
            }
        });
    }


    public void checkPlayerPosition(ServerPlayerEntity player, int distance) {
        BlockPos pos = player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();
        ServerWorld world = player.getServerWorld();

        if (world.getRegistryKey() == World.OVERWORLD) {
            if (Math.abs(x) > distance || Math.abs(z) > distance) {
                applyEffects(player, pos, distance);
            }
        } else if (world.getRegistryKey() == World.NETHER) {
            if (Math.abs(x) > distance/8 || Math.abs(z) > distance/8) {
                applyEffects(player, pos, distance);
                breakNearbyPortalBlocks(player);
            }
        }
    }


    private void applyEffects(ServerPlayerEntity player, BlockPos _playerBlockPos, int _distance) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 5, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 5, false, false));

        if(this.darkness == 199){
            player.setFireTicks(140);
            if(random.nextBoolean()){
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 3, false, false));
            }
        }

        if (
                (_playerBlockPos.getZ() >= _distance + 20) ||
                (_playerBlockPos.getX() >= _distance + 20) ||
                (_playerBlockPos.getZ() <= _distance + 20) ||
                (_playerBlockPos.getX() <= _distance + 20)

        ){
            if (player.getWorld().getRegistryKey() == World.OVERWORLD) {
                if (this.freezing == 200){
                    if (random.nextBoolean()){
                        FreezingEffect.applyUpdateEffect(player, 200);
                    }
                }
            } else {
                FreezingEffect.deleteFreezingEffect(player);
            }
        }

        if (player.hasVehicle()){
            Entity vehicle = player.getControllingVehicle();
            assert vehicle != null;

            if(vehicle instanceof BoatEntity) {
                vehicle.updatePosition(_playerBlockPos.getX(), _playerBlockPos.getY() - 1, _playerBlockPos.getZ());
                player.dismountVehicle();
            }
        }
    }


    private void breakNearbyPortalBlocks(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        BlockPos playerPos = player.getBlockPos();
        BlockPos nearestPortalPos = findNearestPortalBlock(world, playerPos);

        if (nearestPortalPos != null) {
            if (playerPos.isWithinDistance(nearestPortalPos, 2.5)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 3, false, false));
                for (int x = -1; x <= 3; x++) {
                    for (int y = -1; y <= 3; y++) {
                        for (int z = -1; z <= 3; z++) {
                            BlockPos offsetPos = nearestPortalPos.add(x, y, z);
                            if (world.getBlockState(offsetPos).getBlock() instanceof NetherPortalBlock || world.getBlockState(offsetPos).getBlock() == Blocks.OBSIDIAN) {
                                world.setBlockState(offsetPos, Blocks.AIR.getDefaultState());
                                if (random.nextBoolean()){
                                    world.setBlockState(offsetPos, Blocks.CRYING_OBSIDIAN.getDefaultState());
                                }
                                LOGGER.info("BORDER: Portal block at {} broken", offsetPos.toShortString());
                            }
                        }
                    }
                }
            }
        }
    }


    private BlockPos findNearestPortalBlock(ServerWorld world, BlockPos playerPos) {
        BlockPos nearestPortalPos = null;
        double nearestDistance = ((double) this.distance /8);

        for (int x = -10; x <= 10; x++) {
            for (int y = -11; y <= 10; y++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    if (
                            world.getBlockState(pos).getBlock() instanceof NetherPortalBlock
                    ) {
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
}