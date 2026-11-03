package dev.questplus.fabric.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Pixel frame; EditBox supplies cursor, selection, IME and accessible text input. */
public final class QuestTextField extends EditBox {
    public QuestTextField(Font font,int x,int y,int width,int height,Component label) {
        super(font,x+4,y+1,Math.max(1,width-8),Math.max(1,height-2),label);
        setBordered(false);
        setTextColor(QuestSkin.INK);
        setTextColorUneditable(QuestSkin.DIM);
        setTextShadow(false);
    }
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        QuestSkin.panel(g,getX()-4,getY()-1,getWidth()+8,getHeight()+2,0xff211a17,isFocused() ? QuestSkin.HIGHLIGHT : QuestSkin.EDGE);
        super.extractWidgetRenderState(g,mouseX,mouseY,delta);
    }
}
