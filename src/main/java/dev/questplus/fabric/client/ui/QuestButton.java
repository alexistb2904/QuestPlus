package dev.questplus.fabric.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Tablet-styled control with native keyboard focus and narration, without vanilla button sprites. */
public final class QuestButton extends AbstractWidget {
    private final Runnable action;
    private boolean selected, pressed;
    private ItemStack icon=ItemStack.EMPTY;

    public QuestButton(Component label,int x,int y,int width,int height,Runnable action) {
        super(x,y,width,height,label);
        this.action=action;
        setTooltip(Tooltip.create(label));
    }
    public QuestButton selected(boolean value) { selected=value; return this; }
    public QuestButton icon(ItemStack value) { icon=value==null ? ItemStack.EMPTY : value; return this; }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        int x=getX(),y=getY();
        boolean highlighted=isHoveredOrFocused();
        int fill=!active ? 0xff302923 : pressed ? 0xff483022 : selected ? 0xff523727 : highlighted ? 0xff4a3327 : 0xff30241e;
        int edge=!active ? 0xff55483e : selected ? QuestSkin.SELECTED : highlighted ? QuestSkin.HIGHLIGHT : QuestSkin.EDGE;
        QuestSkin.panel(g,x,y,width,height,fill,edge);
        if (selected) g.fill(x+4,y+height-3,x+width-4,y+height-2,0xffae7b4e);
        var font=Minecraft.getInstance().font;
        int iconWidth=icon.isEmpty() ? 0 : 15;
        if (!icon.isEmpty()) {
            g.pose().pushMatrix(); g.pose().translate(x+3,y+(height-12)/2f); g.pose().scale(.75f);
            g.item(icon,0,0); g.pose().popMatrix();
        }
        int available=Math.max(0,(int)((width-8-iconWidth)/QuestSkin.TEXT_SCALE));
        String full=getMessage().getString(), label=font.plainSubstrByWidth(full,available);
        if (!label.equals(full) && available>=font.width("…")) label=font.plainSubstrByWidth(full,available-font.width("…"))+"…";
        int textX=x+iconWidth+(int)((width-iconWidth-font.width(label)*QuestSkin.TEXT_SCALE)/2);
        QuestSkin.text(g,font,label,textX,y+(height-7)/2,active ? QuestSkin.INK : QuestSkin.DIM,false);
    }
    @Override public void onClick(MouseButtonEvent event,boolean doubleClick) { pressed=true; action.run(); }
    @Override public void onRelease(MouseButtonEvent event) { pressed=false; }
    @Override public boolean keyPressed(KeyEvent event) {
        if (!active || !visible || !isFocused()) return false;
        if (event.key()!=InputConstants.KEY_SPACE && event.key()!=InputConstants.KEY_RETURN && event.key()!=InputConstants.KEY_NUMPADENTER) return false;
        playDownSound(Minecraft.getInstance().getSoundManager()); action.run(); return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
