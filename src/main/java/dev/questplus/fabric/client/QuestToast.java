package dev.questplus.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded, animated quest achievements, separate from the pinned HUD. */
final class QuestToast {
    private record Notice(String kind,String name,String icon) {}
    private static final Deque<Notice> QUEUE=new ArrayDeque<>();
    private static Notice active;
    private static long started;
    private static final long DURATION=4600L;
    private QuestToast() {}

    static void show(String kind,String name,String icon) {
        if (QUEUE.size()==3) QUEUE.removeFirst();
        QUEUE.addLast(new Notice(kind,name,icon));
    }
    static void clear() {
        QUEUE.clear(); active=null;
    }
    static void draw(GuiGraphicsExtractor g) {
        var mc=Minecraft.getInstance();
        if (mc.player==null || !QuestPlusClient.settings.notifications) return;
        long now=System.currentTimeMillis();
        if (active!=null && now-started>=DURATION) active=null;
        if (active==null) {
            active=QUEUE.pollFirst();
            started=now;
        }
        if (active==null) return;

        float scale=(float)Math.clamp(Math.min(g.guiWidth()/620.0,g.guiHeight()/380.0),0.76,1.0);
        int virtualWidth=(int)(g.guiWidth()/scale);
        int w=Math.min(206,virtualWidth-20);
        if (w<80) return;
        int x=(virtualWidth-w)/2;
        long elapsed=now-started;
        double progress=1;
        if (!QuestPlusClient.settings.reducedMotion && QuestPlusClient.settings.animations) {
            progress=Math.min(1.0,elapsed/240.0);
            if (DURATION-elapsed<260) progress=Math.min(progress,Math.max(0,(DURATION-elapsed)/260.0));
            progress=progress*progress*(3-2*progress);
        }
        int y=8-(int)Math.round((1-progress)*55);
        boolean finish=active.kind.equals("completed");
        int accent=finish?0xffd9ae73:0xffa9c08b;
        g.pose().pushMatrix(); g.pose().scale(scale);
        // One compact toast: icon, event, quest title; no colored left-side banner.
        g.fill(x+1,y+2,x+w+1,y+40,0x44000000);
        g.fill(x,y,x+w,y+38,0xe5191b1a);
        g.fill(x+6,y+5,x+30,y+31,0xff302820);
        g.outline(x+6,y+5,24,26,0xff61513e);
        var id=Identifier.tryParse(active.icon);
        ItemStack icon=id!=null&&BuiltInRegistries.ITEM.containsKey(id)
            ?new ItemStack(BuiltInRegistries.ITEM.getValue(id)):new ItemStack(Items.BOOK);
        g.item(icon,x+10,y+10);
        String heading=QuestPlusClient.tr("toast."+active.kind).getString();
        String name=mc.font.plainSubstrByWidth(active.name,w-43);
        g.text(mc.font,heading,x+36,y+7,accent,false);
        g.text(mc.font,name,x+36,y+20,0xffe6e0d5,false);
        g.fill(x+7,y+35,x+w-7,y+36,0xff4c4842);
        int bar=(int)Math.round((w-14)*Math.clamp((double)(DURATION-elapsed)/DURATION,0,1));
        g.fill(x+7,y+35,x+7+bar,y+36,accent);
        g.pose().popMatrix();
    }
}
