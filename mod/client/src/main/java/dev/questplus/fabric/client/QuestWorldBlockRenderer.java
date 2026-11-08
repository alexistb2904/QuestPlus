package dev.questplus.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.questplus.fabric.*;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Baked block models remain static; only changed board content is laid out. */
public final class QuestWorldBlockRenderer implements BlockEntityRenderer<QuestUtilityEntity,QuestWorldBlockRenderer.State> {
    private final Font font;
    private final ItemModelResolver items;
    private record Line(FormattedCharSequence title,FormattedCharSequence detail,ItemStackRenderState icon){}
    private record Layout(long revision,int size,List<Line> lines){}
    private final Map<QuestUtilityEntity,Layout> cache=new WeakHashMap<>();
    public static final class State extends BlockEntityRenderState {
        int size,rotation;boolean board;List<Line> lines=List.of();
    }
    private QuestWorldBlockRenderer(BlockEntityRendererProvider.Context ctx){font=ctx.font();items=ctx.itemModelResolver();}
    static void register(){BlockEntityRendererRegistry.register(QuestWorldBlocks.ENTITY,QuestWorldBlockRenderer::new);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(QuestUtilityEntity be,State state,float delta,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(be,state,delta,camera,overlay);
        var block=be.getBlockState();state.board=block.is(QuestWorldBlocks.BOARD);if(!state.board)return;
        state.size=block.getValue(QuestUtilityBlock.SIZE);
        state.rotation=switch(block.getValue(QuestUtilityBlock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        var layout=cache.get(be);
        if(layout==null || layout.revision!=be.contentRevision || layout.size!=state.size){
            int[] width={3,4,4,6},height={2,2,3,4};int count=Math.min(be.rows.size(),(height[state.size]*48-24)/22);
            var lines=new ArrayList<Line>();
            for(var row:be.rows.subList(0,count)){
                var icon=new ItemStackRenderState();ItemStack stack;
                try{stack=BuiltInRegistries.ITEM.getValue(Identifier.parse(row.icon())).getDefaultInstance();}
                catch(RuntimeException e){stack=Items.PAPER.getDefaultInstance();}
                if(!row.player().isEmpty())stack.set(DataComponents.PROFILE,ResolvableProfile.createUnresolved(UUID.fromString(row.player())));
                items.updateForTopItem(icon,stack,ItemDisplayContext.FIXED,be.getLevel(),null,0);
                String title=font.plainSubstrByWidth(row.title(),width[state.size]*48-35);
                String detail=row.progress()+"  "+(row.state().isEmpty()?"":QuestBlockScreen.tr("condition."+row.state()).getString());
                lines.add(new Line(FormattedCharSequence.forward(title,net.minecraft.network.chat.Style.EMPTY),
                        FormattedCharSequence.forward(detail,net.minecraft.network.chat.Style.EMPTY),icon));
            }
            layout=new Layout(be.contentRevision,state.size,List.copyOf(lines));cache.put(be,layout);
        }
        state.lines=layout.lines;
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector queue,CameraRenderState camera){
        if(!state.board)return;
        int[] width={3,4,4,6},height={2,2,3,4};
        pose.pushPose();pose.translate(.5,0,.5);pose.rotate(Axis.YP.rotationDegrees(-state.rotation));pose.translate(-.5,0,-.5);
        // The local x axis is mirrored to make text face out of the north surface.
        pose.translate(width[state.size]-.2,height[state.size]-.3,12.45/16);
        pose.scale(-1/48f,-1/48f,1/48f);
        int i=0;
        for(var line:state.lines){
            int y=i++*22;
            queue.submitText(pose,20,y,line.title,false,Font.DisplayMode.POLYGON_OFFSET,state.lightCoords,0xffe4d5b8,0,0);
            queue.submitText(pose,20,y+10,line.detail,false,Font.DisplayMode.POLYGON_OFFSET,state.lightCoords,0xffa89178,0,0);
            pose.pushPose();pose.translate(8,y+7,-.1);pose.scale(14,-14,-14);line.icon.submit(pose,queue,state.lightCoords,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 48;}
}
