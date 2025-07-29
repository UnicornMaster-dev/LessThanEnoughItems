package net.jackson.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(HandledScreen.class)
public interface HandledScreenAccessor<T extends ScreenHandler> {
    @Accessor("x") int getX();
    @Accessor("y") int getY();
    @Accessor("backgroundWidth") int getBackgroundWidth();
    @Accessor("backgroundHeight") int getBackgroundHeight();
}
