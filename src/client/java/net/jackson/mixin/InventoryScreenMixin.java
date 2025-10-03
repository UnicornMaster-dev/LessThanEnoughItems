package net.jackson.mixin;

import net.jackson.ItemListOverlay;
import net.jackson.RecipeViewerConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public class InventoryScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo info) {
        // Only render if the new UI is enabled in config
        if (RecipeViewerConfig.getInstance().useNewUI) {
            ItemListOverlay.render(context, mouseX, mouseY);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        // Only handle clicks if the new UI is enabled
        if (RecipeViewerConfig.getInstance().useNewUI && ItemListOverlay.handleClick(mouseX, mouseY)) {
            cir.setReturnValue(true);
        }
    }
}
