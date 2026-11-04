package dev.questplus.fabric.client;

import com.google.gson.JsonObject;
import dev.questplus.clientdata.QuestPresentation;
import dev.questplus.core.QuestDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Compact pinned-objective tracker, aligned to all nine client HUD positions. */
final class QuestHud {
    private record ObjectiveLine(String label,String modifier,long current,long target) {
        int height() { return 24+(modifier.isBlank()?0:12); }
    }
    private record Panel(QuestDefinition quest,String name,String status,List<ObjectiveLine> lines,int height) {}
    private QuestHud() {}

    static void draw(GuiGraphicsExtractor g) {
        var mc=Minecraft.getInstance();
        var settings=QuestPlusClient.settings;
        var state=QuestPlusClient.STATE;
        if (mc.player==null || TabletSurfaceRenderer.hidesHud() || mc.gui.screen() instanceof QuestScreen) return;

        if (settings.hud && state.ready && !settings.pinned.isEmpty()) {
            List<Panel> panels=new ArrayList<>();
            int total=0,maxHeight=Math.max(65,g.guiHeight()-90);
            for (String id:settings.pinned) {
                var q=state.quests.get(id);
                if (q==null) continue;
                JsonObject progress=state.progress(id), counters=progress.getAsJsonObject("counters");
                List<ObjectiveLine> objectives=new ArrayList<>();
                if (q.kind().equals("information")) {
                    if (!QuestClientState.flag(progress,"completed"))
                        objectives.add(new ObjectiveLine(QuestPlusClient.tr(q.acknowledgement().equals("manual") ? "information_review" : "information_read").getString(),"",0,0));
                } else {
                    for (var objective:q.objectives()) {
                        if (objectives.size()>=2) break;
                        long count=counters!=null&&counters.has(objective.id())?counters.get(objective.id()).getAsLong():0;
                        String base=state.text(objective.text());
                        if (base.isBlank()) {
                            String target=objective.target().replace("#","").replace("minecraft:","").replace('_',' ').replace('/',' ');
                            base=QuestPresentation.objectiveText(objective,mc.options.languageCode,target);
                        }
                        String modifier=objective.conditions()==null?"":QuestConditionText.describe(objective.conditions(),qid->{
                            var other=state.quests.get(qid);
                            return other==null?QuestPlusClient.tr("unknown_quest").getString():state.text(other.title());
                        });
                        objectives.add(new ObjectiveLine(base,modifier,count,QuestPresentation.target(q,objective,progress)));
                    }
                }
                int height=29+objectives.stream().mapToInt(ObjectiveLine::height).sum()+(q.objectives().size()>2?10:0);
                if (!panels.isEmpty() && total+height+5>maxHeight) break;
                panels.add(new Panel(q,state.text(q.title()),QuestPresentation.status(q,progress),List.copyOf(objectives),height));
                total+=height+5;
                if (panels.size()>=3) break;
            }
            if (!panels.isEmpty()) drawPanels(g,mc,panels,total-5,settings.hudPosition);
        }
        QuestToast.draw(g);
    }

    private static void drawPanels(GuiGraphicsExtractor g,Minecraft mc,List<Panel> panels,int height,int position) {
        int w=Math.min(206,g.guiWidth()-14);
        int x=position%3==0?7:position%3==1?(g.guiWidth()-w)/2:g.guiWidth()-w-7;
        int y=position/3==0?34:position/3==1?(g.guiHeight()-height)/2:g.guiHeight()-height-48;
        y=Math.max(5,y);
        for (Panel panel:panels) {
            boolean reward=panel.status.equals("reward_ready");
            boolean done=panel.status.equals("completed");
            int accent=reward?0xffd9b177:done?0xff8d9d87:0xffa89986;
            g.fill(x+1,y+2,x+w+2,y+panel.height+2,0x33000000);
            g.fill(x,y,x+w,y+panel.height,0xa9151718);
            g.fill(x,y,x+2,y+panel.height,accent);
            var identifier=Identifier.tryParse(panel.quest.icon());
            ItemStack icon=identifier!=null&&BuiltInRegistries.ITEM.containsKey(identifier)
                ?new ItemStack(BuiltInRegistries.ITEM.getValue(identifier)):new ItemStack(Items.BOOK);
            g.item(icon,x+7,y+6);
            String title=mc.font.plainSubstrByWidth(panel.name,Math.max(1,w-37));
            g.text(mc.font,title,x+27,y+6,0xfff0eae0,false);
            String caption=reward?QuestPlusClient.tr("hud.claim").getString()
                :done?QuestPlusClient.tr("hud.completed").getString()
                :panel.quest.progression().equals("once")?"":QuestPlusClient.tr("progression."+panel.quest.progression()).getString();
            if (!caption.isEmpty()) g.text(mc.font,mc.font.plainSubstrByWidth(caption,w-36),x+27,y+17,accent,false);

            int row=y+29;
            for (ObjectiveLine line:panel.lines) {
                String count=line.target>0?line.current+"/"+line.target:"";
                int countWidth=mc.font.width(count);
                String label=mc.font.plainSubstrByWidth(line.label,Math.max(1,w-18-countWidth));
                g.text(mc.font,label,x+9,row,0xffd3d1ca,false);
                if (!count.isEmpty()) g.text(mc.font,count,x+w-8-countWidth,row,0xffa9a7a1,false);
                if (!line.modifier.isBlank())
                    g.text(mc.font,mc.font.plainSubstrByWidth("› "+line.modifier,w-20),x+12,row+11,0xffbfb3a4,false);
                if (!count.isEmpty()) {
                    int barY=row+(line.modifier.isBlank()?12:24);
                    g.fill(x+9,barY,x+w-9,barY+2,0xff454341);
                    int width=w-18,progress=(int)(width*Math.clamp((double)line.current/Math.max(1,line.target),0,1));
                    if (progress>0) g.fill(x+9,barY,x+9+progress,barY+2,accent);
                }
                row+=line.height();
            }
            if (panel.quest.objectives().size()>2)
                g.text(mc.font,QuestPlusClient.tr("hud.more",panel.quest.objectives().size()-2).getString(),x+9,row-2,0xff979591,false);
            y+=panel.height+5;
        }
    }
}
