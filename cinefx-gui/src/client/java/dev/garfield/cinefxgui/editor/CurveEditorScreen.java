package dev.garfield.cinefxgui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.garfield.cinefx.api.CubicBezier;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Blender-style graph editor for the real keyframes stored in a CineFX GUI scene preset. */
public final class CurveEditorScreen extends Screen {
    private static final int TOP = 34;
    private static final int LEFT = 258;
    private static final int PAD = 18;

    private enum Drag { NONE, KEY, HANDLE_OUT, HANDLE_IN }

    private final Screen parent;
    private final EditorModel.Project project;
    private final EditorModel.Element element;
    private final PreviewController preview;
    private final EditorModel.History history;
    private List<CurveChannels.Channel> channels;
    private int channelIndex;
    private int selectedKey = -1;
    private JsonObject activeKey;
    private Drag drag = Drag.NONE;
    private double leftScroll;
    private double tickMin, tickMax, valueMin, valueMax;
    private boolean fitted;

    public CurveEditorScreen(Screen parent, EditorModel.Project project, EditorModel.Element element,
                             PreviewController preview, EditorModel.History history) {
        super(Text.literal("CineFX Curve Editor"));
        this.parent = parent;
        this.project = project;
        this.element = element;
        this.preview = preview;
        this.history = history;
        this.channels = CurveChannels.discover(element);
    }

    @Override protected void init() {
        if (!fitted) { fit(); fitted = true; }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) { }

    @Override public void tick() {
        super.tick();
        if (client != null && client.world != null) preview.tick(client);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float deltaTicks) {
        c.fill(0, 0, width, height, 0xFC11151A);
        c.fill(0, 0, width, TOP, 0xFF1C2229);
        c.fill(0, TOP, LEFT, height, 0xFF171C22);
        c.drawTextWithShadow(textRenderer, "CineFX Curve Editor", 10, 12, 0xFFF0F4F7);
        button(c, mx, my, 145, 6, 48, "Back");
        button(c, mx, my, 198, 6, 40, "Fit");
        button(c, mx, my, 243, 6, 58, "Bezier");
        button(c, mx, my, 306, 6, 52, "Linear");
        String help = "drag keys · drag tangent handles · wheel time zoom · Ctrl+wheel value zoom · B Bezier · F fit";
        c.drawTextWithShadow(textRenderer, help, 370, 12, 0xFF8394A1);

        drawChannels(c, mx, my);
        drawGraph(c, mx, my);
        super.render(c, mx, my, deltaTicks);
    }

    private void drawChannels(DrawContext c, int mx, int my) {
        c.drawTextWithShadow(textRenderer, "CHANNELS · " + channels.size(), 10, TOP + 10, 0xFF8293A0);
        c.drawTextWithShadow(textRenderer, element == null ? "" : trim(element.key(), 220), 10, TOP + 24, 0xFFC9D5DC);
        int y = TOP + 48 - (int)leftScroll;
        for (int i = 0; i < channels.size(); i++) {
            CurveChannels.Channel channel = channels.get(i);
            if (y > TOP + 36 && y < height - 20) {
                boolean selected = i == channelIndex;
                boolean hover = mx >= 6 && mx <= LEFT - 7 && my >= y - 2 && my <= y + 18;
                c.fill(6, y - 2, LEFT - 7, y + 18, selected ? 0xFF304D62 : hover ? 0xFF27333D : 0xFF1B2228);
                c.drawTextWithShadow(textRenderer, trim(channel.label(), LEFT - 34), 12, y + 4, selected ? 0xFFF2F6F8 : 0xFFB8C5CD);
            }
            y += 21;
        }
        if (channels.isEmpty()) {
            c.drawTextWithShadow(textRenderer, "No numeric keyframed channel", 10, TOP + 58, 0xFFFFA07F);
            c.drawTextWithShadow(textRenderer, "Add keys/tracks in Studio first.", 10, TOP + 72, 0xFF8796A0);
        }
    }

    private void drawGraph(DrawContext c, int mx, int my) {
        int x1 = LEFT + PAD, y1 = TOP + PAD, x2 = width - PAD, y2 = height - 34;
        if (x2 <= x1 + 20 || y2 <= y1 + 20) return;
        c.fill(x1, y1, x2, y2, 0xFF101419);
        for (int i = 0; i <= 10; i++) {
            int x = x1 + (x2 - x1) * i / 10;
            int y = y1 + (y2 - y1) * i / 10;
            c.fill(x, y1, x + 1, y2, i == 0 || i == 10 ? 0x554D5A64 : 0x223C4851);
            c.fill(x1, y, x2, y + 1, i == 0 || i == 10 ? 0x554D5A64 : 0x223C4851);
        }
        if (channels.isEmpty()) return;
        CurveChannels.Channel channel = channel();
        if (channel == null) return;

        String range = String.format(Locale.ROOT, "%.2f..%.2f ticks   %.3f..%.3f", tickMin, tickMax, valueMin, valueMax);
        c.drawTextWithShadow(textRenderer, trim(channel.label(), Math.max(80, x2 - x1 - 220)), x1 + 5, y1 + 5, 0xFFE2E9ED);
        c.drawTextWithShadow(textRenderer, range, x2 - textRenderer.getWidth(range) - 5, y1 + 5, 0xFF71818C);

        ViewportGizmo.ScreenPoint previous = null;
        int samples = Math.max(80, Math.min(500, x2 - x1));
        for (int i = 0; i <= samples; i++) {
            double tick = tickMin + (tickMax - tickMin) * i / samples;
            double value = channel.sample(tick);
            ViewportGizmo.ScreenPoint point = new ViewportGizmo.ScreenPoint(graphX(tick,x1,x2), graphY(value,y1,y2), 1, true);
            if (previous != null) ViewportGizmo.drawLine(c, previous.x(), previous.y(), point.x(), point.y(), 0xFF6FC5F2, 2);
            previous = point;
        }

        for (int i = 0; i < channel.size(); i++) {
            JsonObject key = channel.key(i);
            int x = (int)Math.round(graphX(channel.tick(i), x1, x2));
            int y = (int)Math.round(graphY(channel.value(i), y1, y2));
            boolean selected = key == activeKey || i == selectedKey;
            c.fill(x - (selected ? 5 : 4), y - (selected ? 5 : 4), x + (selected ? 6 : 5), y + (selected ? 6 : 5), 0xCC000000);
            c.fill(x - 3, y - 3, x + 4, y + 4, selected ? 0xFFFFD260 : 0xFFDDE7EC);
        }

        int index = activeIndex(channel);
        if (index >= 0 && index < channel.size() - 1) drawBezierHandles(c, channel, index, x1, y1, x2, y2);

        double playheadLocal = preview.currentTick() - element.startTick();
        int playX = (int)Math.round(graphX(playheadLocal, x1, x2));
        if (playX >= x1 && playX <= x2) c.fill(playX, y1, playX + 1, y2, 0x88FFD468);
        c.drawTextWithShadow(textRenderer, "Selected key " + (index < 0 ? "-" : index + " · " + fmt(channel.tick(index)) + " / " + fmt(channel.value(index))),
                x1 + 5, y2 + 9, 0xFF8FA0AB);
    }

    private void drawBezierHandles(DrawContext c, CurveChannels.Channel channel, int index, int x1, int y1, int x2, int y2) {
        JsonObject a = channel.key(index), b = channel.key(index + 1);
        CubicBezier bezier = channel.bezier(a);
        if (bezier == null) return;
        double ta = channel.tick(index), tb = channel.tick(index + 1), va = channel.value(index), vb = channel.value(index + 1);
        double dt = tb - ta, dv = channel.angular() ? shortest(vb - va) : vb - va;
        double c1t = ta + bezier.x1() * dt, c1v = va + bezier.y1() * dv;
        double c2t = ta + bezier.x2() * dt, c2v = va + bezier.y2() * dv;
        double ax = graphX(ta,x1,x2), ay = graphY(va,y1,y2), bx = graphX(tb,x1,x2), by = graphY(vb,y1,y2);
        double h1x = graphX(c1t,x1,x2), h1y = graphY(c1v,y1,y2), h2x = graphX(c2t,x1,x2), h2y = graphY(c2v,y1,y2);
        ViewportGizmo.drawLine(c, ax, ay, h1x, h1y, 0xFFAD8CF0, 1);
        ViewportGizmo.drawLine(c, bx, by, h2x, h2y, 0xFFAD8CF0, 1);
        c.fill((int)h1x-4,(int)h1y-4,(int)h1x+5,(int)h1y+5,0xFFB89CF4);
        c.fill((int)h2x-4,(int)h2y-4,(int)h2x+5,(int)h2y+5,0xFFB89CF4);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && my >= 6 && my <= 28) {
            if (mx >= 145 && mx <= 193) { closeEditor(); return true; }
            if (mx >= 198 && mx <= 238) { fit(); return true; }
            if (mx >= 243 && mx <= 301) { makeBezier(); return true; }
            if (mx >= 306 && mx <= 358) { makeLinear(); return true; }
        }
        if (mx < LEFT && my >= TOP + 36) {
            int index = (int)Math.floor((my - (TOP + 48) + leftScroll + 2) / 21.0);
            if (index >= 0 && index < channels.size()) {
                channelIndex = index; activeKey = null; selectedKey = -1; fit(); return true;
            }
        }
        int x1 = LEFT + PAD, y1 = TOP + PAD, x2 = width - PAD, y2 = height - 34;
        if (mx < x1 || mx > x2 || my < y1 || my > y2 || channel() == null) return super.mouseClicked(click,doubled);
        CurveChannels.Channel channel = channel();

        int active = activeIndex(channel);
        if (active >= 0 && active < channel.size() - 1 && channel.bezier(channel.key(active)) != null) {
            int handle = handleAt(channel, active, mx, my, x1,y1,x2,y2);
            if (handle != 0) {
                checkpoint(); drag = handle == 1 ? Drag.HANDLE_OUT : Drag.HANDLE_IN; selectedKey = active; activeKey = channel.key(active); return true;
            }
        }

        int hit = keyAt(channel,mx,my,x1,y1,x2,y2);
        if (hit >= 0) {
            selectedKey = hit; activeKey = channel.key(hit);
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) { makeBezier(); return true; }
            if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && !element.locked) { checkpoint(); drag = Drag.KEY; return true; }
            return true;
        }
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double tick = tickAt(mx,x1,x2);
            preview.setTick(client, element.startTick() + Math.max(0,tick));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || drag == Drag.NONE || channel() == null || activeKey == null) return super.mouseDragged(click,dx,dy);
        int x1 = LEFT + PAD, y1 = TOP + PAD, x2 = width - PAD, y2 = height - 34;
        CurveChannels.Channel channel = channel();
        if (drag == Drag.KEY) {
            channel.setTick(activeKey, Math.max(0.0, tickAt(click.x(),x1,x2)));
            channel.setValue(activeKey, valueAt(click.y(),y1,y2));
        } else {
            int index = activeIndex(channel);
            if (index < 0 || index >= channel.size()-1) return true;
            JsonObject a = channel.key(index), b = channel.key(index+1);
            CubicBezier current = channel.ensureBezier(a);
            double ta=channel.tick(index),tb=channel.tick(index+1),va=channel.value(index),vb=channel.value(index+1);
            double dt=Math.max(1.0e-6,tb-ta),dv=channel.angular()?shortest(vb-va):vb-va;
            double x = clamp((tickAt(click.x(),x1,x2)-ta)/dt,0,1);
            double y = Math.abs(dv)<1.0e-9
                    ? (drag == Drag.HANDLE_OUT ? current.y1() : current.y2())
                    : (valueAt(click.y(),y1,y2)-va)/dv;
            if (drag == Drag.HANDLE_OUT) channel.setBezier(a,new CubicBezier(x,y,current.x2(),current.y2()));
            else channel.setBezier(a,new CubicBezier(current.x1(),current.y1(),x,y));
        }
        changed(); return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && drag != Drag.NONE) {
            CurveChannels.Channel channel = channel();
            if (channel != null) { channel.sort(); selectedKey = activeIndex(channel); }
            drag = Drag.NONE; changed(); return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontalAmount, double verticalAmount) {
        if (mx < LEFT) { leftScroll = Math.max(0,leftScroll-verticalAmount*24); return true; }
        int x1=LEFT+PAD,y1=TOP+PAD,x2=width-PAD,y2=height-34;
        if (mx<x1||mx>x2||my<y1||my>y2) return super.mouseScrolled(mx,my,horizontalAmount,verticalAmount);
        boolean ctrl = keyDown(GLFW.GLFW_KEY_LEFT_CONTROL)||keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
        double factor = Math.pow(1.16,-verticalAmount);
        if (ctrl) {
            double anchor=valueAt(my,y1,y2); valueMin=anchor+(valueMin-anchor)*factor; valueMax=anchor+(valueMax-anchor)*factor;
        } else {
            double anchor=tickAt(mx,x1,x2); tickMin=anchor+(tickMin-anchor)*factor; tickMax=anchor+(tickMax-anchor)*factor;
            tickMin=Math.max(-100000,tickMin); tickMax=Math.max(tickMin+.01,tickMax);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key()==GLFW.GLFW_KEY_ESCAPE) { closeEditor(); return true; }
        if (input.key()==GLFW.GLFW_KEY_F) { fit(); return true; }
        if (input.key()==GLFW.GLFW_KEY_B) { makeBezier(); return true; }
        if (input.key()==GLFW.GLFW_KEY_L) { makeLinear(); return true; }
        if (input.key()==GLFW.GLFW_KEY_SPACE) { preview.togglePlay(client); return true; }
        if (input.key()==GLFW.GLFW_KEY_DELETE && activeKey!=null && channel()!=null && channel().size()>1) {
            checkpoint(); removeIdentity(channel().keys(),activeKey); activeKey=null; selectedKey=-1; changed(); return true;
        }
        return super.keyPressed(input);
    }

    private void makeBezier() {
        CurveChannels.Channel channel=channel(); int index=activeIndex(channel);
        if(channel==null||index<0||index>=channel.size()-1||element.locked)return;
        checkpoint(); channel.ensureBezier(channel.key(index)); changed();
    }

    private void makeLinear() {
        CurveChannels.Channel channel=channel(); int index=activeIndex(channel);
        if(channel==null||index<0||index>=channel.size()||element.locked)return;
        checkpoint(); JsonObject key=channel.key(index); key.remove("bezier"); key.addProperty("easing","LINEAR"); changed();
    }

    private void fit() {
        CurveChannels.Channel channel=channel();
        if(channel==null){tickMin=0;tickMax=200;valueMin=-1;valueMax=1;return;}
        double[] b=channel.bounds(); double tx=Math.max(1,b[1]-b[0])*.08, vy=Math.max(1.0e-6,b[3]-b[2])*.14;
        tickMin=Math.max(0,b[0]-tx); tickMax=b[1]+tx; valueMin=b[2]-vy; valueMax=b[3]+vy;
    }

    private CurveChannels.Channel channel(){return channels.isEmpty()||channelIndex<0||channelIndex>=channels.size()?null:channels.get(channelIndex);}
    private int activeIndex(CurveChannels.Channel channel){
        if(channel==null)return -1; if(activeKey!=null)for(int i=0;i<channel.size();i++)if(channel.key(i)==activeKey)return i;
        return selectedKey>=0&&selectedKey<channel.size()?selectedKey:-1;
    }

    private int keyAt(CurveChannels.Channel channel,double mx,double my,int x1,int y1,int x2,int y2){
        int best=-1; double distance=9;
        for(int i=0;i<channel.size();i++){double x=graphX(channel.tick(i),x1,x2),y=graphY(channel.value(i),y1,y2),d=Math.hypot(mx-x,my-y);if(d<distance){distance=d;best=i;}}
        return best;
    }

    private int handleAt(CurveChannels.Channel channel,int index,double mx,double my,int x1,int y1,int x2,int y2){
        JsonObject a=channel.key(index); CubicBezier curve=channel.bezier(a); if(curve==null)return 0;
        double ta=channel.tick(index),tb=channel.tick(index+1),va=channel.value(index),vb=channel.value(index+1),dt=tb-ta,dv=channel.angular()?shortest(vb-va):vb-va;
        double h1x=graphX(ta+curve.x1()*dt,x1,x2),h1y=graphY(va+curve.y1()*dv,y1,y2);
        double h2x=graphX(ta+curve.x2()*dt,x1,x2),h2y=graphY(va+curve.y2()*dv,y1,y2);
        if(Math.hypot(mx-h1x,my-h1y)<=9)return 1;if(Math.hypot(mx-h2x,my-h2y)<=9)return 2;return 0;
    }

    private void checkpoint(){if(history!=null)history.checkpoint(project);}
    private void changed(){project.dirty=true;preview.markDirty();}
    private void closeEditor(){if(client!=null)client.setScreen(parent);}
    private boolean keyDown(int key){return client!=null&&GLFW.glfwGetKey(client.getWindow().getHandle(),key)==GLFW.GLFW_PRESS;}

    private void button(DrawContext c,int mx,int my,int x,int y,int w,String text){boolean hover=mx>=x&&mx<=x+w&&my>=y&&my<=y+22;c.fill(x,y,x+w,y+22,hover?0xFF34414C:0xFF262E36);c.drawTextWithShadow(textRenderer,text,x+(w-textRenderer.getWidth(text))/2,y+7,0xFFE8EEF2);}
    private double graphX(double tick,int x1,int x2){return x1+(tick-tickMin)/Math.max(1.0e-9,tickMax-tickMin)*(x2-x1);}
    private double graphY(double value,int y1,int y2){return y2-(value-valueMin)/Math.max(1.0e-9,valueMax-valueMin)*(y2-y1);}
    private double tickAt(double x,int x1,int x2){return tickMin+(x-x1)/Math.max(1,x2-x1)*(tickMax-tickMin);}
    private double valueAt(double y,int y1,int y2){return valueMin+(y2-y)/Math.max(1,y2-y1)*(valueMax-valueMin);}
    private static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    private static double shortest(double degrees){double v=degrees%360.0;if(v>=180)v-=360;if(v< -180)v+=360;return v;}
    private static String fmt(double value){return String.format(Locale.ROOT,"%.3f",value);}
    private String trim(String value,int width){if(value==null)return"";if(textRenderer.getWidth(value)<=width)return value;String out=value;while(out.length()>2&&textRenderer.getWidth(out+"…")>width)out=out.substring(0,out.length()-1);return out+"…";}
    private static void removeIdentity(JsonArray array,JsonObject key){for(int i=0;i<array.size();i++)if(array.get(i)==key){array.remove(i);return;}}
}
