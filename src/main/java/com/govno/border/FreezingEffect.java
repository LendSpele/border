package com.govno.border;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class FreezingEffect {

    public static void applyUpdateEffect(ServerPlayerEntity entity, int amplifier) {
        if (entity.getFrozenTicks() < 250 * (amplifier + 1)) {
            entity.setFrozenTicks(entity.getFrozenTicks() + 3 * (amplifier + 1));
            tickFrozenHands(entity);
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
                dropItem(player, true);
            } else {
                dropItem(player, false);
            }
        } else if (mainhand) {
            dropItem(player, true);
        } else if (offhand) {
            dropItem(player, false);
        }
    }

    private static void dropItem(ServerPlayerEntity player, boolean mainHand) {
        ItemStack itemStack = mainHand ? player.getMainHandStack() : player.getOffHandStack();
        ItemStack copy = itemStack.copy();
        copy.setCount(1);
        player.dropItem(copy, false, true);
        if (mainHand) itemStack.decrement(1);
        else itemStack.decrement(1);
    }
}
