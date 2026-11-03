package dev.questplus.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.questplus.core.QuestDefinition;
import java.util.*;

final class QuestHud {
    static void draw(GuiGraphicsExtractor g) {
        var mc=Minecraft.getInstance(); var settings=QuestPlusClient.settings; var state=QuestPlusClient.STATE;
        if (mc.player==null || mc.gui.screen() instanceof QuestScreen) return;
        if (settings.hud && state.ready) {
            List<String> lines=new ArrayList<>();
            for (String id:settings.pinned) {
                QuestDefinition quest=state.quests.get(id); if (quest==null) continue;
                lines.add(state.text(quest.title()));
                var counters=state.progress(id).getAsJsonObject("counters");
                for (var objective:quest.objectives()) {
                    long value=counters!=null && counters.has(objective.id()) ? counters.get(objective.id()).getAsLong() : 0;
                    lines.add("  "+state.text(objective.text())+" "+value+"/"+dev.questplus.clientdata.QuestPresentation.target(quest,objective,state.progress(id)));
                    if (lines.size()>=10) break;
                }
                if (lines.size()>=10) break;
            }
            if (!lines.isEmpty()) {
                int w=Math.min(220,g.guiWidth()-16),h=lines.size()*11+10,pos=settings.hudPosition;
                int x=pos%3==0 ? 7 : pos%3==1 ? (g.guiWidth()-w)/2 : g.guiWidth()-w-7;
                int y=pos/3==0 ? 35 : pos/3==1 ? (g.guiHeight()-h)/2 : g.guiHeight()-h-48;
                g.fill(x,y,x+w,y+h,0x990f1210);
                for (int i=0;i<lines.size();i++) g.text(mc.font,mc.font.plainSubstrByWidth(lines.get(i),w-12),x+6,y+5+i*11,0xffe6e4cf,false);
            }
        }
        if (settings.notifications && QuestPlusClient.notice!=null && System.currentTimeMillis()<QuestPlusClient.noticeUntil) {
            String text=mc.font.plainSubstrByWidth(QuestPlusClient.notice,g.guiWidth()-40); int w=mc.font.width(text)+16;
            g.fill((g.guiWidth()-w)/2,8,(g.guiWidth()+w)/2,29,0xcc181d18);
            g.centeredText(mc.font,text,g.guiWidth()/2,14,0xffd7d6b3);
        }
    }
}
