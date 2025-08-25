package com.govno.border;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import static com.govno.border.Border.MOD_ID;

public class BorderDamageSource {

    // Регистрируем ключ типа урона
    public static final RegistryKey<DamageType> BORDER_DEATH =
            RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(MOD_ID, "border_death"));


    public static DamageSource create(ServerWorld world) {
        return new DamageSource(
                world.getRegistryManager()
                        .getOrThrow(RegistryKeys.DAMAGE_TYPE)
                        .getEntry(BORDER_DEATH.getValue()).get()
        );
    }


    public static void damage(Entity entity, float amount) {
        if (entity.getWorld() instanceof ServerWorld serverWorld) {
            entity.damage(serverWorld, create(serverWorld), amount);
        }
    }
}
