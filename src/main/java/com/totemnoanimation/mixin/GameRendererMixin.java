package com.totemnoanimation.mixin;

import com.totemnoanimation.TotemRefill;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game calls showFloatingItem exactly when YOUR totem pops (the big totem flying over the screen).
 * We use it as the "totem popped" signal and cancel the animation.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "showFloatingItem", at = @At("HEAD"), cancellable = true)
    private void totemnoanimation$hidePopAnimation(ItemStack floatingItem, CallbackInfo ci) {
        TotemRefill.onTotemPop();
        ci.cancel();
    }
}
