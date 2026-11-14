package com.ultimateflashy.vpn.mixin;

import com.ultimateflashy.vpn.VPNSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void ultimateFlashy$addSettingsButton(CallbackInfo ci) {
        Screen parent = (Screen) (Object) this;
        Button button = Button.builder(
            Component.literal("✦ VPN Settings"),
            ignored -> Minecraft.getInstance().setScreen(new VPNSettingsScreen(parent))
        ).bounds(parent.width / 2 - 100, parent.height / 4 + 120, 200, 20).build();
        ((ScreenAccessor) parent).ultimateFlashy$addWidget(button);
    }
}
