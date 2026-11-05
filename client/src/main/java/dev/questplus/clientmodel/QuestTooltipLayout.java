package dev.questplus.clientmodel;

/** Places hover text in the logical tablet viewport, not the host window. */
public final class QuestTooltipLayout {
    private QuestTooltipLayout(){}
    public record Point(int x,int y){}
    public static Point place(int mouseX,int mouseY,int width,int height,
                              int left,int top,int right,int bottom) {
        if(right<=left || bottom<=top)throw new IllegalArgumentException("Invalid tooltip viewport");
        int w=Math.max(0,width),h=Math.max(0,height);
        int x=mouseX+12;
        if(x+w>right)x=mouseX-w-12;
        int y=mouseY+12;
        if(y+h>bottom)y=mouseY-h-8;
        x=Math.clamp(x,left,Math.max(left,right-w));
        y=Math.clamp(y,top,Math.max(top,bottom-h));
        return new Point(x,y);
    }
}
