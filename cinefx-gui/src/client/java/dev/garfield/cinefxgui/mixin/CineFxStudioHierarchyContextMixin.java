package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.HierarchyJson;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps editor-only hierarchy resolution scoped to the currently open Studio project. */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public abstract class CineFxStudioHierarchyContextMixin {
    @Shadow private EditorModel.Project project;

    @Inject(method = "render", at = @At("HEAD"))
    private void cinefxGui$setHierarchyProject(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        HierarchyJson.setProject(project);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void cinefxGui$setHierarchyProjectOnTick(CallbackInfo ci) {
        HierarchyJson.setProject(project);
    }
}
