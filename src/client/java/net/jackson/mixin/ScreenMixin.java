package net.jackson.mixin;

import net.jackson.ItemListOverlay;
import net.jackson.RecipeViewerConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Only handle keyboard input if we're in the inventory screen and the new UI is enabled
        if (((Screen) (Object) this) instanceof InventoryScreen &&
            RecipeViewerConfig.getInstance().useNewUI &&
            ItemListOverlay.handleKeyPress(keyCode, scanCode, modifiers)) {
            cir.setReturnValue(true);
        }
    }
}
