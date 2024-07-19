package com.govno.border;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Border implements ModInitializer {
    public static final String MOD_ID = "border";
    public static final Logger LOGGER = LoggerFactory.getLogger("border-mod");

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing BorderMod");

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

                                    //context.getSource().sendFeedback(Text.literal("Border set to " + distance + " blocks."), false);
                                    LOGGER.info("Border set to: " + distance + " blocks.");
                                    return 1;
                                })
                        )
                )
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StateSaverAndLoader serverState = StateSaverAndLoader.getServerState(server);
            int distance = serverState.getDistance();
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                checkPlayerPosition(player, distance);
            }
        });
    }

    public static void checkPlayerPosition(ServerPlayerEntity player, int distance) {
        BlockPos pos = player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();
        if (Math.abs(x) > distance || Math.abs(z) > distance) {
            applyEffects(player);
        }
    }

    private static void applyEffects(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 1));
    }
}
