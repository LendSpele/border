package com.govno.border;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class FreezingEffect {

    public static void applyUpdateEffect(ServerPlayerEntity entity, int amplifier) {
        if (entity.isPlayer()) {
            if (entity.getFrozenTicks() < 250 * (amplifier + 1)) {
                entity.setFrozenTicks(entity.getFrozenTicks() + 3 * (amplifier + 1));
                tickFrozenHands( entity);
            }
        }
    }

    public static void deleteFreezingEffect(ServerPlayerEntity entity){
        entity.setFrozenTicks(0);
    }


    private static void tickFrozenHands(ServerPlayerEntity player) {
        boolean mainhand = !player.getMainHandStack().isEmpty();
        boolean offhand = !player.getOffHandStack().isEmpty();
        player.sendMessage(Text.literal("§bВам холодно..."), true);

        if (mainhand && offhand) {
            if (player.getRandom().nextBetween(0, 1) == 0) {
                ItemStack itemStack = player.getMainHandStack().copy();
                itemStack.setCount(1);

                player.dropItem(itemStack, false, true);
                player.getMainHandStack().decrement(1);
            } else {
                ItemStack itemStack = player.getOffHandStack().copy();
                itemStack.setCount(1);
                player.dropItem(itemStack, false, true);
                player.getOffHandStack().decrement(1);
            }
        } else {
            if (mainhand) {
                ItemStack itemStack = player.getMainHandStack().copy();
                itemStack.setCount(1);
                player.dropItem(itemStack, false, true);
                player.getMainHandStack().decrement(1);
            } else {
                ItemStack itemStack = player.getOffHandStack().copy();
                itemStack.setCount(1);
                player.dropItem(itemStack, false, true);
                player.getOffHandStack().decrement(1);
            }
        }

    }
}