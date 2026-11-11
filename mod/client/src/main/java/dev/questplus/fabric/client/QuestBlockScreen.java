package dev.questplus.fabric.client;

import dev.questplus.platform.ClientPlatform;
import dev.questplus.compat.ClientAdapter;
import com.google.gson.*;
import dev.questplus.fabric.*;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static dev.questplus.fabric.client.QuestBlockChrome.*;

/** Uses the tablet's native controls. The server owns every applied value. */
public final class QuestBlockScreen extends Screen {
    private final net.minecraft.client.gui.Font font=QuestSkin.font();
    private QuestBlockNetwork.Snapshot snapshot;
    private JsonObject state,config;
    /** Fields, then option rows, then the resource line, under the shared header. */
    private static final int WIDTH=320,HEIGHT=294,FIELDS=BAR+8,OPTIONS=BAR+104;
    private QuestTextField quests,objective,category,threshold;
    private QuestButton owner;
    private int size,left,top,scroll;
    private boolean editable;
    private String draftOwner;
    private final List<Component> labels=new ArrayList<>();
    public QuestBlockScreen(QuestBlockNetwork.Snapshot packet){super(tr("title"));receive(packet);}
    static Component tr(String key,Object...args){return Component.translatable("questplus.blocks."+key,args);}
    public void receive(QuestBlockNetwork.Snapshot packet){
        var next=JsonParser.parseString(packet.data()).getAsJsonObject();
        if(state!=null && state.getAsJsonObject("config").equals(next.getAsJsonObject("config"))
                && state.get("owner").equals(next.get("owner")) && state.get("size").equals(next.get("size"))
                && state.get("editable").equals(next.get("editable"))) {
            snapshot=packet;state=next;return;
        }
        snapshot=packet;state=next;
        config=state.getAsJsonObject("config").deepCopy();size=state.get("size").getAsInt();editable=state.get("editable").getAsBoolean();draftOwner=state.get("owner").getAsString();
        if(width>0 && height>0)rebuildWidgets();
    }
    @Override protected void init(){
        clearWidgets();
        left=Math.max(0,(width-WIDTH)/2);top=Math.max(8,(height-HEIGHT)/2)-scroll;labels.clear();
        quests=field("quests",config.getAsJsonArray("quests").asList().stream().map(JsonElement::getAsString).collect(java.util.stream.Collectors.joining(",")),0,8192);
        objective=field("objective",config.get("objective").getAsString(),1,128);
        category=field("category",config.get("category").getAsString(),2,128);
        threshold=field("threshold",config.get("threshold").getAsString(),3,7);
        String ownerName=state.has("ownerName")?state.get("ownerName").getAsString():tr("relay.unknown_player").getString();
        for(var value:state.getAsJsonArray("players")) {
            var player=value.getAsJsonObject();
            if(player.get("id").getAsString().equals(draftOwner))ownerName=player.get("name").getAsString();
        }
        owner=addRenderableWidget(QuestBlockChrome.owner(tr("owner_name",ownerName),left,top,WIDTH,()->{
            collect();
            var choices=state.getAsJsonArray("players").asList().stream().map(JsonElement::getAsJsonObject)
                    .map(p->new QuestBlockPicker.Choice(p.get("id").getAsString(),p.get("name").getAsString())).toList();
            ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,tr("owner"),choices,chosen->{draftOwner=chosen;}));
        }));owner.active=editable;
        cycle("scope",List.of("PERSONAL","TEAM"),0,0);
        int kind=state.get("kind").getAsInt();
        button(tr("pick_quest"),left+168,top+OPTIONS,140,()->{
            collect();
            var choices=QuestBlockPicker.questChoices();
            ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,choices,id->{
                if(kind==1)quests.setValue(id);
                else {
                    var ids=new LinkedHashSet<>(Arrays.asList(quests.getValue().split(",")));ids.remove("");
                    if(!ids.remove(id))ids.add(id);quests.setValue(String.join(",",ids));
                }
                collect();
            }));
        });
        if(kind==0){
            cycle("boardMode",List.of("pinned","automatic","mixed","leaderboard"),1,0);
            cycle("leaderboard",List.of("players","teams","contributions"),2,0);
            button(tr("size",sizeName()),left+168,top+OPTIONS+24,140,()->{size=(size+1)%4;rebuildWidgetsPreserving();});
        }else if(kind==1){
            toggle("automation",1,0);toggle("advanceTiers",2,0);
            int faces=config.get("faces").getAsInt();
            for(int i=0;i<6;i++){final int side=i;
                var b=button(tr("face."+net.minecraft.core.Direction.values()[i].getName()),left+12+i*50,top+OPTIONS+72,46,()->{
                    config.addProperty("faces",config.get("faces").getAsInt()^(1<<side));rebuildWidgetsPreserving();
                });b.selected((faces&(1<<i))!=0);
            }
            button(tr("pick_objective"),left+168,top+OPTIONS+24,140,()->{
                collect();String id=config.getAsJsonArray("quests").isEmpty()?"":config.getAsJsonArray("quests").get(0).getAsString();
                var q=QuestPlusClient.STATE.quests.get(id);if(q==null)return;
                var choices=q.objectives().stream().filter(o->Set.of("item_turn_in","fluid_submit","energy_submit").contains(o.event()))
                        .map(o->new QuestBlockPicker.Choice(o.id(),o.text().isEmpty()?o.id():QuestPlusClient.STATE.text(o.text()))).toList();
                ClientAdapter.INSTANCE.setScreen(minecraft, new QuestBlockPicker(this,choices,chosen->{objective.setValue(chosen);collect();}));
            });
            button(tr("return_items"),left+168,top+OPTIONS+48,140,()->send("return_items"));
        }else{
            cycle("condition",List.of("unlocked","started","completed","claimed","tier","stage"),1,0);
            cycle("output",List.of("binary","pulse","analog"),2,0);toggle("all",1,1);
        }
        int footer=footerY(top,HEIGHT);
        addRenderableWidget(new QuestButton(tr("save"),applyX(left,WIDTH),footer,APPLY,ROW,()->send("save"))).selected(true).active=editable;
        addRenderableWidget(new QuestButton(tr("close"),closeX(left,WIDTH),footer,CLOSE,ROW,this::onClose));
    }
    private String sizeName(){return List.of("2×3","2×4","3×4","4×6").get(size);}
    private QuestTextField field(String key,String value,int row,int maximum){
        var f=addRenderableWidget(new QuestTextField(font,left+104,top+FIELDS+row*24,204,ROW,tr(key)));
        f.setMaxLength(maximum);f.setValue(value);f.setEditable(editable);labels.add(tr(key));return f;
    }
    private QuestButton button(Component label,int x,int y,int w,Runnable action){
        var b=addRenderableWidget(new QuestButton(label,x,y,w,20,action));b.active=editable;return b;
    }
    private void cycle(String key,List<String> values,int row,int column){
        button(tr(key+"."+config.get(key).getAsString()),left+12+column*156,top+OPTIONS+row*24,140,()->{
            config.addProperty(key,values.get((values.indexOf(config.get(key).getAsString())+1)%values.size()));rebuildWidgetsPreserving();
        });
    }
    private void toggle(String key,int row,int column){
        button(tr(key,config.get(key).getAsBoolean()?tr("yes"):tr("no")),left+12+column*156,top+OPTIONS+row*24,140,()->{
            config.addProperty(key,!config.get(key).getAsBoolean());rebuildWidgetsPreserving();
        }).selected(config.get(key).getAsBoolean());
    }
    private void collect(){
        var ids=new JsonArray();for(String q:quests.getValue().split(","))if(!q.isBlank())ids.add(q.trim());config.add("quests",ids);
        config.addProperty("objective",objective.getValue().trim());config.addProperty("category",category.getValue().trim());
        try{config.addProperty("threshold",Integer.parseInt(threshold.getValue()));}catch(NumberFormatException e){config.addProperty("threshold",0);}
    }
    private void rebuildWidgetsPreserving(){collect();rebuildWidgets();}
    private void send(String action){
        collect();ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),action,config.toString(),draftOwner,size));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int kind=state.get("kind").getAsInt();
        String identity=state.has("ownerName")?state.get("ownerName").getAsString():"";
        if(state.has("teamName")&&!state.get("teamName").getAsString().isEmpty())identity+=" · "+state.get("teamName").getAsString();
        window(g,font,width,height,left,top,WIDTH,HEIGHT,new net.minecraft.world.item.ItemStack(kind==0?QuestWorldBlocks.BOARD:kind==1?QuestWorldBlocks.STATION:QuestWorldBlocks.RELAY),
                tr("kind."+kind),identity,owner.getWidth()+8);
        for(int i=0;i<labels.size();i++)QuestSkin.text(g,font,QuestSkin.fit(font,labels.get(i).getString(),88),left+12,top+FIELDS+6+i*24,QuestSkin.DIM,false);
        var status=state.get("error").getAsString().isEmpty()?tr("status."+state.get("status").getAsString()):tr("configuration_rejected");
        String statusText=status.getString()+(state.has("progress")?" · "+state.get("progress").getAsInt()+"%":"");
        QuestSkin.text(g,font,QuestSkin.fit(font,tr("resources",state.get("items").getAsInt(),state.get("fluid").getAsLong(),state.get("energy").getAsLong(),state.get("signal").getAsInt()).getString(),WIDTH-2*PAD),
                left+12,top+OPTIONS+98,QuestSkin.DIM,false);
        footer(g,font,left,top,WIDTH,HEIGHT,Component.literal(statusText),state.get("error").getAsString().isEmpty()?QuestSkin.DIM:QuestSkin.ALERT,APPLY+GAP+CLOSE);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        int maximum=Math.max(0,HEIGHT+16-height),next=Math.max(0,Math.min(maximum,scroll-(int)(vertical*20)));
        if(next==scroll)return super.mouseScrolled(x,y,horizontal,vertical);
        scroll=next;rebuildWidgetsPreserving();return true;
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        ClientPlatform.send(new QuestBlockNetwork.Request(snapshot.pos(),snapshot.token(),"close","","",0));
        super.onClose();
    }
}
