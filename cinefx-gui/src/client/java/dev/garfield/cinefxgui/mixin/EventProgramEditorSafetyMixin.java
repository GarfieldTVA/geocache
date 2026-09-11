package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.EventProgramEditorScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The constructor establishes selection before Screen.init() has created inspector widgets. */
@Mixin(value = EventProgramEditorScreen.class, remap = false)
public abstract class EventProgramEditorSafetyMixin {
    @Shadow @Final private TextFieldWidget[] fields;

    @Inject(method = "configureFields", at = @At("HEAD"), cancellable = true)
    private void cinefxGui$deferBindingsUntilInit(CallbackInfo ci) {
        if (fields == null || fields.length == 0 || fields[0] == null) ci.cancel();
    }
}
