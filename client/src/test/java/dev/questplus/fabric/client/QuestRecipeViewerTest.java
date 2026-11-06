package dev.questplus.fabric.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QuestRecipeViewerTest {
    @Test void recipeViewersStayOptional() {
        assertFalse(QuestRecipeViewer.available());
    }
    @Test void onlyVisibleIconPixelsAreClickable() {
        var target=new QuestRecipeViewer.Target(null,100,40);
        assertTrue(target.contains(100,45,45,60));
        assertTrue(target.contains(115,55,45,60));
        assertFalse(target.contains(99,50,45,60));
        assertFalse(target.contains(116,50,45,60));
        assertFalse(target.contains(105,44,45,60));
        assertFalse(target.contains(105,56,45,60));
        assertFalse(target.contains(105,50,20,50));
    }
}
