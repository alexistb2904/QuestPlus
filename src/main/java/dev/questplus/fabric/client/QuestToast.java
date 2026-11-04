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

        int w=Math.min(248,g.guiWidth()-20);
        if (w<80) return;
        int x=(g.guiWidth()-w)/2;
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
        // One compact toast: icon, event, quest title. No duplicated paragraphs.
        g.fill(x+1,y+2,x+w+2,y+45,0x55000000);
        g.fill(x,y,x+w,y+43,0xed191b1a);
        g.fill(x,y,x+3,y+43,accent);
        g.fill(x+7,y+7,x+32,y+34,0xff302820);
        g.outline(x+7,y+7,25,27,0xff61513e);
        var id=Identifier.tryParse(active.icon);
        ItemStack icon=id!=null&&BuiltInRegistries.ITEM.containsKey(id)
            ?new ItemStack(BuiltInRegistries.ITEM.getValue(id)):new ItemStack(Items.BOOK);
        g.item(icon,x+11,y+12);
        String heading=QuestPlusClient.tr("toast."+active.kind).getString();
        String name=mc.font.plainSubstrByWidth(active.name,w-50);
        g.text(mc.font,heading,x+40,y+9,accent,false);
        g.text(mc.font,name,x+40,y+23,0xffe6e0d5,false);
        g.fill(x+7,y+40,x+w-7,y+41,0xff4c4842);
        int bar=(int)Math.round((w-14)*Math.clamp((double)(DURATION-elapsed)/DURATION,0,1));
        g.fill(x+7,y+40,x+7+bar,y+41,accent);
    }
}
