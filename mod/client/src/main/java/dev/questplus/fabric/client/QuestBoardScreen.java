package dev.questplus.fabric.client;

import dev.questplus.platform.ClientPlatform;
import dev.questplus.compat.ClientAdapter;
import com.google.gson.*;
import dev.questplus.fabric.*;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/** Board-specific settings, with a local draft until Apply is acknowledged by the server. */
public final class QuestBoardScreen extends Screen {
    private final net.minecraft.client.gui.Font font=QuestSkin.font();
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state,config;
    private final List<String> pins=new ArrayList<>();
    private int left,top,panelWidth,panelHeight,sideWidth,listX,listY,listWidth,listHeight,scroll,page;
    private boolean editable,ranking,dirty,ownerSettings;
    /** The last Apply was refused. Kept until the next edit (periodic snapshots carry no error). */
    private boolean rejected;
    private String owner;
    /** The last pinned/automatic/mixed choice, restored when leaving the leaderboard tab. */
    private String questMode="mixed";
    private QuestButton ownerButton;
    public QuestBoardScreen(QuestBlockNetwork.Snapshot packet){super(t("title"));receive(packet);}
    static Component t(String key,Object...args){return Component.translatable("questplus.board."+key,args);}
    public void receive(QuestBlockNetwork.Snapshot packet){
        var next=JsonParser.parseString(packet.data()).getAsJsonObject();
        boolean acknowledged=dirty&&config.equals(next.getAsJsonObject("config"))&&owner.equals(next.get("owner").getAsString());
        snapshot=packet;state=next;editable=state.get("editable").getAsBoolean();
        if(acknowledged)rejected=false;
        if(!next.get("error").getAsString().isEmpty())rejected=true;
        if(config==null||acknowledged||!dirty) {
            config=state.getAsJsonObject("config").deepCopy();owner=state.get("owner").getAsString();
            pins.clear();for(var id:config.getAsJsonArray("quests"))pins.add(id.getAsString());
            ranking=config.get("boardMode").getAsString().equals("leaderboard");dirty=false;
            if(!ranking)questMode=config.get("boardMode").getAsString();
            if(width>0)rebuildWidgets();
        }
    }
    @Override protected void init(){
        clearWidgets();panelWidth=Math.min(620,width-16);boolean narrow=panelWidth<470;
        panelHeight=ownerSettings?189:narrow?551:Math.max(361,Math.min(387,height-16));
        left=(width-panelWidth)/2;top=Math.max(8,(height-panelHeight)/2)-scroll;
        sideWidth=narrow?panelWidth-24:Math.max(176,panelWidth/3);
        ownerButton=addRenderableWidget(QuestBlockChrome.owner(t("owner",ownerName()),left,top,panelWidth,
                ()->{ownerSettings=true;scroll=0;rebuildWidgets();}));
        ownerButton.active=editable;
        int footer=footerY(top,panelHeight);
        if(ownerSettings){
            var field=addRenderableWidget(new QuestTextField(font,left+12,top+81,panelWidth-24,22,t("owner_uuid")));
            field.setMaxLength(36);field.setValue(owner);field.setEditable(editable);
            field.setResponder(value->{owner=value.trim();dirty=true;});
            button(t("choose_player"),left+12,top+127,panelWidth-24,22,this::chooseOwner);
            addRenderableWidget(new QuestButton(t("configuration"),left+PAD,footer,Math.min(140,panelWidth-2*PAD-APPLY-CLOSE-3*GAP),ROW,()->{ownerSettings=false;scroll=0;rebuildWidgets();}));
            button(QuestBlockScreen.tr("save"),applyX(left,panelWidth),footer,APPLY,ROW,this::save).selected(true);
            addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"),closeX(left,panelWidth),footer,CLOSE,ROW,this::onClose));
            return;
        }
        int x=left+12,y=top+63;
        int tabsX=left+12,tabWidth=(panelWidth-32)/2;
        button(t("configuration"),tabsX,top+36,tabWidth,21,()->{ranking=false;set("boardMode",questMode);})
                .icon(Items.COMPARATOR.getDefaultInstance()).selected(!ranking);
        button(t("leaderboard"),tabsX+tabWidth+8,top+36,tabWidth,21,()->{ranking=true;set("boardMode","leaderboard");})
                .icon(Items.GOLD_INGOT.getDefaultInstance()).selected(ranking);
        button(t("personal"),x+8,y+29,(sideWidth-24)/2,22,()->set("scope","PERSONAL")).selected(config.get("scope").getAsString().equals("PERSONAL"));
        button(t("team"),x+16+(sideWidth-24)/2,y+29,(sideWidth-24)/2,22,()->set("scope","TEAM")).selected(config.get("scope").getAsString().equals("TEAM"));
        y+=61;
        var modes=ranking?List.of("players","teams","contributions"):List.of("pinned","automatic","mixed");
        String key=ranking?"leaderboard":"boardMode";
        for(int i=0;i<modes.size();i++){
            String mode=modes.get(i);
            button(t("mode."+mode),x+8,y+27+i*27,sideWidth-16,23,()->{if(!ranking)questMode=mode;set(key,mode);}).selected(config.get(key).getAsString().equals(mode));
        }
        y+=114;
        button(categoryName(),x+8,y+29,sideWidth-16,23,()->{
            var choices=new ArrayList<QuestBlockPicker.Choice>();choices.add(new QuestBlockPicker.Choice("",t("all_categories").getString()));
            choices.addAll(QuestBlockPicker.categoryChoices());
            ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,choices,id->set("category",id)));
        });
        button(t("completed",config.get("all").getAsBoolean()?"✓":"□"),x+8,y+57,sideWidth-16,20,()->{
            config.addProperty("all",!config.get("all").getAsBoolean());changed();
        }).selected(config.get("all").getAsBoolean());
        listX=narrow?x:left+sideWidth+22;listY=narrow?top+365:top+63;
        listWidth=narrow?sideWidth:panelWidth-sideWidth-34;listHeight=narrow?146:panelHeight-103;
        int capacity=capacity();int total=entries().size();
        int pages=Math.max(1,(total+capacity-1)/capacity);page=Math.min(page,pages-1);
        for(int i=page*capacity;i<Math.min(pinCount(),(page+1)*capacity);i++) {
            final int index=i;int rowY=listY+42+(i-page*capacity)*43;
            button(Component.literal("↑"),listX+listWidth-76,rowY+10,20,22,()->move(index,-1)).active=editable&&i>0;
            button(Component.literal("↓"),listX+listWidth-53,rowY+10,20,22,()->move(index,1)).active=editable&&i+1<pins.size();
            button(Component.literal("×"),listX+listWidth-30,rowY+10,20,22,()->{pins.remove(index);changed();}).hoverText(t("remove"));
        }
        if(!ranking&&!questMode().equals("automatic")){
            button(t("add"),listX+8,listY+listHeight-30,Math.min(160,listWidth-94),22,this::pickQuest).active=editable&&pins.size()<32;
            button(t("clear"),listX+listWidth-80,listY+listHeight-30,72,22,()->{pins.clear();changed();});
        }
        if(pages>1) {
            addRenderableWidget(new QuestButton(Component.literal("‹"),listX+listWidth-62,listY+8,24,20,()->{page=Math.max(0,page-1);rebuildWidgets();}));
            addRenderableWidget(new QuestButton(Component.literal("›"),listX+listWidth-32,listY+8,24,20,()->{page=Math.min(pages-1,page+1);rebuildWidgets();}));
        }
        button(QuestBlockScreen.tr("save"),applyX(left,panelWidth),footer,APPLY,ROW,this::save).selected(true);
        addRenderableWidget(new QuestButton(QuestBlockScreen.tr("close"),closeX(left,panelWidth),footer,CLOSE,ROW,this::onClose));
    }
    private String ownerName(){
        for(var value:state.getAsJsonArray("players")){var p=value.getAsJsonObject();if(p.get("id").getAsString().equals(owner))return p.get("name").getAsString();}
        return owner.equals(state.get("owner").getAsString())&&state.has("ownerName")?state.get("ownerName").getAsString():owner;
    }
    private void chooseOwner(){
        var choices=state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                .map(p->new QuestBlockPicker.Choice(p.get("id").getAsString(),p.get("name").getAsString())).toList();
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,t("choose_player"),choices,id->{owner=id;changed();}));
    }
    private String questMode(){return config.get("boardMode").getAsString();}
    /** Pins are ignored by the server in automatic mode and by the leaderboard. */
    private int pinCount(){return ranking||questMode().equals("automatic")?0:pins.size();}
    /** What the board shows: editable pins first, then the server's live in-progress rows (or the ranking). */
    private List<JsonObject> entries(){
        var result=new ArrayList<JsonObject>();
        if(ranking){state.getAsJsonArray("leaders").forEach(e->result.add(e.getAsJsonObject()));return result;}
        for(String id:pins.subList(0,pinCount())){
            var row=state.getAsJsonObject("pins").has(id)?state.getAsJsonObject("pins").getAsJsonObject(id):new JsonObject();
            if(!row.has("title")){
                var q=QuestPlusClient.STATE.quests.get(id);row.addProperty("title",q==null?id:QuestPlusClient.STATE.text(q.title()));
                row.addProperty("icon",q==null?"minecraft:paper":q.icon());
            }
            result.add(row);
        }
        // Live rows follow the saved config. Ranking rows carry no quest id and are skipped.
        if(!questMode().equals("pinned"))for(var e:state.getAsJsonArray("rows")){
            var row=e.getAsJsonObject();String id=row.has("id")?row.get("id").getAsString():"";
            if(!id.isEmpty()&&!pins.subList(0,pinCount()).contains(id))result.add(row);
        }
        return result;
    }
    private int capacity(){return Math.max(1,(listHeight-80)/43);}
    private QuestButton button(Component label,int x,int y,int w,int h,Runnable run){
        var b=addRenderableWidget(new QuestButton(label,x,y,w,h,run));b.active=editable;return b;
    }
    private void set(String key,String value){config.addProperty(key,value);changed();}
    private void changed(){
        var ids=new JsonArray();pins.forEach(ids::add);config.add("quests",ids);dirty=true;rejected=false;rebuildWidgets();
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
        ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,choices,id->{if(pins.size()<32&&!pins.contains(id))pins.add(id);changed();}));
    }
    private void save(){ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),"save",config.toString(),owner,state.get("size").getAsInt()));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        String subtitle=t(config.get("scope").getAsString().equals("TEAM")?"team":"personal").getString();
        if(state.has("boardWidth")&&state.get("modular").getAsBoolean())subtitle+=" · "+state.get("boardWidth").getAsInt()+" × "+state.get("boardHeight").getAsInt();
        window(g,font,width,height,left,top,panelWidth,panelHeight,Items.WRITABLE_BOOK.getDefaultInstance(),title,subtitle,ownerButton.getWidth()+8);
        if(!ownerSettings)g.fill(left+12,top+60,left+panelWidth-12,top+61,QuestSkin.LINE);
        if(ownerSettings){
            text(g,t("owner_uuid").getString(),left+12,top+65,panelWidth-24,QuestSkin.INK);
            text(g,t("owner_hint").getString(),left+12,top+110,panelWidth-24,QuestSkin.DIM);
            footer(g,font,left,top,panelWidth,panelHeight,Component.empty(),QuestSkin.DIM,panelWidth);
            super.extractRenderState(g,mx,my,delta);return;
        }
        int x=left+12,y=top+63;
        box(g,x,y,sideWidth,57,"scope","scope_hint");
        box(g,x,y+61,sideWidth,110,ranking?"leaderboard":"display",ranking?"ranking_hint":"display_hint");
        box(g,x,y+175,sideWidth,82,"categories","categories_hint");
        QuestSkin.panel(g,listX,listY,listWidth,listHeight,QuestSkin.PANEL,QuestSkin.LINE);
        boolean automatic=questMode().equals("automatic");
        text(g,t(ranking?"leaderboard":automatic?"active":"pinned").getString(),listX+10,listY+9,listWidth-84,QuestSkin.INK);
        text(g,ranking?t("ranking_hint").getString():automatic?t("live",state.getAsJsonArray("rows").size()).getString()
                :t("priority",pins.size()).getString(),listX+10,listY+24,listWidth-20,QuestSkin.DIM);
        var entries=entries();
        int total=entries.size(),capacity=capacity(),pinned=pinCount();
        for(int i=page*capacity;i<Math.min(total,(page+1)*capacity);i++)
            drawRow(g,entries.get(i),listX+8,listY+42+(i-page*capacity)*43,listWidth-16,!ranking,i<pinned);
        if(total==0&&!automatic)text(g,t(ranking?"no_ranking":"empty").getString(),listX+12,listY+57,listWidth-24,QuestSkin.DIM);
        // A blank board is usually blocked (e.g. team progress unavailable): say so instead of "live".
        String blockStatus=state.get("status").getAsString();
        boolean alert=dirty||rejected||blockStatus.equals("blocked")||blockStatus.equals("storage_error");
        String status=rejected?QuestBlockScreen.tr("configuration_rejected").getString():dirty?t("unsaved").getString()
                :alert?QuestBlockScreen.tr("status."+blockStatus).getString():t("live",state.getAsJsonArray("rows").size()).getString();
        footer(g,font,left,top,panelWidth,panelHeight,Component.literal(status),alert?QuestSkin.ALERT:QuestSkin.DIM,APPLY+GAP+CLOSE);
        super.extractRenderState(g,mx,my,delta);
    }
    private void box(GuiGraphicsExtractor g,int x,int y,int w,int h,String heading,String hint){
        QuestSkin.panel(g,x,y,w,h,QuestSkin.PANEL,QuestSkin.LINE);
        text(g,t(heading).getString(),x+8,y+7,w-16,QuestSkin.INK);
        text(g,t(hint).getString(),x+8,y+18,w-16,QuestSkin.DIM);
    }
    private void text(GuiGraphicsExtractor g,String s,int x,int y,int w,int color){g.text(font,font.plainSubstrByWidth(s,Math.max(0,w)),x,y,color,false);}
    /** {@code quest} rows get a progress bar. {@code controls} rows reserve room for the pin buttons. */
    private void drawRow(GuiGraphicsExtractor g,JsonObject row,int x,int y,int w,boolean quest,boolean controls){
        QuestSkin.panel(g,x,y,w,39,QuestSkin.FIELD,QuestSkin.LINE);
        ItemStack icon=Items.PAPER.getDefaultInstance();
        try{icon=BuiltInRegistries.ITEM.getValue(Identifier.parse(row.get("icon").getAsString())).getDefaultInstance();}catch(RuntimeException ignored){}
        g.item(icon,x+6,y+7);
        int available=w-(controls?104:quest?12:32);
        text(g,row.get("title").getAsString(),x+28,y+5,available,QuestSkin.INK);
        String detail=row.has("detail")?row.get("detail").getAsString():"";
        text(g,detail,x+28,y+17,available,QuestSkin.DIM);
        if(row.has("fraction")&&quest){
            double f=row.get("fraction").getAsDouble();int bar=Math.min(64,Math.max(16,available/2));
            for(int n=0;n<8;n++)g.fill(x+28+n*bar/8,y+29,x+28+(n+1)*bar/8-1,y+34,n<Math.round(f*8)?(f>=1?QuestSkin.DONE:QuestSkin.ACTIVE):QuestSkin.LINE);
            if(row.has("count"))text(g,row.get("count").getAsString(),x+32+bar,y+27,available-bar-4,QuestSkin.INK);
        }else if(row.has("progress"))text(g,row.get("progress").getAsString(),x+w-40,y+20,34,QuestSkin.INK);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        if(x>=listX&&x<listX+listWidth&&y>=listY&&y<listY+listHeight){
            int total=entries().size();
            page=Math.max(0,Math.min(Math.max(0,(total-1)/capacity()),page+(v<0?1:-1)));rebuildWidgets();return true;
        }
        int next=Math.max(0,Math.min(Math.max(0,panelHeight+16-height),scroll-(int)(v*24)));
        if(next==scroll)return super.mouseScrolled(x,y,h,v);scroll=next;rebuildWidgets();return true;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),"close","","",0));super.onClose();}
}
