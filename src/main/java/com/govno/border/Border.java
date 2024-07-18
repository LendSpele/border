package com.govno.border;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.slf4j.LoggerFactory;

public class Border implements ModInitializer {

    //public static final Logger LOGGER = (Logger) LoggerFactory.getLogger("border-mod");
    private static int borderDistance = 1000;

    @Override
    public void onInitialize() {
        //LOGGER.info("Initializing BorderMod");

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("setborder")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.argument("distance", IntegerArgumentType.integer())
                        .executes(context -> {
                            borderDistance = IntegerArgumentType.getInteger(context, "distance");
                            context.getSource().sendFeedback(() -> Text.literal("Border set to " + borderDistance + " blocks."), false);
                            //LOGGER.info("Border set to {}" + borderDistance +"blocks.");
                            return 1;
                        }))));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                checkPlayerPosition(player);
            }
        });
    }

    public static void checkPlayerPosition(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        int x = pos.getX();
        int z = pos.getZ();
        if (Math.abs(x) > borderDistance || Math.abs(z) > borderDistance) {
            applyEffects(player);
        }
    }

    private static void applyEffects(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 200, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 200, 1));
    }
}