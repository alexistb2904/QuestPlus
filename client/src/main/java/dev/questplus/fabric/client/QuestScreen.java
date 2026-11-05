package dev.questplus.fabric.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import dev.questplus.clientmodel.*;
import dev.questplus.core.QuestDefinition;
import dev.questplus.clientdata.QuestPresentation;
import dev.questplus.fabric.client.ui.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.*;
import java.util.*;
import static dev.questplus.fabric.client.QuestPlusClient.*;

public final class QuestScreen extends Screen {
    private final QuestClientState state=STATE;
    private final Map<String,QuestCamera> cameras=new HashMap<>();
    private final Map<String,QuestLayout> layouts=new HashMap<>();
    private final Map<String,ItemStack> icons=new HashMap<>();
    private QuestLayout layout=QuestLayout.compute(List.of());
    private Map<QuestLayout.Edge,List<QuestLayout.Point>> routes=Map.of();
    private QuestCamera camera=new QuestCamera(QuestCamera.MIN_ZOOM,QuestCamera.MAX_ZOOM);
    private String category,selected,tab="quests",filter="all",confirmation;
    private EditBox search,input;
    private String query="";
    private int left,top,right,bottom,treeLeft,treeTop,treeRight,treeBottom,detailsScroll,categoryScroll,teamPage;
    private boolean dragging,dragged;
    private double downX,downY;
    private long catalogue=-1,sequence=-1;
    private JsonObject lastTeam;
    private JsonArray lastInvitations;
    private final long opened=System.nanoTime();
    private QuestButton questAction;
    private final List<QuestButton> hoverButtons=new ArrayList<>();
    private String hoverTitle;
    private String pendingManualQuest;
    private long pendingManualAt;
    private String currentAction;
    private boolean overlayDetails;
    private int detailLeft;
    private final Set<String> filtered=new HashSet<>();
    private final Map<String,String> titles=new HashMap<>(),statuses=new HashMap<>();
    private List<String> visibleNodes=List.of();
    private List<QuestLayout.Edge> visibleEdges=List.of();
    private long filteredSequence=-2,filteredCatalogue=-2;
    private String filteredQuery,filteredLanguage,filteredFilter;
    private double cachedLeft=Double.NaN,cachedTop,cachedRight,cachedBottom;
    private record DetailRow(List<String> lines,ItemStack icon,String progress,List<FormattedCharSequence> formatted,JsonObject rich,int richHeight) {
        DetailRow(List<String> lines,ItemStack icon,String progress) { this(lines,icon,progress,null,null,0); }
        DetailRow(List<String> lines,ItemStack icon,String progress,List<FormattedCharSequence> formatted) { this(lines,icon,progress,formatted,null,0); }
        int lineCount() { return formatted==null ? lines.size() : formatted.size(); }
    }
    private List<DetailRow> detailLines=List.of();
    private String detailQuest,detailLanguage;
    private long detailSequence=-2;
    private int detailWidth;
    private long closingAt;
    private float visualOffset;
    private boolean attachedToScreenFace;
    // Both modes use the exact same 500×300 layout. The fallback GUI
    // scales the whole surface as one unit rather than stretching each widget.
    private double screenScale=1,screenOffsetX,screenOffsetY;
    private Double pendingInterfaceScale;
    private String keyboardQuest;
    private long completedNodes;
    public double animationProgress() {
        if (QuestClientTabletFlow.nativeHandsActive()) return 1;
        if (!settings.animations || settings.reducedMotion) return closingAt==0 ? 1 : 0;
        double p=Math.clamp((System.nanoTime()-(closingAt==0 ? opened : closingAt))/250_000_000.0,0,1);
        p=p*p*(3-2*p); return closingAt==0 ? p : 1-p;
    }
    @Override public void onClose() {
        if (QuestClientTabletFlow.nativeHandsActive()) {
            QuestClientTabletFlow.close();
            super.onClose();
        } else if (!settings.animations || settings.reducedMotion) super.onClose();
        else if (closingAt==0) closingAt=System.nanoTime();
    }
    public QuestScreen() { super(Component.literal("Quest Plus")); }
    private Component t(String key,Object... args) { return tr(key,args); }
    private QuestButton button(String key,int x,int y,int w,Runnable action) {
        QuestButton result=addRenderableWidget(new QuestButton(t(key),x,y,w,15,action));
        hoverButtons.add(result);
        return result;
    }
    @Override protected void init() {
        attachedToScreenFace=TabletSurfaceRenderer.active();
        if(attachedToScreenFace) {
            width=Math.max(1,TabletSurfaceRenderer.logicalWidth());
            height=Math.max(1,TabletSurfaceRenderer.logicalHeight());
            screenScale=1;screenOffsetX=0;screenOffsetY=0;
        }else{
            // 2D fallback is a scaled presentation of the SAME logical surface
            // as the 3D tablet; this also avoids invalid widget sizes on GUI resize.
            int hostWidth=Math.max(1,minecraft.getWindow().getGuiScaledWidth());
            int hostHeight=Math.max(1,minecraft.getWindow().getGuiScaledHeight());
            width=TabletUiViewport.WIDTH;
            height=TabletUiViewport.HEIGHT;
            var viewport=TabletUiViewport.fit(hostWidth,hostHeight,settings.scale);
            screenScale=viewport.scale();
            screenOffsetX=viewport.offsetX();
            screenOffsetY=viewport.offsetY();
        }
        left=0;top=0;right=width;bottom=height;
        int fw=width,fh=height;
        overlayDetails=selected!=null && fw<480;
        treeLeft=left+38; // padding between category icons and the diagram
        treeTop=top+52;
        treeRight=selected==null || overlayDetails ? right-7 : right-Math.min(170,fw*2/5);
        treeBottom=bottom-19;
        detailLeft=overlayDetails ? treeLeft : treeRight+8;
        cachedLeft=Double.NaN; questAction=null;
        hoverButtons.clear();
        clearWidgets();
        int tabWidth=Math.min(78,(fw-35)/3);
        button("quests",left+5,top+6,tabWidth,() -> switchTab("quests")).selected(tab.equals("quests")).icon(tabWidth>=80 ? icon("minecraft:book") : ItemStack.EMPTY);
        button("team",left+7+tabWidth,top+6,tabWidth,() -> switchTab("team")).selected(tab.equals("team")).icon(tabWidth>=80 ? icon("minecraft:player_head") : ItemStack.EMPTY);
        button("settings",left+9+tabWidth*2,top+6,tabWidth,() -> switchTab("settings")).selected(tab.equals("settings")).icon(tabWidth>=80 ? icon("minecraft:comparator") : ItemStack.EMPTY);
        button("close",right-20,top+6,15,this::onClose).setMessage(Component.literal("×"));
        if (tab.equals("quests")) {
            int treeWidth=treeRight-treeLeft;
            boolean compact=treeWidth<230;
            int searchX=left+249;
            int searchY=top+6;
            int searchWidth=right-searchX-26;
            search=addRenderableWidget(new QuestTextField(font,searchX,searchY,searchWidth,14,t("search")));
            search.setMaxLength(128); search.setHint(t("search")); search.setValue(query); search.setResponder(value -> query=value);
            if (!overlayDetails) {
            var f=button("filter."+filter,compact ? treeRight-42 : treeRight-110,treeTop+2,compact ? 15 : 73,() -> { filter=List.of("all","available","active","completed").get((List.of("all","available","active","completed").indexOf(filter)+1)%4); init(); });
            if (compact) f.setMessage(Component.literal("≡"));
            button("center_tree",treeRight-19,treeTop+2,15,this::fit).setMessage(Component.literal("o"));
            button("zoom_out",treeRight-40,treeTop+20,15,() -> camera.zoomAt(1/QuestCamera.ZOOM_STEP,(treeLeft+treeRight)/2.0,(treeTop+treeBottom)/2.0)).setMessage(Component.literal("−"));
            button("zoom_in",treeRight-21,treeTop+20,15,() -> camera.zoomAt(QuestCamera.ZOOM_STEP,(treeLeft+treeRight)/2.0,(treeTop+treeBottom)/2.0)).setMessage(Component.literal("+"));
            }
            if (!overlayDetails) {
                QuestButton claimAll=button("claim_all",treeRight-19,treeBottom-19,16,() -> action("claim_all",""));
                claimAll.icon(icon("minecraft:chest"));
                claimAll.setMessage(Component.literal(""));
                claimAll.hoverText(t("claim_all"));
                claimAll.active=state.progress.values().stream().anyMatch(p -> QuestClientState.flag(p,"claimable"));
            }
            if (selected!=null && state.quests.containsKey(selected)) detailsButtons();
        } else if (tab.equals("team")) teamWidgets();
        else settingsWidgets();
    }
    private void switchTab(String next) { tab=next; confirmation=null; init(); }
    private void refreshCatalogue() {
        if (catalogue==state.catalogueRevision) return;
        catalogue=state.catalogueRevision; layouts.clear(); icons.clear();
        if (!state.categories.containsKey(category)) category=state.categories.keySet().stream().findFirst().orElse(null);
        if (!state.quests.containsKey(selected)) selected=null;
        chooseCategory(category); init();
    }
    private void chooseCategory(String id) {
        category=id;
        QuestPlusClient.NEW_UNLOCKS.removeIf(qid -> state.quests.containsKey(qid) && Objects.equals(state.quests.get(qid).category(),id));
        filteredCatalogue=-2;
        layout=layouts.computeIfAbsent(Objects.toString(id,""),ignored -> QuestLayout.compute(state.quests.values().stream().filter(q -> Objects.equals(q.category(),id)).toList()));
        Map<QuestLayout.Edge,List<QuestLayout.Point>> selectedRoutes=new HashMap<>();
        var data=state.categories.get(id);
        if (data!=null && data.has("linkRoutes")) for (var entry:data.getAsJsonArray("linkRoutes")) {
            var route=entry.getAsJsonObject(); List<QuestLayout.Point> points=new ArrayList<>();
            for (var point:route.getAsJsonArray("points")) {
                var value=point.getAsJsonObject(); points.add(new QuestLayout.Point(value.get("x").getAsDouble(),value.get("y").getAsDouble()));
            }
            selectedRoutes.put(new QuestLayout.Edge(route.get("from").getAsString(),route.get("to").getAsString()),List.copyOf(points));
        }
        routes=Map.copyOf(selectedRoutes);
        boolean fresh=!cameras.containsKey(id);
        camera=cameras.computeIfAbsent(id,ignored -> new QuestCamera(QuestCamera.MIN_ZOOM,QuestCamera.MAX_ZOOM));
        if (fresh) fit();
    }
    private void fit() {
        List<QuestLayout.Point> points=new ArrayList<>(layout.points().values());
        double padX=24,padY=24;
        for(var quest:state.quests.values())if(Objects.equals(quest.category(),category)){
            padX=Math.max(padX,quest.size()/2.0);
            padY=Math.max(padY,quest.size()/2.0);
        }
        var data=state.categories.get(category);
        if(data!=null && data.has("annotations")){
            for(var item:data.getAsJsonArray("annotations")){
                if(!item.isJsonObject())continue;
                var note=item.getAsJsonObject();
                if(!note.has("x")||!note.has("y"))continue;
                points.add(new QuestLayout.Point(note.get("x").getAsDouble(),note.get("y").getAsDouble()));
                padX=Math.max(padX,(note.has("width")?note.get("width").getAsDouble():170)/2);
                padY=Math.max(padY,(note.has("height")?note.get("height").getAsDouble():100)/2);
            }
        }
        camera.fitBounds(points,treeLeft,treeTop,treeRight-treeLeft,treeBottom-treeTop,padX,padY);
    }
    @Override public void tick() {
        if (closingAt!=0 && animationProgress()<=0) { super.onClose(); return; }
        // Rebuild after the click event, not in the middle of widget dispatch.
        // The setting and the viewport must change together in one tick.
        if (pendingInterfaceScale!=null) {
            double next=pendingInterfaceScale;
            pendingInterfaceScale=null;
            settings.scale=next;
            settings.save();
            init();
        }
        refreshCatalogue();
        if (sequence!=state.sequence) {
            sequence=state.sequence;
            if (tab.equals("team") && (!Objects.equals(lastTeam,state.team) || !Objects.equals(lastInvitations,state.invitations))) init();
            else if (tab.equals("quests") && selected!=null) updateQuestAction();
        }
        if (pendingManualQuest!=null && System.currentTimeMillis()-pendingManualAt>2500L) {
            pendingManualQuest=null; updateQuestAction();
        }
    }
    private String status(String id) {
        return QuestPresentation.status(state.quests.get(id),state.progress(id));
    }
    private boolean shown(String id) {
        return filtered.contains(id);
    }
    private int color(String id) { return switch(statuses.get(id)) { case "completed" -> 0xff789474; case "reward_ready" -> 0xffecc875; case "active" -> 0xffb58a56; case "available" -> 0xffb09aba; default -> 0xff716a65; }; }
    private ItemStack icon(String id) {
        return icons.computeIfAbsent(id,key -> { var resource=Identifier.tryParse(key); // A visible barrier item is the fallback for unknown IDs (e.g. a 26.4 item on 26.3).
            // Never resolve an unknown ID to AIR or paint a blank item slot.
            return resource!=null && BuiltInRegistries.ITEM.containsKey(resource)
                ? new ItemStack(BuiltInRegistries.ITEM.getValue(resource)) : new ItemStack(Items.BARRIER); });
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        // Preserve the real first-person tablet frame; an opaque GUI backdrop would hide it.
        if (QuestClientTabletFlow.nativeHandsActive()) return;
        if (TabletShaderCompat.backgroundBlurEnabled() && settings.blurIntensity>0) extractBlurredBackground(g);
        g.fill(0,0,minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),0x660d100e);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if (attachedToScreenFace) return;
        g.pose().pushMatrix();
        try {
            g.pose().translate((float)screenOffsetX,(float)screenOffsetY);
            g.pose().scale((float)screenScale);
            int ux=(int)Math.round((mx-screenOffsetX)/screenScale);
            int uy=(int)Math.round((my-screenOffsetY)/screenScale);
            extractTablet(g,ux,uy,delta);
        } finally { g.pose().popMatrix(); }
        QuestToast.draw(g);
    }
    void extractTablet(GuiGraphicsExtractor g,int mx,int my,float delta) {
        hoverTitle=null;
        visualOffset=QuestClientTabletFlow.nativeHandsActive() ? 0 : (float)((1-animationProgress())*(height-top));
        g.pose().pushMatrix(); g.pose().translate(0,visualOffset); my-=(int)visualOffset;
        if (settings.tablet) {
            // FP PAL renders the REAL arms and Blockbench tablet model itself;
            // do not paint a second 3D tablet over it.
            if (!QuestClientTabletFlow.nativeHandsActive()) {
                // The 2D fallback is the same flat display as the 3D model,
                // framed by restrained pixel chrome, never a stretched 3D model.
                QuestSkin.panel(g,left-5,top-5,right-left+10,bottom-top+10,0xff211914,0xff9a6c4b);
                g.fill(left-3,top-3,right+3,bottom+3,0xff191815);
            }
        }
        else { g.fill(left-5,top-5,right+5,bottom+5,0xff342820); g.outline(left-5,top-5,right-left+10,bottom-top+10,0xff756553); }
        if (attachedToScreenFace) g.enableScissor(left,top,right,bottom);
        QuestSkin.panel(g,left,top,right-left,bottom-top,0xff30231e,0xff805c40);
        int headerBottom=top+(23);
        g.fill(left+3,top+3,right-3,headerBottom,0xff241c18);
        g.fill(left+4,headerBottom,right-4,headerBottom+1,0xff815a3d);
        if (state.ready && tab.equals("quests")) { updateRenderCache(); drawCategoryHeader(g,mx,my); }
        if (!state.ready) {
            QuestSkin.centered(g,font,t(state.error==null ? "loading" : "error."+state.error),(left+right)/2,(top+bottom)/2-24,0xffe4dec9);
            if (state.errorDetail!=null) {
                int ey=(top+bottom)/2-8, available=Math.max(80,right-left-36);
                for (String line:wrap(state.errorDetail,available)) {
                    QuestSkin.text(g,font,line,left+18,ey,0xfff2ac9e,false);
                    ey+=11;
                    if (ey>bottom-16) break;
                }
            }
        }
        else if (tab.equals("quests")) { drawCategories(g,mx,my); if (!overlayDetails) drawTree(g,mx,my); if (selected!=null) drawDetails(g); }
        else if (tab.equals("team")) drawTeam(g);
        else QuestSkin.text(g,font,t("settings_hint"),left+14,bottom-22,0xffaaa99a,false);
        if (state.ready && state.error!=null) {
            String message=state.errorDetail==null?t("error."+state.error).getString():state.errorDetail;
            QuestSkin.text(g,font,font.plainSubstrByWidth(message,Math.max(1,right-left-30)),left+12,bottom-11,0xffe3a692,false);
        }
        super.extractRenderState(g,mx,my,delta);
        drawBoundedHover(g,mx,my);
        if (attachedToScreenFace) g.disableScissor();
        g.pose().popMatrix();
    }
    /** Draw the hover in the tablet's logical pixels, after all widgets. */
    private void drawBoundedHover(GuiGraphicsExtractor g,int mx,int my) {
        if(mx<left || mx>=right || my<top || my>=bottom)return;
        String label=hoverTitle;
        for (QuestButton button:hoverButtons) {
            if(button.visible&&button.active && mx>=button.getX() && mx<button.getX()+button.getWidth()
                && my>=button.getY() && my<button.getY()+button.getHeight()){
                label=button.hoverText().getString();
            }
        }
        if(label==null || label.isBlank())return;
        int contentWidth=Math.min(214,Math.max(20,right-left-20));
        List<String> rows=wrap(label,contentWidth);
        if(rows.isEmpty())return;
        int maxRows=Math.max(1,Math.min(14,(bottom-top-12)/11));
        if(rows.size()>maxRows){
            rows=new ArrayList<>(rows.subList(0,maxRows));
            String last=rows.get(maxRows-1);
            rows.set(maxRows-1,font.plainSubstrByWidth(last,Math.max(1,contentWidth-font.width("…")))+"…");
        }
        int textWidth=rows.stream().mapToInt(font::width).max().orElse(0);
        int boxW=Math.min(right-left-8,textWidth+12),boxH=rows.size()*11+8;
        var place=QuestTooltipLayout.place(mx,my,boxW,boxH,left+4,top+4,right-4,bottom-4);
        g.nextStratum();
        QuestSkin.panel(g,place.x(),place.y(),boxW,boxH,0xf41c1720,0xffb79a75);
        for(int i=0;i<rows.size();i++)
            QuestSkin.text(g,font,rows.get(i),place.x()+6,place.y()+4+i*11,0xfff4e3c5,false);
    }
    private void drawCategories(GuiGraphicsExtractor g,int mx,int my) {
        var categories=new ArrayList<>(state.categories.values());
        int count=Math.max(1,(treeBottom-treeTop)/26);
        categoryScroll=Math.clamp(categoryScroll,0,Math.max(0,categories.size()-count));
        for (int i=categoryScroll;i<Math.min(categories.size(),categoryScroll+count);i++) {
            var c=categories.get(i); int x=left+9,y=treeTop+(i-categoryScroll)*26;
            boolean active=c.get("id").getAsString().equals(category);
            QuestSkin.panel(g,x,y,22,22,active ? 0xff513827 : 0xff2d241e,active ? 0xffd5ae74 : 0xff6f513d);
            g.item(icon(c.has("icon") ? c.get("icon").getAsString() : "minecraft:book"),x+3,y+3);
            String catId=c.get("id").getAsString();
            boolean claimable=state.quests.values().stream().anyMatch(q -> catId.equals(q.category()) && QuestClientState.flag(state.progress(q.id()),"claimable"));
            boolean fresh=state.quests.values().stream().anyMatch(q -> catId.equals(q.category()) && QuestPlusClient.NEW_UNLOCKS.contains(q.id()));
            if(claimable || fresh){
                int marker=claimable ? 0xfff3c46d : 0xff8ad6f2;
                g.fill(x+15,y+15,x+21,y+21,0xff211914);
                g.fill(x+16,y+16,x+20,y+20,marker);
            }
            if(mx>=x && mx<x+22 && my>=y && my<y+22)
                hoverTitle=localized(c.getAsJsonObject("title"))+
                    (claimable?" · "+t("claim").getString():fresh?" · "+t("toast.unlocked").getString():"");
        }
        if (categories.size()>count) {
            if (categoryScroll>0) g.fill(left+14,treeTop-3,left+23,treeTop-2,QuestSkin.HIGHLIGHT);
            if (categoryScroll+count<categories.size()) g.fill(left+14,treeTop+count*26-3,left+23,treeTop+count*26-2,QuestSkin.HIGHLIGHT);
        }
    }
    private String localized(JsonObject map) { Map<String,String> values=new HashMap<>(); map.entrySet().forEach(e -> values.put(e.getKey(),e.getValue().getAsString())); return state.text(values); }
    private String annotationLabel(JsonObject map) {
        if (map==null) return "";
        Map<String,String> values=new HashMap<>();
        map.entrySet().forEach(e -> {
            if (e.getValue().isJsonPrimitive()&&e.getValue().getAsJsonPrimitive().isString())
                values.put(e.getKey(),e.getValue().getAsString());
        });
        // "Note" is the studio's initial placeholder, not an authored translation.
        Map<String,String> authored=new HashMap<>();
        values.forEach((key,value) -> {
            if (!value.isBlank() && !value.trim().equals("Note")) authored.put(key,value);
        });
        return state.text(authored.isEmpty()?values:authored);
    }

    private void drawCategoryHeader(GuiGraphicsExtractor g,int mx,int my) {
        var c=state.categories.get(category); if (c==null) return;
        int x=treeLeft,w=right-7-treeLeft;
        QuestSkin.panel(g,x,top+(26),w,23,0xff3b2b21,0xff866046);
        g.item(icon(c.has("icon") ? c.get("icon").getAsString() : "minecraft:book"),x+5,top+(30));
        QuestSkin.text(g,font,font.plainSubstrByWidth(localized(c.getAsJsonObject("title")),Math.max(1,w-32-(w>260 ? 90 : 0))),x+24,top+(30),QuestSkin.INK,false);
        QuestSkin.text(g,font,font.plainSubstrByWidth(localized(c.getAsJsonObject("description")),Math.max(1,w-32-(w>260 ? 90 : 0))),x+24,top+(39),QuestSkin.DIM,false);
        long done=completedNodes;
        if (w>260) {
            int bar=80,bx=right-7-bar-8;
            QuestSkin.text(g,font,done+" / "+layout.points().size(),bx,top+(30),QuestSkin.INK,false);
            QuestSkin.panel(g,bx,top+(40),bar,4,0xff211a17,0xff715849);
            if (!layout.points().isEmpty()) g.fill(bx+2,top+(41),bx+2+(int)((bar-4)*done/layout.points().size()),top+(42),0xffa37bab);
        }
        if (mx>=x && mx<x+w && my>=top+26 && my<treeTop)
            hoverTitle=localized(c.getAsJsonObject("title"))+"\n"+localized(c.getAsJsonObject("description"));
    }
    private void drawPaper(GuiGraphicsExtractor g) {
        QuestSkin.panel(g,treeLeft,treeTop,treeRight-treeLeft,treeBottom-treeTop,0xff38291f,0xff805d42);
        for (int yy=treeTop+5;yy<treeBottom-3;yy+=9) for (int xx=treeLeft+5;xx<treeRight-3;xx+=17) {
            int seed=(xx*19+yy*31)&15;
            if (seed<6) g.fill(xx,yy,Math.min(treeRight-2,xx+2+seed),yy+1,0xff3d2d22);
        }
        int c=0xff775338;
        for (int sx:new int[]{treeLeft+3,treeRight-9}) for (int sy:new int[]{treeTop+3,treeBottom-9}) {
            g.fill(sx,sy,sx+6,sy+1,c); g.fill(sx,sy,sx+1,sy+6,c); g.fill(sx+3,sy+3,sx+5,sy+5,c);
        }
    }
    private void drawTree(GuiGraphicsExtractor g,int mx,int my) {
        updateRenderCache();
        g.enableScissor(treeLeft,treeTop,treeRight,treeBottom);
        drawPaper(g);
        g.nextStratum(); drawBackgrounds(g); g.nextStratum();
        drawAnnotations(g);
        for (var edge:visibleEdges) {
            var a=layout.points().get(edge.from()); var b=layout.points().get(edge.to());
            int x1=(int)camera.screenX(a.x()),y1=(int)camera.screenY(a.y())+(int)Math.round(nodeRadius(state.quests.get(edge.from()))*camera.zoom());
            int x2=(int)camera.screenX(b.x()),y2=(int)camera.screenY(b.y())-(int)Math.round(nodeRadius(state.quests.get(edge.to()))*camera.zoom());
            int c=QuestClientState.flag(state.progress(edge.from()),"completed") ? 0xffd9c4a4 : 0xff827366;
            if (routes.containsKey(edge) && !routes.get(edge).isEmpty()) {
                int previousX=x1,previousY=y1;
                for (var point:routes.get(edge)) {
                    int x=(int)camera.screenX(point.x()),y=(int)camera.screenY(point.y());
                    drawSegment(g,previousX,previousY,x,y,c,camera.zoom()); previousX=x; previousY=y;
                }
                drawSegment(g,previousX,previousY,x2,y2,c,camera.zoom());
                g.pose().pushMatrix(); g.pose().translate(x2,y2);
                g.pose().rotate((float)Math.atan2(y2-previousY,x2-previousX));
                g.pose().scale((float)camera.zoom());
                g.fill(-4,-2,-3,3,c); g.fill(-3,-1,-2,2,c); g.fill(-2,0,0,1,c); g.pose().popMatrix();
                continue;
            }
            if (Math.max(x1,x2)<treeLeft || Math.min(x1,x2)>treeRight || Math.max(y1,y2)<treeTop || Math.min(y1,y2)>treeBottom) continue;
            int middle=(y1+y2)/2;
            drawSegment(g,x1,y1,x1,middle,c,camera.zoom());
            drawSegment(g,x1,middle,x2,middle,c,camera.zoom());
            drawSegment(g,x2,middle,x2,y2,c,camera.zoom());
            g.pose().pushMatrix(); g.pose().translate(x2,y2); g.pose().scale((float)camera.zoom());
            g.fill(-2,-3,3,-2,c); g.fill(-1,-2,2,-1,c); g.pose().popMatrix();
        }
        boolean any=false;
        for (String id:visibleNodes) {
            any=true;
            var p=layout.points().get(id); int x=(int)camera.screenX(p.x()),y=(int)camera.screenY(p.y()),r=nodeRadius(state.quests.get(id));
            // Scale the complete node, not just the distance to neighbouring nodes.
            g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale((float)camera.zoom()); g.pose().translate(-x,-y);
            QuestSkin.panel(g,x-r,y-r,2*r,2*r,0xff433a32,color(id));
            if (r>7) g.outline(x-r+2,y-r+2,2*r-4,2*r-4,0xff625344);
            if (id.equals(selected) || id.equals(keyboardQuest)) QuestSkin.panel(g,x-r-2,y-r-2,2*r+4,2*r+4,0x00433a32,0xffe9dab1);
            g.pose().pushMatrix(); g.pose().translate(x-r/2f,y-r/2f); g.pose().scale(r/16f);
            g.item(icon(state.quests.get(id).icon()),0,0); g.pose().popMatrix();
            String progression=state.quests.get(id).progression();
            if (!progression.equals("once")) {
                String marker=progression.equals("repeatable") ? "R" : progression.equals("infinite") ? "∞" : "◆";
                QuestSkin.text(g,font,marker,x+r-7,y+r-8,0xffe4cf96,false);
            }
            if (statuses.get(id).equals("reward_ready")) {
                g.fill(x+r-6,y+r-7,x+r+3,y+r+3,0xff604019);
                QuestSkin.text(g,font,"!",x+r-3,y+r-6,0xffffe3a1,false);
            }
            else if (statuses.get(id).equals("completed")) { g.fill(x+r-5,y+r-6,x+r+2,y+r+1,0xff34492e); g.fill(x+r-4,y+r-3,x+r-2,y+r-1,0xffd6e4b2); g.fill(x+r-2,y+r-4,x+r,y+r-2,0xffd6e4b2); g.fill(x+r,y+r-6,x+r+2,y+r-3,0xffd6e4b2); }
            else if (statuses.get(id).equals("locked")) { g.fill(x+r-5,y+r-4,x+r+1,y+r+1,0xffaca196); g.outline(x+r-4,y+r-7,4,4,0xffaca196); g.fill(x+r-3,y+r-2,x+r-2,y+r,0xff483c34); }
            g.pose().popMatrix();
            double hitRadius=r*camera.zoom();
            if (mx>=x-hitRadius && mx<=x+hitRadius && my>=y-hitRadius && my<=y+hitRadius) hoverTitle=titles.get(id)+" · "+t("status."+statuses.get(id)).getString()
                +(progression.equals("once") ? "" : " · "+t("progression."+progression).getString());
        }
        if (!any && layout.points().isEmpty()) QuestSkin.centered(g,font,t("empty"),(treeLeft+treeRight)/2,(treeTop+treeBottom)/2,0xff9ea99d);
        g.disableScissor();
    }
    private static void drawSegment(GuiGraphicsExtractor g,int x1,int y1,int x2,int y2,int color,double zoom) {
        double length=Math.hypot((double)x2-x1,(double)y2-y1);
        if (length==0) return;
        g.pose().pushMatrix(); g.pose().translate(x1,y1);
        g.pose().rotate((float)Math.atan2((double)y2-y1,(double)x2-x1));
        g.pose().scale((float)zoom);
        // The coordinates were already camera-transformed; scale line thickness
        // while keeping the two endpoints at their exact screen positions.
        g.fill(0,0,Math.max(1,(int)Math.ceil(length/zoom)),1,color); g.pose().popMatrix();
    }

    private void drawBackgrounds(GuiGraphicsExtractor g) {
        var data=state.categories.get(category);
        if (data==null || !data.has("annotations")) return;
        for (var entry:data.getAsJsonArray("annotations")) {
            var note=entry.getAsJsonObject();
            if (!note.has("style") || !note.get("style").getAsString().equals("background")) continue;
            int x=(int)Math.round(camera.screenX(note.get("x").getAsDouble()));
            int y=(int)Math.round(camera.screenY(note.get("y").getAsDouble()));
            int w=Math.max(1,note.get("width").getAsInt());
            int h=Math.max(1,note.get("height").getAsInt());
            double halfW=w*camera.zoom()/2,halfH=h*camera.zoom()/2;
            if (x+halfW<treeLeft || x-halfW>treeRight || y+halfH<treeTop || y-halfH>treeBottom) continue;
            int rgb=Integer.parseInt(note.get("color").getAsString().substring(1),16);
            g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale((float)camera.zoom()); g.pose().translate(-x,-y);
            int l=x-w/2,t=y-h/2;
            g.fill(l,t,l+w,t+h,0x24000000|rgb); g.outline(l,t,w,h,0x55000000|rgb);
            g.pose().popMatrix();
        }
    }
    /** Annotation coordinates use the same world-space camera as quest nodes. */
    private void drawAnnotations(GuiGraphicsExtractor g) {
        var data=state.categories.get(category);
        if (data==null || !data.has("annotations")) return;
        JsonArray imagePool=data.has("images")?data.getAsJsonArray("images"):new JsonArray();
        for (var entry:data.getAsJsonArray("annotations")) {
            var note=entry.getAsJsonObject();
            if (note.has("style") && note.get("style").getAsString().equals("background")) continue;
            int x=(int)camera.screenX(note.get("x").getAsDouble());
            int y=(int)camera.screenY(note.get("y").getAsDouble());
            int extentX=note.has("width")?Math.max(1,note.get("width").getAsInt())/2:200;
            int extentY=note.has("height")?Math.max(1,note.get("height").getAsInt())/2:150;
            if (x<treeLeft-extentX*camera.zoom() || x>treeRight+extentX*camera.zoom() || y<treeTop-extentY*camera.zoom() || y>treeBottom+extentY*camera.zoom()) continue;
            g.pose().pushMatrix(); g.pose().translate(x,y); g.pose().scale((float)camera.zoom()); g.pose().translate(-x,-y);
            try {
            if (RichAnnotationRenderer.render(g,font,note,imagePool,minecraft.options.languageCode,x,y)) continue;
            String label=annotationLabel(note.getAsJsonObject("text"));
            String style=note.has("style")?note.get("style").getAsString():"note";
            String hex=note.has("color")?note.get("color").getAsString():"#c6a986";
            int color=0xff000000|Integer.parseInt(hex.substring(1),16);
            int w=note.has("width")?Math.max(1,note.get("width").getAsInt()):Math.max(35,font.width(label)+12);
            int h=note.has("height")?Math.max(1,note.get("height").getAsInt()):20;
            String visible=font.plainSubstrByWidth(label,Math.max(1,w-12));
            String align=note.has("align")?note.get("align").getAsString():"center";
            int tx=switch(align){case "left" -> x-w/2+6; case "right" -> x+w/2-6-font.width(visible); default -> x-font.width(visible)/2;};
            if (!style.equals("heading") && !style.equals("label")) QuestSkin.panel(g,x-w/2,y-h/2,w,h,0xdd33271f,color);
            QuestSkin.text(g,font,visible,tx,y-4,color,style.equals("heading"));
            } finally { g.pose().popMatrix(); }
        }
    }
    private int nodeRadius(QuestDefinition quest){
        int size=quest==null||quest.size()==0?32:Math.max(1,quest.size());
        return size/2;
    }
    private Optional<String> hitQuestAtScreen(double x,double y){
        return visibleNodes.stream().filter(id->{
            var p=layout.points().get(id);
            double r=nodeRadius(state.quests.get(id))*camera.zoom();
            return Math.abs(camera.screenX(p.x())-x)<=r && Math.abs(camera.screenY(p.y())-y)<=r;
        }).findFirst();
    }
    private void updateRenderCache() {
        String language=minecraft.options.languageCode;
        if (filteredSequence!=state.sequence || filteredCatalogue!=catalogue || !Objects.equals(filteredQuery,query)
            || !Objects.equals(filteredFilter,filter) || !Objects.equals(filteredLanguage,language)) {
            filteredSequence=state.sequence; filteredCatalogue=catalogue; filteredQuery=query; filteredFilter=filter; filteredLanguage=language;
            filtered.clear(); titles.clear(); statuses.clear(); String needle=query.toLowerCase(Locale.ROOT);
            completedNodes=0;
            for (String id:layout.points().keySet()) {
                var q=state.quests.get(id); String title=state.text(q.title()),status=status(id); titles.put(id,title); statuses.put(id,status);
                if (status.equals("completed") || status.equals("reward_ready")) completedNodes++;
                if ((filter.equals("all") || status.equals(filter) || filter.equals("completed") && status.equals("reward_ready")) && title.toLowerCase(Locale.ROOT).contains(needle)) filtered.add(id);
            }
            cachedLeft=Double.NaN;
        }
        double l=camera.worldX(treeLeft),r=camera.worldX(treeRight),t=camera.worldY(treeTop),b=camera.worldY(treeBottom);
        if (l==cachedLeft && r==cachedRight && t==cachedTop && b==cachedBottom) return;
        cachedLeft=l; cachedRight=r; cachedTop=t; cachedBottom=b;
        visibleNodes=layout.points().entrySet().stream().filter(e->{
            if(!filtered.contains(e.getKey()))return false;
            double sx=camera.screenX(e.getValue().x()),sy=camera.screenY(e.getValue().y());
            double nr=nodeRadius(state.quests.get(e.getKey()))*camera.zoom();
            return sx+nr>=treeLeft&&sx-nr<=treeRight&&sy+nr>=treeTop&&sy-nr<=treeBottom;
        }).map(Map.Entry::getKey).toList();
        visibleEdges=layout.edges().stream().filter(e -> filtered.contains(e.from()) && filtered.contains(e.to())).filter(e -> {
            var a=layout.points().get(e.from()); var z=layout.points().get(e.to());
            return routes.containsKey(e) || Math.max(a.x(),z.x())>=l && Math.min(a.x(),z.x())<=r && Math.max(a.y(),z.y())>=t && Math.min(a.y(),z.y())<=b;
        }).toList();
    }
    private void detailsButtons() {
        int x=detailLeft,w=right-x-8;
        button("close_details",x,treeTop,w,() -> { selected=null; init(); });
        var pinButton=button(settings.pinned.contains(selected) ? "unpin" : "pin",x,treeTop+18,w,() -> {
            if(!settings.pinned.remove(selected) && !QuestClientState.flag(state.progress(selected),"completed")
                && settings.pinned.size()<ClientSettings.MAX_PINNED)
                settings.pinned.add(selected);
            settings.save(); init();
        });
        pinButton.active=settings.pinned.contains(selected) ||
            (!QuestClientState.flag(state.progress(selected),"completed") && settings.pinned.size()<ClientSettings.MAX_PINNED);
        questAction=button("claim",x,treeTop+36,w,() -> {
            if (currentAction==null) return;
            if (currentAction.equals("trigger")) {
                pendingManualQuest=selected;
                pendingManualAt=System.currentTimeMillis();
            }
            action(currentAction,selected);
            updateQuestAction();
        }); updateQuestAction();
    }
    private void updateQuestAction() {
        if (questAction==null || selected==null) return;
        var p=state.progress(selected); var q=state.quests.get(selected); if (q==null) return;
        currentAction=QuestClientState.flag(p,"claimable") ? "claim" : QuestClientState.flag(p,"waitingRestart") ? "restart" : (q.kind().equals("information") && q.acknowledgement().equals("manual") ||
            q.objectives().stream().anyMatch(o -> o.event().equals("manual") && o.target().equals(q.id())))
            && !QuestClientState.flag(p,"completed") ? "trigger" : null;
        questAction.visible=currentAction!=null;
        boolean pending=currentAction!=null && currentAction.equals("trigger") && Objects.equals(pendingManualQuest,selected)
            && System.currentTimeMillis()-pendingManualAt<2500L;
        questAction.active=currentAction!=null && !pending && (currentAction.equals("claim") || QuestClientState.flag(p,"unlocked"));
        if (currentAction!=null) questAction.setMessage(t(pending ? "processing" : currentAction));
    }
    private void drawDetails(GuiGraphicsExtractor g) {
        var q=state.quests.get(selected); if (q==null) return;
        int x=detailLeft,w=right-x-8,y=treeTop+(55),end=bottom-(7);
        if (overlayDetails) QuestSkin.panel(g,treeLeft,treeTop,right-treeLeft-5,treeBottom-treeTop,0xff30251e,QuestSkin.EDGE);
        g.enableScissor(x,y,right-5,end);
        if (!Objects.equals(detailQuest,selected) || detailSequence!=state.sequence || detailWidth!=w || !Objects.equals(detailLanguage,minecraft.options.languageCode)) {
        detailQuest=selected; detailSequence=state.sequence; detailWidth=w; detailLanguage=minecraft.options.languageCode;
        List<DetailRow> lines=new ArrayList<>();
        append(lines,state.text(q.title()),w); append(lines,t("status."+status(q.id())).getString()+" · "+t("progression."+q.progression()).getString(),w);
        String description=state.text(q.description());
        List<String> labels=q.objectives().stream().map(this::objectiveLabelForDisplay).toList();
        var richDescription=q.descriptionRich()==null?null:selectRichDescription(q.descriptionRich(),minecraft.options.languageCode);
        if(richDescription!=null && !richDescription.isEmpty()) {
            JsonArray inline=new JsonArray();
            for(var entry:richDescription) {
                if(!entry.isJsonObject())continue;
                var part=entry.getAsJsonObject();
                if(!part.has("type"))continue;
                if("image".equals(part.get("type").getAsString())){
                    if(!inline.isEmpty()){appendRichRow(lines,inline,w);inline=new JsonArray();}
                    var single=new JsonArray();single.add(part.deepCopy());
                    appendRichRow(lines,single,w);
                }else if("text".equals(part.get("type").getAsString())) inline.add(part.deepCopy());
            }
            if(!inline.isEmpty())appendRichRow(lines,inline,w);
        }else {
            var formattedDescription=QuestDescriptionText.prepare(description);
            String plainDescription=formattedDescription.plain();
            if (!QuestPresentation.redundantDescription(plainDescription,labels) && !plainDescription.isBlank()) {
                var formatted=font.split(formattedDescription.component(),Math.max(1,w)).stream().map(formattedDescription::restore).toList();
                lines.add(new DetailRow(List.of(),ItemStack.EMPTY,null,formatted));
            }
        }
        if (!q.narrative().isEmpty()) append(lines,state.text(q.narrative()),w);
        var p=state.progress(q.id()); var counters=p.getAsJsonObject("counters");
        if (!q.kind().equals("information")) {
        append(lines,t("objectives").getString()+" · "+t("mode."+q.objectiveMode()).getString(),w);
        for (var o:q.objectives()) {
            long value=counters!=null && counters.has(o.id()) ? counters.get(o.id()).getAsLong() : 0;
            String label=objectiveLabelForDisplay(o);
            appendTile(lines,label,w,iconForObjective(q,o),value+" / "+QuestPresentation.target(q,o,p));
        }
        } else if (!QuestClientState.flag(p,"completed"))
            append(lines,t(q.acknowledgement().equals("manual") ? "information_review" : "information_read").getString(),w);
        if (q.requires()!=null) { append(lines,t("requires").getString(),w); append(lines,condition(q.requires()),w); }
        if (q.progression().equals("tiered")) append(lines,t("tier_count",QuestClientState.count(p,"tier")).getString(),w);
        else if (!q.progression().equals("once") && QuestClientState.count(p,"cycles")>0)
            append(lines,t("repeat_count",QuestClientState.count(p,"cycles")).getString(),w);
        append(lines,t("rewards").getString(),w);
        for (var r:q.rewards()) appendTile(lines,reward(r),w,rewardIcon(r));
        for (var tier:q.tiers()) { append(lines,t("tier",tier.threshold()).getString(),w); for (var r:tier.rewards()) appendTile(lines,reward(r),w,rewardIcon(r)); }
        if (q.timeLimitSeconds()>0) append(lines,t("time_limit",q.timeLimitSeconds()).getString(),w);
        if (q.noDeath()) append(lines,t("no_death").getString(),w);
        detailLines=List.copyOf(lines);
        }
        int total=detailLines.stream().mapToInt(this::detailHeight).sum();
        detailsScroll=Math.clamp(detailsScroll,0,Math.max(0,total-(end-y)));
        int rowY=y-detailsScroll;
        for (var row:detailLines) {
            int h=detailHeight(row);
            if (rowY+h>y && rowY<end) {
                int textX=x;
                if (row.rich()!=null) {
                    RichAnnotationRenderer.render(g,font,row.rich(),null,"en_us",x+w/2,rowY+h/2);
                    rowY+=h;
                    continue;
                }
                if (!row.icon().isEmpty()) {
                    QuestSkin.panel(g,x,rowY,w,h-2,0xff29211c,QuestSkin.EDGE);
                    g.item(row.icon(),x+3,rowY+2);
                    textX+=23;
                }
                for (int i=0;i<row.lineCount();i++) {
                    int textY=rowY+(!row.icon().isEmpty() ? 3 : 0)+i*10;
                    if (row.formatted()==null) QuestSkin.text(g,font,row.lines().get(i),textX,textY,QuestSkin.INK,false);
                    else {
                        g.pose().pushMatrix(); g.pose().translate(textX,textY); g.pose().scale(QuestSkin.TEXT_SCALE);
                        g.text(font,row.formatted().get(i),0,0,QuestSkin.INK,false); g.pose().popMatrix();
                    }
                }
                if (row.progress()!=null) QuestSkin.text(g,font,row.progress(),x+w-3-font.width(row.progress()),rowY+3+row.lineCount()*10,QuestSkin.DIM,false);
            }
            rowY+=h;
        }
        g.disableScissor();
    }
    private int detailHeight(DetailRow row) { if(row.rich()!=null)return row.richHeight();return row.icon().isEmpty() ? row.lineCount()*10+4 : Math.max(22,row.lineCount()*10+6+(row.progress()!=null ? 10 : 0)); }
    private JsonArray selectRichDescription(Map<String,JsonArray> values,String language){
        var value=values.get(language);
        if(value!=null&&!value.isEmpty())return value;
        value=values.get("en_us");
        if(value!=null&&!value.isEmpty())return value;
        return values.entrySet().stream().filter(e->e.getValue()!=null&&!e.getValue().isEmpty())
            .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).findFirst().orElse(null);
    }
    private void appendRichRow(List<DetailRow> lines,JsonArray parts,int width) {
        int height=0;
        int textPixels=0,maxLine=11,breaks=1;
        for(var el:parts){
            var part=el.getAsJsonObject();
            if("image".equals(part.get("type").getAsString())){
                height=12+Math.min(280,part.get("height").getAsInt());
                break;
            }
            float scale=part.has("sizePx")?Math.clamp(part.get("sizePx").getAsFloat()/9f,6f/9f,128f/9f)
                :part.has("size")?Math.clamp(part.get("size").getAsFloat(),.75f,2f):1f;
            maxLine=Math.max(maxLine,(int)Math.ceil(11*scale));
            String value=part.has("text")?part.get("text").getAsString():"";
            for(String fragment:value.split("\\n",-1))textPixels+=(int)Math.ceil(font.width(fragment)*scale);
            breaks+=(int)value.chars().filter(c->c=='\n').count();
        }
        if(height==0)height=Math.min(3000,12+Math.max(breaks,(int)Math.ceil(textPixels/(double)Math.max(1,Math.min(400,width)-15)))*maxLine);
        var note=new JsonObject();
        note.addProperty("style","label");
        note.addProperty("width",Math.max(60,Math.min(400,width)));
        note.addProperty("height",Math.min(300,Math.max(20,height-8)));
        var rich=new JsonObject();rich.add("en_us",parts);note.add("rich",rich);
        lines.add(new DetailRow(List.of(),ItemStack.EMPTY,null,null,note,height));
    }
    private int richDetailHeight(JsonObject part,int width){
        if("image".equals(part.get("type").getAsString()))
            return 12+Math.min(280,part.has("height")?part.get("height").getAsInt():80);
        String value=part.has("text")?part.get("text").getAsString():"";
        float scale=part.has("sizePx")?Math.clamp(part.get("sizePx").getAsFloat()/9f,6f/9f,128f/9f)
            :part.has("size")?Math.clamp(part.get("size").getAsFloat(),.75f,2f):1f;
        int lines=0;for(String segment:value.split("\\n",-1))
            lines+=Math.max(1,(int)Math.ceil(font.width(segment)*scale/Math.max(1,Math.min(400,width)-15)));
        return Math.min(3000,12+Math.max(1,lines)*(int)Math.ceil(11*scale));
    }
    private List<String> wrap(String text,int width) {
        List<String> lines=new ArrayList<>();
        for (var line:font.split(Component.literal(text),Math.max(1,width))) {
            StringBuilder b=new StringBuilder(); line.accept((i,style,cp) -> { b.appendCodePoint(cp); return true; }); lines.add(b.toString());
        }
        return lines;
    }
    private void append(List<DetailRow> rows,String text,int width) { if (!text.isBlank()) rows.add(new DetailRow(wrap(text,width),ItemStack.EMPTY,null)); }
    private void appendTile(List<DetailRow> rows,String text,int width,ItemStack item) { appendTile(rows,text,width,item,null); }
    private void appendTile(List<DetailRow> rows,String text,int width,ItemStack item,String progress) { rows.add(new DetailRow(wrap(text,Math.max(1,width-26)),item,progress)); }
    private ItemStack iconForObjective(QuestDefinition q,QuestDefinition.Objective o) {
        var id=Identifier.tryParse(o.target().startsWith("#") ? o.target().substring(1) : o.target());
        if (id!=null && o.target().startsWith("#") && minecraft.level!=null) {
            var blocks=minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
            var tag=blocks.get(TagKey.create(Registries.BLOCK,id));
            if (tag.isPresent()) for (var holder:tag.get()) {
                var item=holder.value().asItem();
                if (item!=Items.AIR) return new ItemStack(item);
            }
        }
        if (id!=null && o.event().equals("entity_kill")) {
            var entity=BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (entity.isPresent()) {
                var egg=SpawnEggItem.byId(entity.get());
                if (egg.isPresent()) return new ItemStack(egg.get().value());
            }
        }
        return id!=null && BuiltInRegistries.ITEM.containsKey(id) ? icon(o.target()) : icon(q.icon());
    }
    private String objectiveLabelForDisplay(QuestDefinition.Objective o) {
        // The equipment marker describes itself via the condition. Old worlds
        // may still contain generated four-slot text: prefer the short, current
        // requirement instead of stacking it with a second gear description.
        if ("equipment_check".equals(o.event()) && o.conditions()!=null) {
            String gear=QuestConditionText.describe(o.conditions(),this::questName);
            if(!gear.isBlank())return gear;
        }
        String custom=state.text(o.text());
        return QuestConditionText.withConditions(custom.isBlank()?objectiveLabel(o):custom,o.conditions(),this::questName);
    }
    private String objectiveLabel(QuestDefinition.Objective o) {
        var id=Identifier.tryParse(o.target().startsWith("#") ? o.target().substring(1) : o.target());
        String target=id==null ? o.target() : id.getPath().replace('_',' ').replace('/',' ');
        if (id!=null && o.event().equals("entity_kill")) {
            var entity=BuiltInRegistries.ENTITY_TYPE.getOptional(id);
            if (entity.isPresent()) target=entity.get().getDescription().getString();
        } else if (id!=null && !o.target().startsWith("#") && BuiltInRegistries.ITEM.containsKey(id)) target=icon(o.target()).getHoverName().getString();
        return QuestPresentation.objectiveText(o,minecraft.options.languageCode,target);
    }
    private ItemStack rewardIcon(QuestDefinition.Reward r) {
        if (r.item()!=null) return icon(r.item());
        return new ItemStack(r.type().equals("experience") ? Items.EXPERIENCE_BOTTLE : Items.CHEST);
    }
    private String reward(QuestDefinition.Reward r) { if (r.text()!=null && !r.text().isEmpty()) return state.text(r.text()); return r.type().equals("experience") ? t("xp",r.amount()).getString() : r.item()!=null ? r.count()+" × "+icon(r.item()).getHoverName().getString() : t("custom_reward").getString(); }
    private String questName(String id) {
        var quest=state.quests.get(id);
        return quest==null?t("unknown_quest").getString():state.text(quest.title());
    }
    private String condition(QuestDefinition.Condition c) {
        return QuestConditionText.describe(c,this::questName);
    }
    private void teamWidgets() {
        lastTeam=state.team; lastInvitations=state.invitations;
        int x=left+14,y=top+40,w=right-left-28;
        if (state.team==null) {
            input=addRenderableWidget(new QuestTextField(font,x,y,w-85,18,t("team_name"))); input.setMaxLength(64); input.setHint(t("team_name"));
            button("create",right-90,y,76,() -> action("create",input.getValue())); y+=30;
            int capacity=Math.max(1,(bottom-y-38)/25),start=teamPage*capacity;
            for (int i=start;i<Math.min(state.invitations.size(),start+capacity);i++) {
                var invitation=state.invitations.get(i).getAsJsonObject(); String id=invitation.get("id").getAsString(); int yy=y+(i-start)*25;
                button("join",right-155,yy,66,() -> action("join",id)); button("decline",right-85,yy,71,() -> action("decline",id));
            }
            if (state.invitations.size()>capacity) pages(capacity,state.invitations.size());
        } else {
            boolean owner=state.team.get("owner").getAsString().equals(minecraft.player.getUUID().toString());
            if (owner) {
                input=addRenderableWidget(new QuestTextField(font,x,top+69,w-85,18,t("player_uuid"))); input.setMaxLength(36); input.setHint(t("player_uuid"));
                button("invite",right-90,top+69,76,() -> action("invite",input.getValue()));
            }
            var members=state.team.getAsJsonArray("members"); int capacity=Math.max(1,(bottom-top-143)/23),start=teamPage*capacity;
            if (owner) for (int i=start;i<Math.min(members.size(),start+capacity);i++) {
                String id=members.get(i).getAsJsonObject().get("id").getAsString(); int yy=top+99+(i-start)*23;
                if (!id.equals(state.team.get("owner").getAsString())) { button("kick",right-143,yy,58,() -> confirm("kick",id)); button("transfer",right-81,yy,67,() -> confirm("transfer",id)); }
            }
            if (members.size()>capacity) pages(capacity,members.size());
            button(owner ? "disband" : "leave",right-126,top+24,112,() -> confirm(owner ? "disband" : "leave",""));
        }
        if (confirmation!=null) { String[] parts=confirmation.split("\n",2); int confirmY=top+90; button("confirm",right-170,confirmY,74,() -> { action(parts[0],parts[1]); confirmation=null; init(); }); button("cancel",right-90,confirmY,76,() -> { confirmation=null; init(); }); }
    }
    private void pages(int capacity,int total) { int y=top+24; button("previous",right-240,y,52,() -> { teamPage=Math.max(0,teamPage-1); init(); }); button("next",right-184,y,52,() -> { teamPage=Math.min((total-1)/capacity,teamPage+1); init(); }); }
    private void confirm(String action,String argument) { confirmation=action+"\n"+argument; init(); }
    private void drawTeam(GuiGraphicsExtractor g) {
        int x=left+14;
        if (state.team==null) {
            int capacity=Math.max(1,(bottom-(top+70)-38)/25),start=teamPage*capacity;
            for (int i=start;i<Math.min(state.invitations.size(),start+capacity);i++) { var invitation=state.invitations.get(i).getAsJsonObject(); QuestSkin.text(g,font,font.plainSubstrByWidth(invitation.get("name").getAsString(),Math.max(30,right-left-183)),x,top+75+(i-start)*25,0xffd8dccb,false); }
            if (state.invitations.isEmpty()) QuestSkin.text(g,font,t("no_invitations"),x,top+79,0xffa9b3a5,false);
        } else {
            QuestSkin.text(g,font,font.plainSubstrByWidth(state.team.get("name").getAsString(),right-left-28),x,top+40,0xffe4d6b1,false);
            QuestSkin.text(g,font,t("owner",state.team.get("owner").getAsString()),x,top+53,0xffa9b3a5,false);
            var members=state.team.getAsJsonArray("members"); int capacity=Math.max(1,(bottom-top-143)/23),start=teamPage*capacity;
            for (int i=start;i<Math.min(members.size(),start+capacity);i++) { var m=members.get(i).getAsJsonObject(); String name=m.has("name") ? m.get("name").getAsString() : m.get("id").getAsString(); QuestSkin.text(g,font,font.plainSubstrByWidth((QuestClientState.flag(m,"online") ? "● " : "○ ")+name,Math.max(30,right-left-169)),x,top+104+(i-start)*23,0xffd8dccb,false); }
        }
        if (confirmation!=null) QuestSkin.text(g,font,t("confirm_hint"),x,top+92,0xffe3b991,false);
    }
    private void settingsWidgets() {
        int x=left+14,y=top+28,w=(right-left-36)/2;
        // Six compact rows remain usable on the 500x300 in-world tablet as
        // well as smaller Minecraft GUI scales.
        int available=bottom-y-32;
        int row=Math.max(16,Math.min(20,(available-15)/5));
        String[] names={"tablet","blur","animations","reduced_motion","notifications","hud"};
        boolean[] values={settings.tablet,TabletShaderCompat.backgroundBlurEnabled(),
            settings.animations,settings.reducedMotion,settings.notifications,settings.hud};
        for (int i=0;i<names.length;i++) {
            final int n=i;
            QuestButton b=button(names[i],x+(i%2)*(w+8),y+(i/2)*row,w,() -> {
                switch(n) {
                    case 0 -> settings.tablet=!settings.tablet;
                    case 1 -> settings.blur=!settings.blur;
                    case 2 -> settings.animations=!settings.animations;
                    case 3 -> settings.reducedMotion=!settings.reducedMotion;
                    case 4 -> settings.notifications=!settings.notifications;
                    case 5 -> settings.hud=!settings.hud;
                }
                settings.save(); init();
            });
            b.setMessage(t(names[i]).copy().append(": ").append(t(values[i] ? "on" : "off")));
            if(n==1 && TabletShaderCompat.shadersActive()) {
                b.active=false;
                b.hoverText(t("blur_shader_disabled"));
            }
        }
        QuestButton hudAnchor=button("hud_position",x,y+3*row,w,() -> {
            settings.hudPosition=(settings.hudPosition+1)%9;
            settings.save(); init();
        });
        Component hudLabel=t("hud_position").copy().append(": ").append(t("position."+settings.hudPosition));
        hudAnchor.setMessage(hudLabel);
        hudAnchor.hoverText(hudLabel);
        QuestButton notificationAnchor=button("notification_position",x+w+8,y+3*row,w,() -> {
            settings.notificationPosition=(settings.notificationPosition+1)%9;
            settings.save(); init();
        });
        Component notificationLabel=t("notification_position").copy().append(": ").append(t("position."+settings.notificationPosition));
        notificationAnchor.setMessage(notificationLabel);
        notificationAnchor.hoverText(notificationLabel);
        button("scale",x,y+4*row,w,() -> {
            pendingInterfaceScale=settings.scale>=1.25 ? 0.75 : settings.scale+0.25;
        }).setMessage(t("scale",Math.round(settings.scale*100)));
        button("blur_intensity",x+w+8,y+4*row,w,() -> {
            settings.blurIntensity=settings.blurIntensity>=0.99 ? 0 : Math.min(1,settings.blurIntensity+0.2);
            settings.save(); init();
        }).setMessage(t("blur_intensity",Math.round(settings.blurIntensity*100)));
        button("stay_open_on_damage",x,y+5*row,w,() -> {
            settings.stayOpenOnDamage=!settings.stayOpenOnDamage;
            settings.save(); init();
        }).setMessage(t("stay_open_on_damage").copy().append(": ").append(t(settings.stayOpenOnDamage ? "on" : "off")));
        // These options affect the illuminated first-person 3D screen only.
        // The 2D fallback does not use the 3D backlight.
        if(settings.tablet){
            QuestButton emissive=button("screen_emissive",x,y+6*row,w,() -> {
                settings.screenEmissive=!settings.screenEmissive;
                settings.save(); init();
            });
            Component emissiveLabel=t("screen_emissive").copy().append(": ")
                .append(t(settings.screenEmissive ? "on" : "off"));
            emissive.setMessage(emissiveLabel);
            emissive.hoverText(emissiveLabel);

            QuestButton brightness=button("screen_brightness",x+w+8,y+6*row,w,() -> {
                int steps=(int)Math.round(settings.screenBrightness*10);
                settings.screenBrightness=(steps+1)%11/10.0;
                settings.save(); init();
            });
            Component brightnessLabel=t("screen_brightness",Math.round(settings.screenBrightness*100));
            brightness.setMessage(brightnessLabel);
            brightness.hoverText(brightnessLabel);
        }
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if (attachedToScreenFace) event=tabletEvent(event);
        else event=new MouseButtonEvent(twoDX(event.x()),twoDY(event.y()),event.buttonInfo());
        event=new MouseButtonEvent(event.x(),event.y()-visualOffset,event.buttonInfo());
        if (super.mouseClicked(event,doubleClick)) return true;
        if (!tab.equals("quests") || event.button()!=InputConstants.MOUSE_BUTTON_LEFT) return false;
        double x=event.x(),y=event.y();
        if (x>=left+8 && x<treeLeft-5 && y>=treeTop && y<treeBottom) {
            int index=categoryScroll+(int)((y-treeTop)/26); var ids=new ArrayList<>(state.categories.keySet());
            if (index<ids.size()) { selected=null; chooseCategory(ids.get(index)); init(); } return true;
        }
        if (!overlayDetails && inTree(x,y)) { dragging=true; dragged=false; downX=x; downY=y; return true; }
        return false;
    }
    private boolean inTree(double x,double y) { return x>=treeLeft && x<treeRight && y>=treeTop && y<treeBottom; }
    private double twoDX(double x){return (x-screenOffsetX)/screenScale;}
    private double twoDY(double y){return (y-screenOffsetY)/screenScale;}
    private MouseButtonEvent tabletEvent(MouseButtonEvent event) {
        var point=TabletFaceAnchor.pointer(event.x(),event.y());
        return new MouseButtonEvent(point==null ? -10000 : point.x()/TabletSurfaceRenderer.uiScale(),point==null ? -10000 : point.y()/TabletSurfaceRenderer.uiScale(),event.buttonInfo());
    }
    @Override public void mouseMoved(double x,double y) {
        if (attachedToScreenFace) {
            var point=TabletFaceAnchor.pointer(x,y);
            x=point==null ? -10000 : point.x()/TabletSurfaceRenderer.uiScale(); y=point==null ? -10000 : point.y()/TabletSurfaceRenderer.uiScale();
        }
        else {x=twoDX(x);y=twoDY(y);}
        super.mouseMoved(x,y);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if (attachedToScreenFace) {
            var previous=TabletFaceAnchor.pointer(event.x()-dx,event.y()-dy);
            event=tabletEvent(event);
            if (previous==null || event.x()<0) { dx=0;dy=0; }
            else { dx=event.x()-previous.x()/TabletSurfaceRenderer.uiScale();dy=event.y()-previous.y()/TabletSurfaceRenderer.uiScale(); }
        }
        else {dx/=screenScale;dy/=screenScale;
            event=new MouseButtonEvent(twoDX(event.x()),twoDY(event.y()),event.buttonInfo());}
        event=new MouseButtonEvent(event.x(),event.y()-visualOffset,event.buttonInfo());
        if (dragging) { if (Math.hypot(event.x()-downX,event.y()-downY)>3) dragged=true; if (dragged) camera.pan(dx,dy); return true; }
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (attachedToScreenFace) event=tabletEvent(event);
        else event=new MouseButtonEvent(twoDX(event.x()),twoDY(event.y()),event.buttonInfo());
        event=new MouseButtonEvent(event.x(),event.y()-visualOffset,event.buttonInfo());
        if (dragging && event.button()==InputConstants.MOUSE_BUTTON_LEFT) {
            dragging=false;
            if (!dragged && inTree(event.x(),event.y())) hitQuestAtScreen(event.x(),event.y()).filter(this::shown).ifPresent(id -> { selected=id; detailsScroll=0; init(); });
            return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if (attachedToScreenFace) {
            var point=TabletFaceAnchor.pointer(x,y);
            if (point==null) return false;
            x=point.x()/TabletSurfaceRenderer.uiScale();y=point.y()/TabletSurfaceRenderer.uiScale();
        }
        if (!attachedToScreenFace) {x=twoDX(x);y=twoDY(y);}
        y-=visualOffset;
        if (tab.equals("quests")) {
            if (x>=left+7 && x<treeLeft && y>=treeTop && y<treeBottom) {
                int count=Math.max(1,(treeBottom-treeTop)/26);
                categoryScroll=Math.clamp(categoryScroll-(int)Math.signum(vertical),0,Math.max(0,state.categories.size()-count)); return true;
            }
            if (selected!=null && x>=detailLeft) { detailsScroll-=(int)(vertical*24); return true; }
            if (inTree(x,y)) {
                if (horizontal!=0) camera.pan(-horizontal*36,0);
                if (vertical!=0) camera.zoomAt(Math.pow(QuestCamera.ZOOM_STEP,vertical),x,y);
                return true;
            }
        }
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key()==InputConstants.KEY_F6 && tab.equals("quests")) {
            updateRenderCache(); var ids=filtered.stream().sorted().toList();
            if (!ids.isEmpty()) { keyboardQuest=ids.get((ids.indexOf(keyboardQuest)+1)%ids.size()); clearFocus(); triggerImmediateNarration(false); }
            return true;
        }
        if (event.key()==InputConstants.KEY_RETURN && keyboardQuest!=null && !(getFocused() instanceof EditBox)) {
            selected=keyboardQuest; detailsScroll=0; init(); return true;
        }
        if (event.key()!=InputConstants.KEY_ESCAPE && getFocused() instanceof EditBox box && box.isFocused()) return super.keyPressed(event);
        switch(event.key()) {
            case InputConstants.KEY_LEFT -> camera.pan(24,0); case InputConstants.KEY_RIGHT -> camera.pan(-24,0); case InputConstants.KEY_UP -> camera.pan(0,24); case InputConstants.KEY_DOWN -> camera.pan(0,-24);
            case InputConstants.KEY_HOME -> fit(); case InputConstants.KEY_EQUALS,InputConstants.KEY_ADD -> camera.zoomAt(QuestCamera.ZOOM_STEP,(treeLeft+treeRight)/2.0,(treeTop+treeBottom)/2.0); case InputConstants.KEY_MINUS -> camera.zoomAt(1/QuestCamera.ZOOM_STEP,(treeLeft+treeRight)/2.0,(treeTop+treeBottom)/2.0);
            case InputConstants.KEY_J -> onClose();
            case InputConstants.KEY_ESCAPE -> { if (selected!=null) { selected=null; init(); } else onClose(); }
            default -> { return super.keyPressed(event); }
        }
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public Component getNarrationMessage() {
        var quest=state.quests.get(keyboardQuest);
        return quest==null ? super.getNarrationMessage() : Component.literal(state.text(quest.title())+". "+t("status."+status(quest.id())).getString());
    }
}
