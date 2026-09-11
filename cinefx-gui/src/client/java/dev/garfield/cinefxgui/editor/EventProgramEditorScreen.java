package dev.garfield.cinefxgui.editor;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Visual authoring surface for EventProgramSpec and AssetBundle. No raw JSON is required. */
public final class EventProgramEditorScreen extends Screen {
    private static final int TOP = 34;
    private static final int LEFT = 238;
    private static final int RIGHT = 330;
    private enum Mode { PROGRAM, BUNDLES }
    private enum Kind { PROGRAM, PHASE, ACTION, TRANSITION, CONDITION, BUNDLE, LIST_ENTRY, MAP_ENTRY }

    private final Screen parent;
    private EventAuthoringModel.Workspace workspace;
    private Mode mode = Mode.PROGRAM;
    private Selection selection;
    private int phaseIndex;
    private int bundleIndex;
    private double leftScroll;
    private double centerScroll;
    private int autosaveTicks;
    private String status = "Visual event authoring";
    private long statusUntil;
    private final List<Hit> hits = new ArrayList<>();
    private final List<FieldBinding> bindings = new ArrayList<>();
    private final TextFieldWidget[] fields = new TextFieldWidget[6];
    private boolean configuringFields;

    public EventProgramEditorScreen(Screen parent) {
        super(Text.literal("CineFX Event Studio"));
        this.parent = parent;
        EventAuthoringModel.Workspace autosave = EventWorkspaceStore.loadAutosave();
        this.workspace = autosave == null ? EventAuthoringModel.Workspace.fresh() : autosave;
        EventWorkspaceStore.normalize(workspace);
        selectProgram();
    }

    @Override protected void init() {
        for (int i = 0; i < fields.length; i++) {
            int index = i;
            fields[i] = new TextFieldWidget(textRenderer, width - RIGHT + 10, TOP + 62 + i * 37, RIGHT - 20, 18, Text.literal("Value"));
            fields[i].setMaxLength(4096);
            fields[i].setVisible(false);
            fields[i].setChangedListener(value -> applyField(index, value));
            addDrawableChild(fields[i]);
        }
        configureFields();
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override public void tick() {
        super.tick();
        if (workspace.dirty && ++autosaveTicks >= 100) {
            autosaveTicks = 0;
            EventWorkspaceStore.autosave(workspace);
        }
    }

    @Override public void removed() {
        EventWorkspaceStore.autosave(workspace);
        super.removed();
    }

    @Override
    public void render(DrawContext c, int mx, int my, float deltaTicks) {
        hits.clear();
        c.fill(0,0,width,height,0xFF11151A);
        c.fill(0,0,width,TOP,0xFF1D242B);
        c.fill(0,TOP,LEFT,height,0xFF171C22);
        c.fill(width-RIGHT,TOP,width,height,0xFF171C22);
        c.drawTextWithShadow(textRenderer,"CineFX Event Studio",9,12,0xFFF0F4F7);
        topButton(c,mx,my,150,6,44,"Back",this::closeEditor);
        topButton(c,mx,my,198,6,44,"New",this::newWorkspace);
        topButton(c,mx,my,246,6,46,"Save",this::saveWorkspace);
        topButton(c,mx,my,296,6,62,"Validate",this::validateWorkspace);
        topButton(c,mx,my,362,6,58,"Publish",this::publishWorkspace);
        topButton(c,mx,my,428,6,66,mode==Mode.PROGRAM?"Program*":"Program",()->{mode=Mode.PROGRAM;selectProgram();});
        topButton(c,mx,my,498,6,66,mode==Mode.BUNDLES?"Bundles*":"Bundles",()->{mode=Mode.BUNDLES;selectCurrentBundle();});
        String state = workspace.dirty ? "modified" : "saved";
        c.drawTextWithShadow(textRenderer,workspace.name+" · "+state,width-textRenderer.getWidth(workspace.name+" · "+state)-9,12,workspace.dirty?0xFFFFC86A:0xFF87CFA3);

        drawLeft(c,mx,my);
        drawCenter(c,mx,my);
        drawInspector(c,mx,my);
        c.drawTextWithShadow(textRenderer,statusText(),8,height-14,0xFF8797A2);
        super.render(c,mx,my,deltaTicks);
    }

    private void drawLeft(DrawContext c,int mx,int my) {
        int y=TOP+10-(int)leftScroll;
        if(mode==Mode.PROGRAM){
            c.drawTextWithShadow(textRenderer,"PHASES · "+workspace.program.phases.size(),9,y,0xFF81919D);y+=18;
            int addY=y; row(c,mx,my,6,addY,LEFT-12,"+ Add phase",0xFF75B9E7,()->addPhase()); y+=23;
            for(int i=0;i<workspace.program.phases.size();i++){
                EventAuthoringModel.Phase phase=workspace.program.phases.get(i);int index=i,yy=y;
                boolean selected=i==phaseIndex;
                row(c,mx,my,6,yy,LEFT-12,(phase.id.equals(workspace.program.initialPhase)?"◆ ":"  ")+phase.id,selected?0xFFFFD16A:0xFFD6E0E5,()->selectPhase(index));
                y+=22;
            }
        } else {
            c.drawTextWithShadow(textRenderer,"ASSET BUNDLES · "+workspace.bundles.size(),9,y,0xFF81919D);y+=18;
            int addY=y;row(c,mx,my,6,addY,LEFT-12,"+ Add bundle",0xFF75B9E7,this::addBundle);y+=23;
            for(int i=0;i<workspace.bundles.size();i++){
                EventAuthoringModel.Bundle bundle=workspace.bundles.get(i);int index=i,yy=y;
                row(c,mx,my,6,yy,LEFT-12,bundle.id,i==bundleIndex?0xFFFFD16A:0xFFD6E0E5,()->selectBundle(index));y+=22;
            }
        }
        y+=10;c.drawTextWithShadow(textRenderer,"SAVED WORKSPACES",9,y,0xFF81919D);y+=18;
        for(String name:EventWorkspaceStore.list()){int yy=y;row(c,mx,my,6,yy,LEFT-12,name,0xFFB8C6CE,()->loadWorkspace(name));y+=22;}
        int yy=y;row(c,mx,my,6,yy,LEFT-12,"Restore autosave",0xFF8FB4CF,this::restoreAutosave);
    }

    private void drawCenter(DrawContext c,int mx,int my) {
        int x=LEFT+12,right=width-RIGHT-12;
        if(right<=x)return;
        int y=TOP+12-(int)centerScroll;
        if(mode==Mode.BUNDLES){drawBundleCenter(c,mx,my,x,right,y);return;}
        EventAuthoringModel.Phase phase=currentPhase();
        if(phase==null){c.drawTextWithShadow(textRenderer,"No phase",x,y,0xFFFF9C80);return;}
        c.drawTextWithShadow(textRenderer,"PHASE  "+phase.id,x,y,0xFFF0F5F7);
        if(phase.id.equals(workspace.program.initialPhase))c.drawTextWithShadow(textRenderer,"INITIAL",right-48,y,0xFFFFD16A);
        y+=24;
        y=drawActionSection(c,mx,my,x,right,y,"ON ENTER",phase.onEnter);
        y+=8;y=drawActionSection(c,mx,my,x,right,y,"ON EXIT",phase.onExit);
        y+=10;
        c.drawTextWithShadow(textRenderer,"TRANSITIONS",x,y,0xFF8293A0);y+=18;
        int addY=y;row(c,mx,my,x,addY,right-x,"+ Add transition",0xFF75B9E7,()->addTransition(phase));y+=24;
        for(int i=0;i<phase.transitions.size();i++){
            EventAuthoringModel.Transition transition=phase.transitions.get(i);int index=i,yy=y;
            String label="→ "+transition.targetPhase+"   ["+EventAuthoringModel.describe(transition.condition)+"]   p"+transition.priority;
            row(c,mx,my,x,yy,right-x,label,0xFFE0E8EC,()->select(new Selection(Kind.TRANSITION,transition,phase.transitions,index)));
            y+=22;
            int conditionY=y;
            row(c,mx,my,x+18,conditionY,right-x-18,"Condition: "+EventAuthoringModel.describe(transition.condition),0xFFC49CEF,
                    ()->select(new Selection(Kind.CONDITION,transition.condition,transition,null)));
            y+=22;
            for(int a=0;a<transition.actions.size();a++){
                EventAuthoringModel.Action action=transition.actions.get(a);int ai=a,ay=y;
                row(c,mx,my,x+18,ay,right-x-18,"↳ "+EventAuthoringModel.describe(action),0xFFA9C7D9,
                        ()->select(new Selection(Kind.ACTION,action,transition.actions,ai)));y+=22;
            }
            int actionY=y;row(c,mx,my,x+18,actionY,right-x-18,"+ Transition action",0xFF6FAFD7,()->addAction(transition.actions));y+=27;
        }
    }

    private int drawActionSection(DrawContext c,int mx,int my,int x,int right,int y,String title,ArrayList<EventAuthoringModel.Action> actions){
        c.drawTextWithShadow(textRenderer,title,x,y,0xFF8293A0);y+=18;
        for(int i=0;i<actions.size();i++){
            EventAuthoringModel.Action action=actions.get(i);int index=i,yy=y;
            row(c,mx,my,x,yy,right-x,EventAuthoringModel.describe(action),0xFFDCE5EA,()->select(new Selection(Kind.ACTION,action,actions,index)));y+=22;
        }
        int addY=y;row(c,mx,my,x,addY,right-x,"+ Add action",0xFF75B9E7,()->addAction(actions));return y+24;
    }

    private void drawBundleCenter(DrawContext c,int mx,int my,int x,int right,int y){
        EventAuthoringModel.Bundle bundle=currentBundle();
        if(bundle==null){c.drawTextWithShadow(textRenderer,"No bundle",x,y,0xFFFF9C80);return;}
        c.drawTextWithShadow(textRenderer,"BUNDLE  "+bundle.id,x,y,0xFFF0F5F7);y+=26;
        c.drawTextWithShadow(textRenderer,"RESOURCE MANAGER IDS",x,y,0xFF8293A0);y+=18;
        for(int i=0;i<bundle.resources.size();i++){int index=i,yy=y;String value=bundle.resources.get(i);row(c,mx,my,x,yy,right-x,value,0xFFDCE5EA,()->select(new Selection(Kind.LIST_ENTRY,new ListRef(bundle.resources,index),bundle.resources,index)));y+=22;}
        int ry=y;row(c,mx,my,x,ry,right-x,"+ Resource",0xFF75B9E7,()->addListEntry(bundle.resources,"minecraft:textures/block/stone.png"));y+=30;
        c.drawTextWithShadow(textRenderer,"LOGICAL MODEL / ANIMATION IDS",x,y,0xFF8293A0);y+=18;
        for(int i=0;i<bundle.logicalAssets.size();i++){int index=i,yy=y;String value=bundle.logicalAssets.get(i);row(c,mx,my,x,yy,right-x,value,0xFFDCE5EA,()->select(new Selection(Kind.LIST_ENTRY,new ListRef(bundle.logicalAssets,index),bundle.logicalAssets,index)));y+=22;}
        int ly=y;row(c,mx,my,x,ly,right-x,"+ Logical asset",0xFF75B9E7,()->addListEntry(bundle.logicalAssets,"cinefx_gui:model"));
    }

    private void drawInspector(DrawContext c,int mx,int my){
        int x=width-RIGHT+10,y=TOP+10;
        c.drawTextWithShadow(textRenderer,"INSPECTOR",x,y,0xFF81919D);y+=18;
        c.drawTextWithShadow(textRenderer,selectionTitle(),x,y,0xFFF0F4F7);y+=23;
        if(selection!=null&&(selection.kind==Kind.ACTION||selection.kind==Kind.CONDITION)){
            String type=selection.kind==Kind.ACTION?((EventAuthoringModel.Action)selection.node).type.name():((EventAuthoringModel.Condition)selection.node).type.name();
            inspectorButton(c,mx,my,x,y,RIGHT-20,"Type: "+type,this::cycleType);y+=28;
        }
        if(selection!=null&&selection.kind==Kind.PHASE){inspectorButton(c,mx,my,x,y,RIGHT-20,"Set as initial",()->{workspace.program.initialPhase=((EventAuthoringModel.Phase)selection.node).id;changed();});y+=28;}
        if(selection!=null&&selection.kind==Kind.CONDITION){
            EventAuthoringModel.Condition condition=(EventAuthoringModel.Condition)selection.node;
            if(condition.type==EventAuthoringModel.ConditionType.ALL||condition.type==EventAuthoringModel.ConditionType.ANY||condition.type==EventAuthoringModel.ConditionType.NOT){
                inspectorButton(c,mx,my,x,y,RIGHT-20,"+ Child condition",()->addConditionChild(condition));y+=28;
                for(int i=0;i<condition.children.size();i++){EventAuthoringModel.Condition child=condition.children.get(i);int index=i,yy=y;row(c,mx,my,x,yy,RIGHT-20,"  "+EventAuthoringModel.describe(child),0xFFC8A8EE,()->select(new Selection(Kind.CONDITION,child,condition.children,index)));y+=22;}
            }
        }
        y=TOP+54;
        for(int i=0;i<bindings.size()&&i<fields.length;i++){
            FieldBinding binding=bindings.get(i);TextFieldWidget field=fields[i];
            c.drawTextWithShadow(textRenderer,binding.label,x,y,0xFF788A96);field.setX(x);field.setY(y+12);field.setWidth(RIGHT-20);y+=37;
        }
        y=Math.max(y+5,TOP+54+bindings.size()*37+5);
        if(selection!=null){
            Map<String,String> map=editableMap(selection);
            if(map!=null){c.drawTextWithShadow(textRenderer,"PARAMETERS / METADATA",x,y,0xFF8293A0);y+=18;
                for(Map.Entry<String,String> entry:new ArrayList<>(map.entrySet())){int yy=y;String key=entry.getKey();row(c,mx,my,x,yy,RIGHT-20,key+" = "+entry.getValue(),0xFFB9C7CF,()->select(new Selection(Kind.MAP_ENTRY,new MapRef(map,key),map,null)));y+=22;}
                int myy=y;row(c,mx,my,x,myy,RIGHT-20,"+ Parameter",0xFF75B9E7,()->addMapEntry(map));y+=27;}
            if(canDelete(selection)){int deleteY=y+5;inspectorButton(c,mx,my,x,deleteY,RIGHT-20,"Delete selected",this::deleteSelected);}
        }
    }

    private Map<String,String> editableMap(Selection selection){
        if(selection==null)return null;
        if(selection.kind==Kind.PROGRAM)return workspace.program.metadata;
        if(selection.kind==Kind.BUNDLE)return ((EventAuthoringModel.Bundle)selection.node).metadata;
        if(selection.kind==Kind.ACTION){EventAuthoringModel.Action a=(EventAuthoringModel.Action)selection.node;return a.type==EventAuthoringModel.ActionType.PLAY||a.type==EventAuthoringModel.ActionType.MARKER?a.parameters:null;}
        return null;
    }

    private void configureFields(){
        bindings.clear();
        if(selection==null){hideFields();return;}
        switch(selection.kind){
            case PROGRAM->{
                bind("Workspace name",()->workspace.name,v->workspace.name=v);
                bind("Program ID",()->workspace.program.id,v->workspace.program.id=v);
                bind("Initial phase",()->workspace.program.initialPhase,v->workspace.program.initialPhase=v);
            }
            case PHASE->{EventAuthoringModel.Phase p=(EventAuthoringModel.Phase)selection.node;bind("Phase ID",()->p.id,v->renamePhase(p,v));}
            case TRANSITION->{EventAuthoringModel.Transition t=(EventAuthoringModel.Transition)selection.node;bind("Target phase",()->t.targetPhase,v->t.targetPhase=v);bind("Priority",()->Integer.toString(t.priority),v->t.priority=parseInt(v,t.priority));}
            case ACTION->configureAction((EventAuthoringModel.Action)selection.node);
            case CONDITION->configureCondition((EventAuthoringModel.Condition)selection.node);
            case BUNDLE->{EventAuthoringModel.Bundle b=(EventAuthoringModel.Bundle)selection.node;bind("Bundle ID",()->b.id,v->b.id=v);}
            case LIST_ENTRY->{ListRef ref=(ListRef)selection.node;bind("Identifier",ref::get,ref::set);}
            case MAP_ENTRY->{MapRef ref=(MapRef)selection.node;bind("Key",ref::key,ref::rename);bind("Value",ref::value,ref::setValue);}
        }
        refreshFields();
    }

    private void configureAction(EventAuthoringModel.Action a){
        switch(a.type){
            case PLAY->{bind("Scene ID",()->a.id,v->a.id=v);bind("Start offset ticks",()->Long.toString(a.startOffsetTicks),v->a.startOffsetTicks=parseLong(v,a.startOffsetTicks));bind("Seed salt",()->Long.toString(a.seedSalt),v->a.seedSalt=parseLong(v,a.seedSalt));}
            case STOP->bind("Scene ID",()->a.id,v->a.id=v);
            case PRELOAD->bind("Bundle ID",()->a.id,v->a.id=v);
            case SET_VARIABLE->{bind("Variable",()->a.key,v->a.key=v);bind("Value",()->a.value,v->a.value=v);}
            case ADD_VARIABLE->{bind("Variable",()->a.key,v->a.key=v);bind("Delta",()->fmt(a.number),v->a.number=parseDouble(v,a.number));}
            case MARKER->bind("Marker name",()->a.value,v->a.value=v);
        }
    }

    private void configureCondition(EventAuthoringModel.Condition c){
        switch(c.type){
            case ALWAYS,ALL,ANY,NOT->{ }
            case AFTER->bind("Ticks",()->fmt(c.number),v->c.number=parseDouble(v,c.number));
            case VARIABLE_EQUALS->{bind("Variable",()->c.key,v->c.key=v);bind("Expected",()->c.value,v->c.value=v);}
            case VARIABLE_AT_LEAST->{bind("Variable",()->c.key,v->c.key=v);bind("Minimum",()->fmt(c.number),v->c.number=parseDouble(v,c.number));}
            case ASSETS_READY->bind("Bundle ID",()->c.id,v->c.id=v);
        }
    }

    private void bind(String label,Supplier<String> get,Consumer<String> set){bindings.add(new FieldBinding(label,get,set));}
    private void refreshFields(){configuringFields=true;for(int i=0;i<fields.length;i++){boolean visible=i<bindings.size();fields[i].setVisible(visible);if(visible)fields[i].setText(bindings.get(i).get.get());else fields[i].setText("");}configuringFields=false;}
    private void hideFields(){configuringFields=true;for(TextFieldWidget field:fields)if(field!=null)field.setVisible(false);configuringFields=false;}
    private void applyField(int index,String value){if(configuringFields||index<0||index>=bindings.size())return;bindings.get(index).set.accept(value);changed();}

    private void selectProgram(){select(new Selection(Kind.PROGRAM,workspace.program,null,null));}
    private void selectPhase(int index){if(index<0||index>=workspace.program.phases.size())return;phaseIndex=index;centerScroll=0;select(new Selection(Kind.PHASE,workspace.program.phases.get(index),workspace.program.phases,index));}
    private void selectBundle(int index){if(index<0||index>=workspace.bundles.size())return;bundleIndex=index;centerScroll=0;select(new Selection(Kind.BUNDLE,workspace.bundles.get(index),workspace.bundles,index));}
    private void selectCurrentBundle(){if(workspace.bundles.isEmpty())addBundle();else selectBundle(Math.max(0,Math.min(bundleIndex,workspace.bundles.size()-1)));}
    private void select(Selection next){selection=next;configureFields();}

    private EventAuthoringModel.Phase currentPhase(){if(workspace.program.phases.isEmpty())return null;phaseIndex=Math.max(0,Math.min(phaseIndex,workspace.program.phases.size()-1));return workspace.program.phases.get(phaseIndex);}
    private EventAuthoringModel.Bundle currentBundle(){if(workspace.bundles.isEmpty())return null;bundleIndex=Math.max(0,Math.min(bundleIndex,workspace.bundles.size()-1));return workspace.bundles.get(bundleIndex);}

    private void addPhase(){String base="phase";int n=workspace.program.phases.size()+1;String id=base+n;while(hasPhase(id))id=base+(++n);EventAuthoringModel.Phase p=new EventAuthoringModel.Phase(id);workspace.program.phases.add(p);changed();selectPhase(workspace.program.phases.size()-1);}
    private boolean hasPhase(String id){for(EventAuthoringModel.Phase p:workspace.program.phases)if(p.id.equals(id))return true;return false;}
    private void renamePhase(EventAuthoringModel.Phase phase,String id){String old=phase.id;phase.id=id;if(old.equals(workspace.program.initialPhase))workspace.program.initialPhase=id;for(EventAuthoringModel.Phase p:workspace.program.phases)for(EventAuthoringModel.Transition t:p.transitions)if(old.equals(t.targetPhase))t.targetPhase=id;}
    private void addAction(ArrayList<EventAuthoringModel.Action> owner){EventAuthoringModel.Action a=new EventAuthoringModel.Action();owner.add(a);changed();select(new Selection(Kind.ACTION,a,owner,owner.size()-1));}
    private void addTransition(EventAuthoringModel.Phase phase){EventAuthoringModel.Transition t=new EventAuthoringModel.Transition();if(workspace.program.phases.size()>1)t.targetPhase=workspace.program.phases.get((phaseIndex+1)%workspace.program.phases.size()).id;phase.transitions.add(t);changed();select(new Selection(Kind.TRANSITION,t,phase.transitions,phase.transitions.size()-1));}
    private void addBundle(){EventAuthoringModel.Bundle b=EventAuthoringModel.Bundle.starter();String base="cinefx_gui:assets";int n=workspace.bundles.size()+1;b.id=base+n;workspace.bundles.add(b);changed();selectBundle(workspace.bundles.size()-1);}
    private void addConditionChild(EventAuthoringModel.Condition parent){if(parent.children==null)parent.children=new ArrayList<>();if(parent.type==EventAuthoringModel.ConditionType.NOT)parent.children.clear();EventAuthoringModel.Condition child=new EventAuthoringModel.Condition();parent.children.add(child);changed();select(new Selection(Kind.CONDITION,child,parent.children,parent.children.size()-1));}
    private void addListEntry(ArrayList<String> list,String value){list.add(value);changed();select(new Selection(Kind.LIST_ENTRY,new ListRef(list,list.size()-1),list,list.size()-1));}
    private void addMapEntry(Map<String,String> map){String base="key";int n=1,keyN=1;String key=base+keyN;while(map.containsKey(key))key=base+(++keyN);map.put(key,"value");changed();select(new Selection(Kind.MAP_ENTRY,new MapRef(map,key),map,null));}

    private void cycleType(){
        if(selection==null)return;
        if(selection.kind==Kind.ACTION){EventAuthoringModel.Action a=(EventAuthoringModel.Action)selection.node;EventAuthoringModel.ActionType[] all=EventAuthoringModel.ActionType.values();a.type=all[(a.type.ordinal()+1)%all.length];}
        else if(selection.kind==Kind.CONDITION){EventAuthoringModel.Condition c=(EventAuthoringModel.Condition)selection.node;EventAuthoringModel.ConditionType[] all=EventAuthoringModel.ConditionType.values();c.type=all[(c.type.ordinal()+1)%all.length];if(c.type==EventAuthoringModel.ConditionType.NOT&&c.children.size()>1)c.children.subList(1,c.children.size()).clear();}
        changed();configureFields();
    }

    private boolean canDelete(Selection s){return s!=null&&s.kind!=Kind.PROGRAM;}
    @SuppressWarnings({"rawtypes","unchecked"})
    private void deleteSelected(){
        if(selection==null)return;
        if(selection.kind==Kind.PHASE&&workspace.program.phases.size()<=1){toast("Program needs one phase");return;}
        if(selection.kind==Kind.CONDITION&&selection.owner instanceof EventAuthoringModel.Transition transition){transition.condition=new EventAuthoringModel.Condition();changed();select(new Selection(Kind.CONDITION,transition.condition,transition,null));return;}
        if(selection.kind==Kind.MAP_ENTRY){MapRef ref=(MapRef)selection.node;ref.map.remove(ref.key);changed();select(selectionForContainer());return;}
        if(selection.kind==Kind.LIST_ENTRY){ListRef ref=(ListRef)selection.node;if(ref.index>=0&&ref.index<ref.list.size())ref.list.remove(ref.index);changed();selectCurrentBundle();return;}
        if(selection.owner instanceof List list){list.remove(selection.node);changed();if(selection.kind==Kind.PHASE){phaseIndex=Math.max(0,Math.min(phaseIndex,workspace.program.phases.size()-1));if(!hasPhase(workspace.program.initialPhase))workspace.program.initialPhase=currentPhase().id;selectPhase(phaseIndex);}else if(selection.kind==Kind.BUNDLE){selectCurrentBundle();}else select(new Selection(Kind.PHASE,currentPhase(),workspace.program.phases,phaseIndex));}
    }

    private Selection selectionForContainer(){if(mode==Mode.BUNDLES)return new Selection(Kind.BUNDLE,currentBundle(),workspace.bundles,bundleIndex);return new Selection(Kind.PHASE,currentPhase(),workspace.program.phases,phaseIndex);}

    private void newWorkspace(){workspace=EventAuthoringModel.Workspace.fresh();phaseIndex=0;bundleIndex=0;workspace.dirty=true;mode=Mode.PROGRAM;selectProgram();toast("New event workspace");}
    private void saveWorkspace(){try{EventWorkspaceStore.save(workspace,workspace.sourceName==null?workspace.name:workspace.sourceName);toast("Saved and published");}catch(Exception e){toast("Save failed: "+compact(e.getMessage()));}}
    private void validateWorkspace(){List<String> errors=EventWorkspaceStore.validate(workspace);toast(errors.isEmpty()?"Valid EventProgramSpec + bundles":errors.getFirst());}
    private void publishWorkspace(){try{EventWorkspaceStore.validateAndPublish(workspace);toast("Published to CineFX registries");}catch(RuntimeException e){toast("Publish failed: "+compact(e.getMessage()));}}
    private void loadWorkspace(String name){try{workspace=EventWorkspaceStore.load(name);phaseIndex=0;bundleIndex=0;mode=Mode.PROGRAM;selectProgram();toast("Loaded "+name);}catch(IOException|RuntimeException e){toast("Load failed: "+compact(e.getMessage()));}}
    private void restoreAutosave(){EventAuthoringModel.Workspace loaded=EventWorkspaceStore.loadAutosave();if(loaded==null){toast("No event autosave");return;}workspace=loaded;phaseIndex=0;bundleIndex=0;selectProgram();toast("Autosave restored");}
    private void closeEditor(){EventWorkspaceStore.autosave(workspace);if(client!=null)client.setScreen(parent);}

    private void changed(){workspace.dirty=true;autosaveTicks=0;}
    private void toast(String text){status=text;statusUntil=System.currentTimeMillis()+5000;}
    private String statusText(){return System.currentTimeMillis()<=statusUntil?status:"Ctrl+S save · Ctrl+Enter publish · Delete remove · click Type to cycle";}
    private String selectionTitle(){if(selection==null)return"Nothing selected";return switch(selection.kind){case PROGRAM->"Program";case PHASE->"Phase";case ACTION->"Action";case TRANSITION->"Transition";case CONDITION->"Condition";case BUNDLE->"Asset bundle";case LIST_ENTRY->"Asset identifier";case MAP_ENTRY->"Map entry";};}

    @Override public boolean mouseClicked(Click click,boolean doubled){
        if(super.mouseClicked(click,doubled)&&focusedField())return true;
        for(Hit hit:List.copyOf(hits))if(hit.contains(click.x(),click.y())){hit.action.run();return true;}
        return false;
    }

    @Override public boolean mouseScrolled(double mx,double my,double horizontalAmount,double verticalAmount){if(mx<LEFT){leftScroll=Math.max(0,leftScroll-verticalAmount*24);return true;}if(mx<width-RIGHT){centerScroll=Math.max(0,centerScroll-verticalAmount*28);return true;}return super.mouseScrolled(mx,my,horizontalAmount,verticalAmount);}

    @Override public boolean keyPressed(KeyInput input){
        if(focusedField()){if(input.key()==GLFW.GLFW_KEY_ESCAPE){for(TextFieldWidget field:fields)field.setFocused(false);return true;}if(super.keyPressed(input))return true;}
        boolean ctrl=(input.modifiers()&GLFW.GLFW_MOD_CONTROL)!=0;
        if(ctrl&&input.key()==GLFW.GLFW_KEY_S){saveWorkspace();return true;}
        if(ctrl&&(input.key()==GLFW.GLFW_KEY_ENTER||input.key()==GLFW.GLFW_KEY_KP_ENTER)){publishWorkspace();return true;}
        if(input.key()==GLFW.GLFW_KEY_DELETE){deleteSelected();return true;}
        if(input.key()==GLFW.GLFW_KEY_ESCAPE){closeEditor();return true;}
        return super.keyPressed(input);
    }

    private boolean focusedField(){for(TextFieldWidget field:fields)if(field!=null&&field.isFocused())return true;return false;}
    private void topButton(DrawContext c,int mx,int my,int x,int y,int w,String label,Runnable action){boolean hover=inside(mx,my,x,y,x+w,y+22);c.fill(x,y,x+w,y+22,hover?0xFF35434E:0xFF273038);c.drawTextWithShadow(textRenderer,label,x+(w-textRenderer.getWidth(label))/2,y+7,0xFFE8EEF2);hits.add(new Hit(x,y,x+w,y+22,action));}
    private void inspectorButton(DrawContext c,int mx,int my,int x,int y,int w,String label,Runnable action){boolean hover=inside(mx,my,x,y,x+w,y+22);c.fill(x,y,x+w,y+22,hover?0xFF35434E:0xFF252D35);c.drawTextWithShadow(textRenderer,trim(label,w-12),x+6,y+7,0xFFDCE5EA);hits.add(new Hit(x,y,x+w,y+22,action));}
    private void row(DrawContext c,int mx,int my,int x,int y,int w,String label,int color,Runnable action){if(y<TOP-20||y>height-18)return;boolean hover=inside(mx,my,x,y-2,x+w,y+18);c.fill(x,y-2,x+w,y+18,hover?0xFF28343D:0xFF1C2329);c.drawTextWithShadow(textRenderer,trim(label,w-12),x+6,y+4,color);hits.add(new Hit(x,y-2,x+w,y+18,action));}
    private static boolean inside(double x,double y,double x1,double y1,double x2,double y2){return x>=x1&&x<=x2&&y>=y1&&y<=y2;}
    private String trim(String value,int max){if(value==null)return"";if(textRenderer.getWidth(value)<=max)return value;String out=value;while(out.length()>1&&textRenderer.getWidth(out+"…")>max)out=out.substring(0,out.length()-1);return out+"…";}
    private static String compact(String value){return value==null?"invalid":value.replace('\n',' ');}
    private static int parseInt(String value,int fallback){try{return Integer.parseInt(value.trim());}catch(Exception ignored){return fallback;}}
    private static long parseLong(String value,long fallback){try{return Long.parseLong(value.trim());}catch(Exception ignored){return fallback;}}
    private static double parseDouble(String value,double fallback){try{return Double.parseDouble(value.trim());}catch(Exception ignored){return fallback;}}
    private static String fmt(double value){return String.format(Locale.ROOT,"%.4f",value).replaceAll("0+$","").replaceAll("\\.$","");}

    private record Hit(double x1,double y1,double x2,double y2,Runnable action){boolean contains(double x,double y){return inside(x,y,x1,y1,x2,y2);}}
    private record Selection(Kind kind,Object node,Object owner,Integer index){}
    private record FieldBinding(String label,Supplier<String> get,Consumer<String> set){}

    private static final class ListRef {
        private final ArrayList<String> list; private int index;
        private ListRef(ArrayList<String> list,int index){this.list=list;this.index=index;}
        String get(){return index>=0&&index<list.size()?list.get(index):"";}
        void set(String value){if(index>=0&&index<list.size())list.set(index,value);}
    }

    private static final class MapRef {
        private final Map<String,String> map; private String key;
        private MapRef(Map<String,String> map,String key){this.map=map;this.key=key;}
        String key(){return key;} String value(){return map.getOrDefault(key,"");}
        void setValue(String value){map.put(key,value);}
        void rename(String next){if(next==null||next.equals(key))return;String value=map.remove(key);key=next;map.put(key,value==null?"":value);}
    }
}
