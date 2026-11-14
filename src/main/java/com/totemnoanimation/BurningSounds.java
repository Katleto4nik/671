package com.totemnoanimation;

import net.minecraft.util.Identifier;

import java.util.Set;

/** Which sounds count as "burning" and how much quieter they get. */
public final class BurningSounds {
    /** 0.5 = 50% quieter. */
    public static final float VOLUME_MULTIPLIER = 0.5F;

    private static final Set<Identifier> IDS = Set.of(
            Identifier.of("minecraft", "entity.player.hurt_on_fire"),
            Identifier.of("minecraft", "entity.generic.burn")
    );

    private BurningSounds() {
    }

    public static boolean isBurning(Identifier id) {
        return id != null && IDS.contains(id);
    }
}
