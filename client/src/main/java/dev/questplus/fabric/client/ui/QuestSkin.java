package dev.questplus.fabric.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Shared pixel-edged surfaces for the tablet interface. */
public final class QuestSkin {
    public static final int PAPER=0xff35251d, INK=0xffe3cdb1, DIM=0xffac927a;
    public static final int EDGE=0xff76513b, HIGHLIGHT=0xffd0a26d, SELECTED=0xffd4ad70;
    private QuestSkin() {}
    /** Whole texture pixels keep the compact 500x300 screen font legible. */
    public static final float TEXT_SCALE=1f;
    public static void text(GuiGraphicsExtractor g,Font font,String text,int x,int y,int color,boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x,y);
        g.pose().scale(TEXT_SCALE);
        g.text(font,text,0,0,color,shadow);
        g.pose().popMatrix();
    }
    public static void text(GuiGraphicsExtractor g,Font font,Component text,int x,int y,int color,boolean shadow) {
        g.pose().pushMatrix();
        g.pose().translate(x,y);
        g.pose().scale(TEXT_SCALE);
        g.text(font,text,0,0,color,shadow);
        g.pose().popMatrix();
    }
    public static void centered(GuiGraphicsExtractor g,Font font,Component text,int x,int y,int color) {
        g.pose().pushMatrix();
        g.pose().translate(x,y);
        g.pose().scale(TEXT_SCALE);
        g.centeredText(font,text,0,0,color);
        g.pose().popMatrix();
    }

    public static void panel(GuiGraphicsExtractor g,int x,int y,int width,int height,int fill,int border) {
        if (width<4 || height<4) return;
        int r=x+width,b=y+height;
        g.fill(x+1,y,r-1,b,fill);
        g.fill(x,y+1,x+1,b-1,fill); g.fill(r-1,y+1,r,b-1,fill);
        g.fill(x+2,y,r-2,y+1,border); g.fill(x+2,b-1,r-2,b,border);
        g.fill(x,y+2,x+1,b-2,border); g.fill(r-1,y+2,r,b-2,border);
        g.fill(x+1,y+1,x+2,y+2,border); g.fill(r-2,y+1,r-1,y+2,border);
        g.fill(x+1,b-2,x+2,b-1,border); g.fill(r-2,b-2,r-1,b-1,border);
    }
}
