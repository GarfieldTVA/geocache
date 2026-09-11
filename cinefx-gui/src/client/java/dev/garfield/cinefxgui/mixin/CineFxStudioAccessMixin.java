package dev.garfield.cinefxgui.mixin;

import dev.garfield.cinefxgui.editor.CineFxStudioScreen;
import dev.garfield.cinefxgui.editor.EditorModel;
import dev.garfield.cinefxgui.editor.PreviewController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * Single typed bridge used by editor extensions. This replaces scattered reflective private-field
 * access with Mixin accessors/invokers that fail loudly during development if the Studio contract moves.
 */
@Mixin(value = CineFxStudioScreen.class, remap = false)
public interface CineFxStudioAccessMixin {
    @Accessor("project") EditorModel.Project cinefxGui$getProject();
    @Accessor("project") void cinefxGui$setProject(EditorModel.Project project);
    @Accessor("selectedId") String cinefxGui$getSelectedId();
    @Accessor("selectedId") void cinefxGui$setSelectedId(String id);
    @Accessor("searchText") String cinefxGui$getSearchText();
    @Accessor("leftScroll") double cinefxGui$getLeftScroll();
    @Accessor("leftScroll") void cinefxGui$setLeftScroll(double value);
    @Accessor("timelineStartTick") double cinefxGui$getTimelineStartTick();
    @Accessor("timelineStartTick") void cinefxGui$setTimelineStartTick(double value);
    @Accessor("pixelsPerTick") double cinefxGui$getPixelsPerTick();
    @Accessor("history") EditorModel.History cinefxGui$getHistory();
    @Accessor("preview") PreviewController cinefxGui$getPreview();
    @Accessor("collapsed") Set<String> cinefxGui$getCollapsedProperties();

    @Invoker("select") void cinefxGui$select(EditorModel.Element element);
    @Invoker("checkpoint") void cinefxGui$checkpoint();
    @Invoker("markChangedContinuous") void cinefxGui$markChanged();
    @Invoker("toast") void cinefxGui$toast(String message);
    @Invoker("loadPreset") void cinefxGui$loadPreset(String name);
    @Invoker("fitTimeline") void cinefxGui$fitTimeline();
    @Invoker("focusSelected") void cinefxGui$focusSelected();
    @Invoker("duplicateSelected") void cinefxGui$duplicateSelected();
    @Invoker("deleteSelected") void cinefxGui$deleteSelected();
    @Invoker("addHit") void cinefxGui$addHit(int x1, int y1, int x2, int y2, Runnable action);
    @Invoker("createCameraFromView") void cinefxGui$createCameraFromView();
    @Invoker("addKeyframesAtPlayhead") void cinefxGui$addKeyframesAtPlayhead();
    @Invoker("saveProject") void cinefxGui$saveProject();
    @Invoker("validateProject") void cinefxGui$validateProject();
    @Invoker("onEditorTextChanged") void cinefxGui$onEditorTextChanged(String text);
}
