package com.totemnoanimation.mixin;

import com.totemnoanimation.BurningSounds;
import net.minecraft.client.sound.AbstractSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes burning sounds 50% quieter. */
@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private void totemnoanimation$quietBurning(CallbackInfoReturnable<Float> cir) {
        if (BurningSounds.isBurning(((SoundInstance) (Object) this).getId())) {
            cir.setReturnValue(cir.getReturnValue() * BurningSounds.VOLUME_MULTIPLIER);
        }
    }
}
