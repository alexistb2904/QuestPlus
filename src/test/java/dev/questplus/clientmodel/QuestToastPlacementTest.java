package dev.questplus.clientmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class QuestToastPlacementTest {
    @Test void allNineAnchorsStayInsideScreenAtSeveralGuiScales(){
        for(int[] screen:new int[][]{{1200,680},{420,260},{260,160},{190,110}}){
            int vw=screen[0],vh=screen[1],w=Math.min(188,vw-20),h=38;
            for(int anchor=0;anchor<9;anchor++){
                var place=QuestToastPlacement.at(anchor,vw,vh,w,h);
                assertTrue(place.x()>=0 && place.x()+w<=vw,"x anchor "+anchor);
                assertTrue(place.y()>=0 && place.y()+h<=vh,"y anchor "+anchor);
            }
        }
    }
    @Test void nineAnchorsFollowTheSameRowMajorOrderAsHud(){
        int width=900,height=500,w=188,h=38;
        assertEquals(8,QuestToastPlacement.at(0,width,height,w,h).x());
        assertEquals((width-w)/2,QuestToastPlacement.at(1,width,height,w,h).x());
        assertEquals(width-w-8,QuestToastPlacement.at(2,width,height,w,h).x());
        assertEquals(8,QuestToastPlacement.at(0,width,height,w,h).y());
        assertEquals((height-h)/2,QuestToastPlacement.at(4,width,height,w,h).y());
        assertEquals(height-h-12,QuestToastPlacement.at(8,width,height,w,h).y());
        assertEquals(height-h-52,QuestToastPlacement.at(7,width,height,w,h).y());
    }
    @Test void entranceDirectionFollowsAnchorAndRemainsAccessible(){
        assertTrue(QuestToastPlacement.at(1,900,500,188,38).enterY()<0);
        assertTrue(QuestToastPlacement.at(8,900,500,188,38).enterY()>0);
        assertTrue(QuestToastPlacement.at(3,900,500,188,38).enterX()<0);
        assertTrue(QuestToastPlacement.at(5,900,500,188,38).enterX()>0);
        assertThrows(IllegalArgumentException.class,()->QuestToastPlacement.at(-1,900,500,188,38));
        assertThrows(IllegalArgumentException.class,()->QuestToastPlacement.at(9,900,500,188,38));
    }
}
