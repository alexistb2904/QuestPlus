package dev.questplus.fabric.client;

import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

final class QuestBlockPicker extends Screen {
    record Choice(String id,String title){}
    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<String> selected;
    private QuestTextField search;
    private int page;
    QuestBlockPicker(Screen parent,List<Choice> choices,Consumer<String> selected){
        super(QuestBlockScreen.tr("pick_quest"));this.parent=parent;this.choices=choices;this.selected=selected;
    }
    @Override protected void init(){
        int x=(width-330)/2,y=Math.max(8,(height-250)/2);
        search=new QuestTextField(font,x,y,330,21,title);search.setMaxLength(128);
        search.setResponder(value->{page=0;rows();});rows();
    }
    private void rows(){
        clearWidgets();addRenderableWidget(search);
        int x=(width-330)/2,y=Math.max(8,(height-250)/2),n=Math.max(1,Math.min(8,(height-90)/23));
        String text=search.getValue().toLowerCase(Locale.ROOT);
        var filtered=choices.stream().filter(c->c.title.toLowerCase(Locale.ROOT).contains(text)||c.id.contains(text)).toList();
        int pages=Math.max(1,(filtered.size()+n-1)/n);page=Math.min(page,pages-1);
        for(int i=page*n;i<Math.min(filtered.size(),(page+1)*n);i++){
            var choice=filtered.get(i);
            addRenderableWidget(new QuestButton(Component.literal(choice.title),x,y+28+(i-page*n)*23,330,21,()->{
                selected.accept(choice.id);minecraft.gui.setScreen(parent);
            }));
        }
        int bottom=Math.min(height-27,y+28+n*23);
        addRenderableWidget(new QuestButton(Component.literal("‹"),x,bottom,50,20,()->{page=Math.max(0,page-1);rows();}));
        addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"),x+57,bottom,216,20,this::onClose));
        addRenderableWidget(new QuestButton(Component.literal("›"),x+280,bottom,50,20,()->{page=Math.min(pages-1,page+1);rows();}));
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xf0201916);super.extractRenderState(g,mx,my,delta);}
    @Override public boolean isPauseScreen(){return false;}
}
