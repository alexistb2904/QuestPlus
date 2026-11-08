package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.fabric.*;
import dev.questplus.fabric.client.ui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;

/** Board-specific settings, with a local draft until Apply is acknowledged by the server. */
public final class QuestBoardScreen extends Screen {
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state,config;
    private final List<String> pins=new ArrayList<>();
    private int left,top,panelWidth,panelHeight,sideWidth,listX,listY,listWidth,listHeight,scroll,page;
    private boolean editable,ranking,dirty,ownerSettings;
    private String owner;
    public QuestBoardScreen(QuestBlockNetwork.Snapshot packet){super(t("title"));receive(packet);}
    static Component t(String key,Object...args){return Component.translatable("questplus.board."+key,args);}
    public void receive(QuestBlockNetwork.Snapshot packet){
        var next=JsonParser.parseString(packet.data()).getAsJsonObject();
        boolean acknowledged=dirty&&config.equals(next.getAsJsonObject("config"))&&owner.equals(next.get("owner").getAsString());
        snapshot=packet;state=next;editable=state.get("editable").getAsBoolean();
        if(config==null||acknowledged||!dirty) {
            config=state.getAsJsonObject("config").deepCopy();owner=state.get("owner").getAsString();
            pins.clear();for(var id:config.getAsJsonArray("quests"))pins.add(id.getAsString());
            ranking=config.get("boardMode").getAsString().equals("leaderboard");dirty=false;
            if(width>0)rebuildWidgets();
        }
    }
    @Override protected void init(){
        clearWidgets();panelWidth=Math.min(620,width-16);boolean narrow=panelWidth<470;
        panelHeight=ownerSettings?220:narrow?590:Math.max(374,Math.min(400,height-16));
        left=(width-panelWidth)/2;top=Math.max(8,(height-panelHeight)/2)-scroll;
        sideWidth=narrow?panelWidth-24:Math.max(176,panelWidth/3);
        if(ownerSettings){
            var field=addRenderableWidget(new QuestTextField(font,left+12,top+94,panelWidth-24,22,t("owner_uuid")));
            field.setMaxLength(36);field.setValue(owner);field.setEditable(editable);
            field.setResponder(value->{owner=value.trim();dirty=true;});
            button(t("choose_player"),left+12,top+140,panelWidth-24,22,this::chooseOwner);
            addRenderableWidget(new QuestButton(t("configuration"),left+12,top+184,Math.min(140,panelWidth-200),23,()->{ownerSettings=false;scroll=0;rebuildWidgets();}));
            button(QuestBlockScreen.tr("save"),left+panelWidth-180,top+184,80,23,this::save).selected(true);
            addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"),left+panelWidth-92,top+184,80,23,this::onClose));
            return;
        }
        int x=left+12,y=top+76;
        int tabsX=left+12,tabWidth=(panelWidth-32)/2;
        button(t("configuration"),tabsX,top+49,tabWidth,21,()->{ranking=false;set("boardMode","mixed");})
                .icon(Items.COMPARATOR.getDefaultInstance()).selected(!ranking);
        button(t("leaderboard"),tabsX+tabWidth+8,top+49,tabWidth,21,()->{ranking=true;set("boardMode","leaderboard");})
                .icon(Items.GOLD_INGOT.getDefaultInstance()).selected(ranking);
        button(t("personal"),x+8,y+29,(sideWidth-24)/2,22,()->set("scope","PERSONAL")).selected(config.get("scope").getAsString().equals("PERSONAL"));
        button(t("team"),x+16+(sideWidth-24)/2,y+29,(sideWidth-24)/2,22,()->set("scope","TEAM")).selected(config.get("scope").getAsString().equals("TEAM"));
        y+=61;
        var modes=ranking?List.of("players","teams","contributions"):List.of("pinned","automatic","mixed");
        String key=ranking?"leaderboard":"boardMode";
        for(int i=0;i<modes.size();i++){
            String mode=modes.get(i);
            button(t("mode."+mode),x+8,y+27+i*27,sideWidth-16,23,()->set(key,mode)).selected(config.get(key).getAsString().equals(mode));
        }
        y+=114;
        button(categoryName(),x+8,y+29,sideWidth-16,23,()->{
            var choices=new ArrayList<QuestBlockPicker.Choice>();choices.add(new QuestBlockPicker.Choice("",t("all_categories").getString()));
            choices.addAll(QuestBlockPicker.categoryChoices());
            minecraft.gui.setScreen(new QuestBlockPicker(this,choices,id->set("category",id)));
        });
        button(t("completed",config.get("all").getAsBoolean()?"✓":"□"),x+8,y+57,sideWidth-16,20,()->{
            config.addProperty("all",!config.get("all").getAsBoolean());changed();
        }).selected(config.get("all").getAsBoolean());
        listX=narrow?x:left+sideWidth+22;listY=narrow?top+378:top+76;
        listWidth=narrow?sideWidth:panelWidth-sideWidth-34;listHeight=narrow?146:panelHeight-126;
        int capacity=capacity();int total=ranking?state.getAsJsonArray("leaders").size():pins.size();
        int pages=Math.max(1,(total+capacity-1)/capacity);page=Math.min(page,pages-1);
        if(!ranking)for(int i=page*capacity;i<Math.min(pins.size(),(page+1)*capacity);i++) {
            final int index=i;int rowY=listY+42+(i-page*capacity)*43;
            button(Component.literal("↑"),listX+listWidth-76,rowY+10,20,22,()->move(index,-1)).active=editable&&i>0;
            button(Component.literal("↓"),listX+listWidth-53,rowY+10,20,22,()->move(index,1)).active=editable&&i+1<pins.size();
            button(Component.literal("×"),listX+listWidth-30,rowY+10,20,22,()->{pins.remove(index);changed();}).hoverText(t("remove"));
        }
        if(!ranking){
            button(t("add"),listX+8,listY+listHeight-30,Math.min(160,listWidth-94),22,this::pickQuest).active=editable&&pins.size()<32;
            button(t("clear"),listX+listWidth-80,listY+listHeight-30,72,22,()->{pins.clear();changed();});
        }
        if(pages>1) {
            addRenderableWidget(new QuestButton(Component.literal("‹"),listX+listWidth-62,listY+8,24,20,()->{page=Math.max(0,page-1);rebuildWidgets();}));
            addRenderableWidget(new QuestButton(Component.literal("›"),listX+listWidth-32,listY+8,24,20,()->{page=Math.min(pages-1,page+1);rebuildWidgets();}));
        }
        int footer=top+panelHeight-36;
        button(QuestBlockScreen.tr("save"),left+panelWidth-180,footer,80,23,this::save).selected(true);
        addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"),left+panelWidth-92,footer,80,23,this::onClose));
        button(t("owner",ownerName()),left+panelWidth-(narrow?112:176),top+14,narrow?100:164,25,
                ()->{ownerSettings=true;scroll=0;rebuildWidgets();}).icon(Items.PLAYER_HEAD.getDefaultInstance());
    }
    private String ownerName(){
        for(var value:state.getAsJsonArray("players")){var p=value.getAsJsonObject();if(p.get("id").getAsString().equals(owner))return p.get("name").getAsString();}
        return owner.equals(state.get("owner").getAsString())&&state.has("ownerName")?state.get("ownerName").getAsString():owner;
    }
    private void chooseOwner(){
        var choices=state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                .map(p->new QuestBlockPicker.Choice(p.get("id").getAsString(),p.get("name").getAsString())).toList();
        minecraft.gui.setScreen(new QuestBlockPicker(this,t("choose_player"),choices,id->{owner=id;changed();}));
    }
    private int capacity(){return Math.max(1,(listHeight-80)/43);}
    private QuestButton button(Component label,int x,int y,int w,int h,Runnable run){
        var b=addRenderableWidget(new QuestButton(label,x,y,w,h,run));b.active=editable;return b;
    }
    private void set(String key,String value){config.addProperty(key,value);changed();}
    private void changed(){
        var ids=new JsonArray();pins.forEach(ids::add);config.add("quests",ids);dirty=true;rebuildWidgets();
    }
    private void move(int from,int by){Collections.swap(pins,from,from+by);changed();}
    private Component categoryName(){
        String id=config.get("category").getAsString();
        if(id.isEmpty())return t("all_categories");
        var c=QuestPlusClient.STATE.categories.get(id);
        if(c==null||!c.has("title"))return Component.literal(id);
        Map<String,String> names=new HashMap<>();c.getAsJsonObject("title").entrySet().forEach(e->names.put(e.getKey(),e.getValue().getAsString()));
        return Component.literal(QuestPlusClient.STATE.text(names));
    }
    private void pickQuest(){
        var choices=QuestBlockPicker.questChoices().stream().filter(q->!pins.contains(q.id())).toList();
        minecraft.gui.setScreen(new QuestBlockPicker(this,choices,id->{if(pins.size()<32&&!pins.contains(id))pins.add(id);changed();}));
    }
    private void save(){ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),"save",config.toString(),owner,state.get("size").getAsInt()));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xb00c0805);
        QuestSkin.panel(g,left,top,panelWidth,panelHeight,0xff24170f,0xffa46d42);
        QuestSkin.panel(g,left+3,top+3,panelWidth-6,panelHeight-6,0xff281a12,0xff69412b);
        QuestSkin.panel(g,left+7,top+7,panelWidth-14,38,0xff352318,0xff593b29);
        g.pose().pushMatrix();g.pose().translate(left+14,top+12);g.pose().scale(1.6f);
        g.item(Items.WRITABLE_BOOK.getDefaultInstance(),0,0);g.pose().popMatrix();
        boolean narrow=panelWidth<470;
        int titleWidth=Math.max(24,panelWidth-(narrow?170:234));
        g.pose().pushMatrix();g.pose().translate(left+48,top+12);g.pose().scale(1.25f);
        g.text(font,font.plainSubstrByWidth(title.getString(),(int)(titleWidth/1.25f)),0,0,0xffffe2ad,true);g.pose().popMatrix();
        String subtitle=t(config.get("scope").getAsString().equals("TEAM")?"team":"personal").getString();
        if(state.has("boardWidth")&&state.get("modular").getAsBoolean())subtitle+=" · "+state.get("boardWidth").getAsInt()+" × "+state.get("boardHeight").getAsInt();
        text(g,subtitle,left+48,top+29,titleWidth,0xffd0af86);
        if(!ownerSettings)g.fill(left+12,top+73,left+panelWidth-12,top+74,0xffbd824e);
        if(ownerSettings){
            text(g,t("owner_uuid").getString(),left+12,top+78,panelWidth-24,QuestSkin.INK);
            text(g,t("owner_hint").getString(),left+12,top+123,panelWidth-24,QuestSkin.DIM);
            super.extractRenderState(g,mx,my,delta);return;
        }
        int x=left+12,y=top+76;
        box(g,x,y,sideWidth,57,"scope","scope_hint");
        box(g,x,y+61,sideWidth,110,ranking?"leaderboard":"display",ranking?"ranking_hint":"display_hint");
        box(g,x,y+175,sideWidth,82,"categories","categories_hint");
        QuestSkin.panel(g,listX,listY,listWidth,listHeight,0xff302016,0xff9a6342);
        text(g,t(ranking?"leaderboard":"pinned").getString(),listX+10,listY+9,listWidth-84,QuestSkin.INK);
        text(g,ranking?t("ranking_hint").getString():t("priority",pins.size()).getString(),listX+10,listY+24,listWidth-20,QuestSkin.DIM);
        var leaders=state.getAsJsonArray("leaders");
        int total=ranking?leaders.size():pins.size(),capacity=capacity();
        for(int i=page*capacity;i<Math.min(total,(page+1)*capacity);i++){
            JsonObject row;
            if(ranking)row=leaders.get(i).getAsJsonObject();
            else {
                String id=pins.get(i);row=state.getAsJsonObject("pins").has(id)?state.getAsJsonObject("pins").getAsJsonObject(id):new JsonObject();
                if(!row.has("title")){
                    var q=QuestPlusClient.STATE.quests.get(id);row.addProperty("title",q==null?id:QuestPlusClient.STATE.text(q.title()));
                    row.addProperty("icon",q==null?"minecraft:paper":q.icon());
                }
            }
            drawRow(g,row,listX+8,listY+42+(i-page*capacity)*43,listWidth-16,!ranking);
        }
        if(total==0)text(g,t(ranking?"no_ranking":"empty").getString(),listX+12,listY+57,listWidth-24,QuestSkin.DIM);
        int footer=top+panelHeight-36;g.fill(left+12,footer-8,left+panelWidth-12,footer-7,0xff74482f);
        String status=!state.get("error").getAsString().isEmpty()?QuestBlockScreen.tr("configuration_rejected").getString()
                :dirty?t("unsaved").getString():t("live",state.getAsJsonArray("rows").size()).getString();
        text(g,status,left+14,footer+7,panelWidth-210,dirty?0xffffd07a:0xffb9cd75);
        super.extractRenderState(g,mx,my,delta);
    }
    private void box(GuiGraphicsExtractor g,int x,int y,int w,int h,String heading,String hint){
        QuestSkin.panel(g,x,y,w,h,0xff362317,0xff9a6342);
        text(g,t(heading).getString(),x+8,y+7,w-16,QuestSkin.INK);
        text(g,t(hint).getString(),x+8,y+18,w-16,QuestSkin.DIM);
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int w,int color){g.text(font,font.plainSubstrByWidth(s,Math.max(0,w)),x,y,color,false);}
    private void drawRow(GuiGraphicsExtractor g,JsonObject row,int x,int y,int w,boolean controls){
        QuestSkin.panel(g,x,y,w,39,0xff41291b,0xff865335);
        ItemStack icon=Items.PAPER.getDefaultInstance();
        try{icon=BuiltInRegistries.ITEM.getValue(Identifier.parse(row.get("icon").getAsString())).getDefaultInstance();}catch(RuntimeException ignored){}
        g.item(icon,x+6,y+7);
        int available=w-(controls?104:32);
        text(g,row.get("title").getAsString(),x+28,y+5,available,QuestSkin.INK);
        String detail=row.has("detail")?row.get("detail").getAsString():"";
        text(g,detail,x+28,y+17,available,QuestSkin.DIM);
        if(row.has("fraction")&&controls){
            double f=row.get("fraction").getAsDouble();int bar=Math.min(64,Math.max(16,available/2));
            for(int n=0;n<8;n++)g.fill(x+28+n*bar/8,y+29,x+28+(n+1)*bar/8-1,y+34,n<Math.round(f*8)?0xff79bd36:0xff65513f);
            if(row.has("count"))text(g,row.get("count").getAsString(),x+32+bar,y+27,available-bar-4,QuestSkin.INK);
        }else if(row.has("progress"))text(g,row.get("progress").getAsString(),x+w-40,y+20,34,0xffffcf73);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(x>=listX&&x<listX+listWidth&&y>=listY&&y<listY+listHeight){
            int total=ranking?state.getAsJsonArray("leaders").size():pins.size();
            page=Math.max(0,Math.min(Math.max(0,(total-1)/capacity()),page+(v<0?1:-1)));rebuildWidgets();return true;
        }
        int next=Math.max(0,Math.min(Math.max(0,panelHeight+16-height),scroll-(int)(v*24)));
        if(next==scroll)return super.mouseScrolled(x,y,h,v);scroll=next;rebuildWidgets();return true;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientPlayNetworking.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),"close","","",0));super.onClose();}
}
