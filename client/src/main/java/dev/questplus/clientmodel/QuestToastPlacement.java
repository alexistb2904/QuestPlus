package dev.questplus.clientmodel;

/** Pure screen-space positioning for all nine toast anchors (row-major TL..BR).
 * Values are in logical GUI pixels so Minecraft's GUI scale is accounted for once. */
public record QuestToastPlacement(int x,int y,int enterX,int enterY) {
    public static QuestToastPlacement at(int anchor,int width,int height,int toastWidth,int toastHeight) {
        if(anchor<0||anchor>8)throw new IllegalArgumentException("Invalid toast anchor: "+anchor);
        int column=anchor%3,row=anchor/3;
        int x=switch(column){
            case 0 -> 8;
            case 1 -> (width-toastWidth)/2;
            default -> width-toastWidth-8;
        };
        int y=switch(row){
            case 0 -> 8;
            case 1 -> (height-toastHeight)/2;
            default -> height-toastHeight-(column==1?52:12);
        };
        x=Math.max(0,Math.min(x,Math.max(0,width-toastWidth)));
        y=Math.max(0,Math.min(y,Math.max(0,height-toastHeight)));
        int enterX=row==1 ? column==0?-40:column==2?40:0 : 0;
        int enterY=row==0?-56:row==2?56:column==1?-22:0;
        return new QuestToastPlacement(x,y,enterX,enterY);
    }
}
