package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.questplus.fabric.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Static models carry the frame; cached rows carry quests, progress and the leaderboard. */
public final class QuestWorldBlockRenderer implements BlockEntityRenderer<QuestUtilityEntity,QuestWorldBlockRenderer.State> {
    /** The mod's outline face at its lowest oversample: world text is minified and sampled NEAREST. */
    private static final Style UI=face("ui_2"),BOLD=face("bold_2");
    /** Same scale as the pixel face had: the 10px em gives about 6.7px capitals (pixel face: 7), so going lower shrinks the text. */
    private static final float BOARD_TEXT_SCALE=1.25f;
    private final Font font;
    private final ItemModelResolver items;
    private record Line(QuestUtilityEntity.Row row,ItemStackRenderState icon){}
    private record Layout(long revision,List<Line> quests,List<Line> leaders,int pinned){}
    /** Ticks each row of an overflowing column stays before the wheel advances by one. */
    private static final int WHEEL_STEP=60;
    private final Map<QuestUtilityEntity,Layout> cache=new WeakHashMap<>();
    private static final Identifier SLATE=Identifier.fromNamespaceAndPath("questplus","textures/entity/tablet_light.png");
    /** Labels already cut to their column: measuring glyphs for every row of every board each frame was the renderer's main cost. */
    private record Fit(String value,int width,Style style){}
    private final Map<Fit,FormattedCharSequence> fitted=new HashMap<>();
    private final Map<Fit,Integer> widths=new HashMap<>();
    private FormattedCharSequence fitted(String value,int width,Style style){
        var key=new Fit(value,width,style);var text=fitted.get(key);
        if(text==null){
            if(fitted.size()>=2048)fitted.clear();
            fitted.put(key,text=FormattedCharSequence.forward(fit(value,width,style),style));
        }
        return text;
    }
    private int width(String value,Style style){
        var key=new Fit(value,0,style);Integer width=widths.get(key);
        if(width==null){
            if(widths.size()>=2048)widths.clear();
            widths.put(key,width=font.width(Component.literal(value).withStyle(style)));
        }
        return width;
    }
    public static final class State extends BlockEntityRenderState {
        int width,height,rotation,pinned;long tick;boolean board,ranking;List<Line> quests=List.of(),leaders=List.of();
        boolean station;QuestUtilityEntity.Row stationQuest;List<FormattedCharSequence> stationTitle=List.of();
    }
    /**
     * Outline (TTF) faces draw through Minecraft's grayscale text pipelines. Iris 1.11 (Minecraft 26.3) leaves the
     * polygon-offset one unmapped (text vanished, streaks crossed the screen) and samples the other's one-channel
     * glyphs as red. Under a shader pack, world text uses the bitmap face, whose pipelines Iris handles like signs.
     */
    private static final Style PIXEL=Style.EMPTY,PIXEL_BOLD=Style.EMPTY.withBold(true);
    private Style ui=UI;
    private QuestWorldBlockRenderer(BlockEntityRendererProvider.Context ctx){font=ctx.font();items=ctx.itemModelResolver();}
    private static Style face(String path){return Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("questplus",path)));}
    /** The text, or its start and an ellipsis, measured in the given face. */
    private String fit(String value,int width,Style style){
        var text=Component.literal(value).withStyle(style);
        if(font.width(text)<=width)return value;
        int ellipsis=font.width(Component.literal("…").withStyle(style));
        return width<ellipsis?font.substrByWidth(text,Math.max(0,width)).getString():font.substrByWidth(text,width-ellipsis).getString()+"…";
    }
    static void register(){BlockEntityRendererRegistry.register(QuestWorldBlocks.ENTITY,QuestWorldBlockRenderer::new);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(QuestUtilityEntity be,State state,float delta,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(be,state,delta,camera,overlay);
        var block=be.getBlockState();state.station=block.is(QuestWorldBlocks.STATION);
        if(state.station){
            state.stationQuest=be.rows.isEmpty()?null:be.rows.getFirst();
            var quest=QuestPlusClient.STATE.quests.get(be.stationQuestId());
            String title=quest!=null?QuestPlusClient.STATE.text(quest.title()):state.stationQuest==null?QuestStationScreen.t("choose_quest").getString():state.stationQuest.title();
            state.stationTitle=font.split(Component.literal(title).withStyle(TabletShaderCompat.shadersActive()?PIXEL_BOLD:BOLD),66).stream().limit(2).toList();
            state.rotation=switch(block.getValue(QuestUtilityBlock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
            return;
        }
        state.board=block.is(QuestWorldBlocks.BOARD);if(!state.board)return;
        if(block.getValue(QuestUtilityBlock.MODULAR)){state.width=be.boardWidth;state.height=be.boardHeight;}
        else {int size=block.getValue(QuestUtilityBlock.SIZE);state.width=new int[]{3,4,4,6}[size];state.height=new int[]{2,2,3,4}[size];}
        state.board=state.width>0;if(!state.board)return;
        state.ranking=be.boardLeaderboard();
        state.rotation=switch(block.getValue(QuestUtilityBlock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        var layout=cache.get(be);
        if(layout==null||layout.revision!=be.contentRevision){
            var pins=be.boardFixedPins();int pinned=0;
            while(pinned<be.rows.size()&&pins.contains(be.rows.get(pinned).id()))pinned++;
            layout=new Layout(be.contentRevision,lines(be,be.rows),lines(be,be.leaders),pinned);cache.put(be,layout);
        }
        state.quests=layout.quests;state.leaders=layout.leaders;state.pinned=layout.pinned;
        state.tick=be.getLevel()==null?0:be.getLevel().getGameTime();
    }
    private List<Line> lines(QuestUtilityEntity be,List<QuestUtilityEntity.Row> rows){
        var result=new ArrayList<Line>();
        for(var row:rows){
            var icon=new ItemStackRenderState();ItemStack stack;
            try{stack=BuiltInRegistries.ITEM.getValue(Identifier.parse(row.icon())).getDefaultInstance();}
            catch(RuntimeException e){stack=Items.PAPER.getDefaultInstance();}
            if(!row.player().isEmpty())try{stack.set(DataComponents.PROFILE,ResolvableProfile.createUnresolved(UUID.fromString(row.player())));}catch(IllegalArgumentException ignored){}
            items.updateForTopItem(icon,stack,ItemDisplayContext.FIXED,be.getLevel(),null,0);result.add(new Line(row,icon));
        }
        return List.copyOf(result);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){
        ui=TabletShaderCompat.shadersActive()?PIXEL:UI;
        if(state.station){station(state,pose,queue);return;}
        if(!state.board)return;
        var rects=new ArrayList<int[]>();
        pose.pushPose();pose.translate(.5,0,.5);pose.rotate(Axis.YP.rotationDegrees(-state.rotation));pose.translate(-.5,0,-.5);
        pose.translate(state.width-.16,state.height-.16,12.45/16);pose.scale(-1/96f,-1/96f,1/96f);
        int w=state.width*96-31,h=state.height*96-31;
        rect(rects,0,0,w,h,0xff281b12,.2f);
        if(state.ranking)column(state,pose,queue,rects,state.leaders,0,w,h,true,0);
        else {
            int questWidth=state.width>=3?(int)(w*.65):w;
            column(state,pose,queue,rects,state.quests,0,questWidth,h,false,state.pinned);
            if(questWidth<w){rect(rects,questWidth+3,0,1,h,0xff8d5d35);column(state,pose,queue,rects,state.leaders,questWidth+10,w-questWidth-10,h,true,0);}
        }
        flush(queue,pose,rects);
        pose.popPose();
    }
    private void station(State state,PoseStack pose,SubmitNodeCollector queue){
        pose.pushPose();pose.translate(.5,0,.5);pose.rotate(Axis.YP.rotationDegrees(-state.rotation));pose.translate(-.5,0,-.5);
        // The screen is on the north face, just ahead of the baked slate surface.
        pose.translate(12.15/16,11.65/16,.025/16);pose.scale(-1/128f,-1/128f,1/128f);
        var row=state.stationQuest;
        for(int i=0;i<state.stationTitle.size();i++)queue.submitText(pose,0,i*10,state.stationTitle.get(i),false,Font.DisplayMode.POLYGON_OFFSET,state.lightCoords,0xffe7d8b5,0,0);
        text(state,pose,queue,row==null?"—":row.progress(),0,20,66,0xffd0b3ec);
        var rects=new ArrayList<int[]>();
        for(int i=0;i<10;i++)rect(rects,i*6,30,5,3,
                row!=null&&i<Math.round(row.fraction()*10)?0xffc5a1e5:0xff42374e);
        flush(queue,pose,rects);
        pose.popPose();
    }
    /** Overflowing rows turn like an endless wheel (see BoardWheel); the first {@code pinned} stay on top. */
    private void column(State state,PoseStack pose,SubmitNodeCollector queue,List<int[]> rects,List<Line> lines,int x,int w,int h,boolean ranking,int pinned){
        boardText(pose,queue,Component.translatable("questplus.board."+(ranking?"adventurers":"active")).getString(),x+4,4,w-8,0xffffe4b5,ui);
        rect(rects,x,16,w,1,0xff9b663c);
        int[] shown=dev.questplus.clientmodel.BoardWheel.rows(lines.size(),Math.max(0,(h-20)/41),pinned,state.tick/WHEEL_STEP);
        for(int i=0;i<shown.length;i++){
            int index=shown[i];
            var line=lines.get(index);var row=line.row;int y=21+i*41;
            rect(rects,x,y,w,38,0xffa76c3b,.12f);rect(rects,x+1,y+1,w-2,36,0xff302016,.08f);
            pose.pushPose();pose.translate(x+11,y+13,-.15);pose.scale(16,-16,-16);
            if(ranking)pose.rotate(Axis.YP.rotationDegrees(180));
            line.icon.submit(pose,queue,net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);pose.popPose();
            // Title a pixel higher than before: the face's descenders reach further than the pixel font's.
            boardText(pose,queue,row.title(),x+23,y+3,w-29,ranking&&index<3?0xffffd477:0xffffedce,ui);
            if(ranking)boardText(pose,queue,row.progress(),x+23,y+22,w-29,0xffffedce,ui);
            else {
                boardText(pose,queue,row.detail()==null?row.state():row.detail(),x+23,y+15,w-29,0xffe0c5a2,ui);
                String value=row.count()==null?row.progress():row.count();
                int available=Math.max(0,w-29),numberWidth=Math.min((int)Math.ceil(width(value,ui)*BOARD_TEXT_SCALE),available);
                int bar=Math.min(64,available-numberWidth-7);
                if(bar>=16){
                    for(int n=0;n<8;n++)rect(rects,x+23+n*bar/8,y+28,bar/8-1,5,n<Math.round(row.fraction()*8)?0xff91db40:0xff66513e);
                    boardText(pose,queue,value,x+30+bar,y+26,available-bar-7,0xffffedce,ui);
                }else boardText(pose,queue,value,x+23,y+26,available,0xffffedce,ui);
            }
        }
    }
    private void boardText(PoseStack pose,SubmitNodeCollector queue,String value,int x,int y,int width,int color,Style style){
        if(width<=0)return;
        pose.pushPose();pose.translate(x,y,0);pose.scale(BOARD_TEXT_SCALE,BOARD_TEXT_SCALE,1);
        // No drop shadow: on the dark slate, minified and NEAREST-sampled, it reads as a smeared fake bold.
        queue.submitText(pose,0,0,fitted(value,(int)(width/BOARD_TEXT_SCALE),style),false,Font.DisplayMode.POLYGON_OFFSET,
                net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,color,0,0);
        pose.popPose();
    }
    private void text(State state,PoseStack pose,SubmitNodeCollector queue,String value,int x,int y,int width,int color){
        queue.submitText(pose,x,y,fitted(value,width,ui),false,Font.DisplayMode.POLYGON_OFFSET,state.lightCoords,color,0,0);
    }
    private static void rect(List<int[]> rects,int x,int y,int w,int h,int color){
        rect(rects,x,y,w,h,color,.05f);
    }
    private static void rect(List<int[]> rects,int x,int y,int w,int h,int color,float depth){
        rects.add(new int[]{x,y,w,h,color,Float.floatToRawIntBits(depth)});
    }
    /** One geometry node per board, not one per rectangle: every node copies the pose and is sorted on its own. */
    private static void flush(SubmitNodeCollector queue,PoseStack pose,List<int[]> rects){
        queue.submitCustomGeometry(pose,RenderTypes.entitySolid(SLATE),(p,v)->{
            for(int[] r:rects){
                float depth=Float.intBitsToFloat(r[5]);
                vertex(p,v,r[0],r[1],depth,r[4]);vertex(p,v,r[0],r[1]+r[3],depth,r[4]);
                vertex(p,v,r[0]+r[2],r[1]+r[3],depth,r[4]);vertex(p,v,r[0]+r[2],r[1],depth,r[4]);
            }
        });
    }
    private static void vertex(PoseStack.Pose p,com.mojang.blaze3d.vertex.VertexConsumer v,float x,float y,float z,int color){
        v.addVertex(p,x,y,z).setColor(color).setUv(.5f,.5f).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.util.LightCoordsUtil.FULL_BRIGHT).setNormal(p,0,0,-1);
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 48;}
}
