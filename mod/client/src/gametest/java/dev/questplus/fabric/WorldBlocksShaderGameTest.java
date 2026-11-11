package dev.questplus.fabric;

import dev.questplus.fabric.client.QuestPlusClient;
import dev.questplus.fabric.client.TabletCompatibilityTest;
import dev.questplus.team.ProgressScope;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/**
 * Board, station and relay under every installed shader pack. Each pack is shot with and
 * without the blocks from one fixed camera: outside the blocks' vanilla footprint the two
 * frames must match, so stray geometry (wrong vertex layout, unmapped pipeline) fails.
 */
public final class WorldBlocksShaderGameTest {
    /** Share of pixels outside the footprint allowed to change: shader noise stays far below, the reported bug covered half the frame. */
    private static final double MAX_STRAY=.02;
    /** Masks the left half, where the crate, its particles and loot stand; the right half shows only wall, floor and station. */
    private static final boolean[] RIGHT_HALF=new boolean[1280*720];
    static{for(int y=0;y<720;y++)Arrays.fill(RIGHT_HALF,y*1280,y*1280+640,true);}
    public static void run(ClientGameTestContext context){
        var packs=new ArrayList<String>();packs.add("");
        try(var files=Files.list(FabricLoader.getInstance().getGameDir().resolve("shaderpacks"))){
            packs.addAll(files.map(p->p.getFileName().toString()).filter(n->n.endsWith(".zip")).sorted().toList());
        }catch(Exception e){throw new AssertionError(e);}
        try(var world=context.worldBuilder().create()){
            var server=world.getServer();BlockPos[] origin={null};
            context.waitFor(c->QuestPlusClient.STATE.ready,600);
            context.getInput().resizeWindow(1280,720);
            context.runOnClient(c->{c.options.guiScale().set(2);c.options.fov().set(70);c.options.bobView().set(false);QuestPlusClient.settings.tablet=false;
                c.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);});
            for(String cmd:List.of("time set 6000","gamerule advance_time false","gamerule advance_weather false","weather clear"))server.runCommand(cmd);
            server.runOnServer(instance->{try{
                var rt=QuestPlusMod.runtime();var p=instance.getPlayerList().getPlayers().getFirst();var level=p.level();
                var folder=instance.getWorldPath(LevelResource.ROOT).resolve("questplus/quests");
                String[] names={"A New Beginning","Mine & Gather","Alchemists Laboratory with an exceptionally long quest title"};
                String[] icons={"book","iron_pickaxe","grass_block"};
                for(int i=0;i<3;i++)Files.writeString(folder.resolve("shader-blocks-"+i+".json5"),"""
                        {schemaVersion:2,id:'questplus:shader_blocks_%d',category:'%s',icon:'minecraft:%s',title:{en_us:'%s'},
                        progression:'once',objectives:[{id:'stone',event:'item_turn_in',target:'minecraft:stone',count:%d}],rewards:[]}
                        """.formatted(i,rt.categories().getFirst().id(),icons[i],names[i],new int[]{5,64,10}[i]));
                rt.reload();p.setGameMode(GameType.CREATIVE);
                var o=origin[0]=p.blockPosition().offset(-2,0,6);
                // A wall filling the view keeps sky, clouds and sun (the noisiest shader output) out of the comparison.
                for(int x=-8;x<12;x++)for(int y=-1;y<9;y++)level.setBlock(o.offset(x,y,1),Blocks.STONE_BRICKS.defaultBlockState(),3);
                for(int x=-8;x<12;x++)for(int z=-8;z<1;z++){level.setBlock(o.offset(x,-1,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
                    for(int y=0;y<9;y++)if(z<0||y>1||x<0||x>3)level.setBlock(o.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
                rt.setObjectiveProgress(p,"questplus:shader_blocks_0","stone",3);rt.setObjectiveProgress(p,"questplus:shader_blocks_1","stone",24);
                for(int i=0;i<3;i++)rt.player(UUID.nameUUIDFromBytes(("shader-leader-"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8))).lastKnownName="Adventurer"+(i+1);
            }catch(Exception error){throw new RuntimeException(error);}});
            context.waitFor(c->QuestPlusClient.STATE.quests.containsKey("questplus:shader_blocks_2"),300);
            boolean[] mask=null;var failures=new ArrayList<String>();
            for(String pack:packs){
                String label=pack.isEmpty()?"vanilla":pack.replace(".zip","");
                context.runOnClient(c->TabletCompatibilityTest.shader(pack));
                place(context,server,origin[0]);
                context.waitTicks(60);
                var with=shot(context,server,origin[0],"blocks-"+label+"-with");
                server.runCommand("time set 18000");context.waitTicks(15);
                dev.questplus.compat.ClientGameTestShims.screenshot(context, "blocks-"+label+"-night");
                server.runCommand("time set 6000");context.waitTicks(15);
                // Fixed camera at 1280x720: first quest title on the board, quest title on the station screen.
                int board=text(with,484,362,600,386),station=text(with,1112,489,1190,512);
                QuestPlusMod.LOGGER.info("WORLD BLOCKS TEXT {} board={} station={}",label,board,station);
                if(board<100||station<50)failures.add(label+" (text missing: board "+board+", station "+station+")");
                clear(server,origin[0]);context.waitTicks(30);
                var without=shot(context,server,origin[0],"blocks-"+label+"-without");
                // The crate on the emptied scene, mid-burst: its glow must stay on the left, where it stands.
                crate(server,origin[0]);context.waitTicks(QuestLootChest.BURST+10);
                var crate=shot(context,server,origin[0],"blocks-"+label+"-crate");
                clear(server,origin[0]);context.waitTicks(10);
                if(mask==null){
                    mask=footprint(with,without);
                    double coverage=0;for(boolean m:mask)if(m)coverage++;coverage/=mask.length;
                    // A wider mask would hide stray geometry: the blocks cover about a quarter of the frame.
                    if(coverage>.3)throw new AssertionError("Vanilla footprint covers "+coverage+" of the frame");
                }
                double stray=stray(with,without,mask),veil=stray(crate,without,RIGHT_HALF);
                boolean pass=stray<=MAX_STRAY&&veil<=MAX_STRAY;
                QuestPlusMod.LOGGER.info("WORLD BLOCKS SHADER {} {} stray={} crate={}",pass?"PASS":"FAIL",label,
                        String.format(Locale.ROOT,"%.4f",stray),String.format(Locale.ROOT,"%.4f",veil));
                if(!pass)failures.add(label+String.format(Locale.ROOT," (blocks %.1f%%, crate %.1f%%)",stray*100,veil*100));
            }
            context.runOnClient(c->TabletCompatibilityTest.shader(""));
            if(!failures.isEmpty())throw new AssertionError("World blocks draw outside their footprint under: "+failures);
            QuestPlusMod.LOGGER.info("WORLD BLOCKS SHADER MATRIX PASS {}",packs.size()-1);
        }
    }
    /** Board 4x2 on the wall, a configured station and a relay on the floor, camera fixed in front. */
    private static void place(ClientGameTestContext context,TestServerContext server,BlockPos o){
        server.runOnServer(instance->{try{
            var p=instance.getPlayerList().getPlayers().getFirst();var level=p.level();
            p.connection.teleport(o.getX()+2,o.getY(),o.getZ()-4.5,0,0);
            for(int y=0;y<2;y++)for(int x=0;x<4;x++){
                var wall=o.offset(x,y,1);var stack=new ItemStack(QuestWorldBlocks.BOARD);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(wall).add(0,0,-.5),Direction.NORTH,wall,false));
            }
            var board=(QuestUtilityEntity)level.getBlockEntity(o);
            board.configure(p,new QuestBlockConfig(ProgressScope.PERSONAL,List.of("questplus:shader_blocks_0","questplus:shader_blocks_1","questplus:shader_blocks_2"),
                    "","","mixed","players","completed","binary",1,true,63,false,false),null,0);
            board.refresh();
            BlockPos[] floor={o.offset(-2,0,-1),o.offset(5,0,-1)};
            for(int i=0;i<2;i++){
                var stack=new ItemStack(i==0?QuestWorldBlocks.STATION:QuestWorldBlocks.RELAY);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                var below=floor[i].below();
                p.gameMode.useItemOn(p,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(below).add(0,.5,0),Direction.UP,below,false));
                var be=(QuestUtilityEntity)level.getBlockEntity(floor[i]);
                if(be==null)throw new AssertionError("Utility block placement failed");
                be.configure(p,new QuestBlockConfig(ProgressScope.PERSONAL,List.of("questplus:shader_blocks_1"),"","stone","pinned","players","completed","binary",1,true,63,true,false),null,0);
            }
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            if(!level.getBlockState(o.offset(3,1,0)).is(QuestWorldBlocks.BOARD))throw new AssertionError("Board placement failed");
        }catch(Exception error){throw new RuntimeException(error);}});
        context.runOnClient(c->{c.player.setYRot(0);c.player.setXRot(0);});
    }
    /** A loot crate mid-reveal: glow, orbiting items and its title. */
    private static void crate(TestServerContext server,BlockPos o){
        server.runOnServer(instance->{var p=instance.getPlayerList().getPlayers().getFirst();
            if(!QuestLootChest.place(p.level(),o.offset(4,0,-2),p,new QuestRuntime.OpenedCrate("questplus:shader_blocks",
                    List.of(new ItemStack(Items.DIAMOND,3),new ItemStack(Items.GOLDEN_APPLE)),3)))throw new AssertionError("Crate placement failed");
        });
    }
    /** Same camera for every frame, without toasts: only the blocks may differ between two shots. */
    private static BufferedImage shot(ClientGameTestContext context,TestServerContext server,BlockPos o,String name){
        server.runOnServer(instance->instance.getPlayerList().getPlayers().getFirst().connection.teleport(o.getX()+2,o.getY(),o.getZ()-4.5,0,0));
        context.runOnClient(c->{c.player.setYRot(0);c.player.setXRot(0);c.player.yRotO=0;c.player.xRotO=0;dev.questplus.compat.ClientGameTestShims.toasts(c).clear();clearQuestToasts();});
        context.waitTicks(5);
        return image(dev.questplus.compat.ClientGameTestShims.screenshot(context, name));
    }
    /** Quest unlock notices are the mod's own HUD toast, outside the vanilla toast manager. */
    private static void clearQuestToasts(){
        try{var clear=Class.forName("dev.questplus.fabric.client.QuestToast").getDeclaredMethod("clear");clear.setAccessible(true);clear.invoke(null);}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private static void clear(TestServerContext server,BlockPos o){
        server.runOnServer(instance->{var level=instance.getPlayerList().getPlayers().getFirst().level();
            for(var pos:List.of(o.offset(-2,0,-1),o.offset(5,0,-1),o.offset(4,0,-2)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            for(int y=0;y<2;y++)for(int x=0;x<4;x++)level.setBlock(o.offset(x,y,0),Blocks.AIR.defaultBlockState(),3);
        });
    }
    /**
     * Light glyph pixels on the dark slate, by their darkest channel: a blank row has almost none, and neither has
     * text a shader tints red (one-channel glyphs sampled as RGBA), which still reads as missing.
     */
    static int text(BufferedImage image,int x0,int y0,int x1,int y1){
        long sum=0;int n=0,count=0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++){sum+=low(image.getRGB(x,y));n++;}
        double mean=(double)sum/n;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)if(low(image.getRGB(x,y))>mean+30)count++;
        return count;
    }
    private static int low(int rgb){return Math.min(rgb>>16&255,Math.min(rgb>>8&255,rgb&255));}
    private static BufferedImage image(Path path){
        try{return ImageIO.read(path.toFile());}catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static int diff(int a,int b){
        return Math.max(Math.abs((a>>16&255)-(b>>16&255)),Math.max(Math.abs((a>>8&255)-(b>>8&255)),Math.abs((a&255)-(b&255))));
    }
    /** Where the blocks are in the vanilla frame, grown by 24px for shader shadows and bloom. Faint changes (relay light, vignette) are left out. */
    static boolean[] footprint(BufferedImage with,BufferedImage without){
        int w=with.getWidth(),h=with.getHeight(),grow=24;var mask=new boolean[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(diff(with.getRGB(x,y),without.getRGB(x,y))>24)
            for(int dy=Math.max(0,y-grow);dy<Math.min(h,y+grow+1);dy++)Arrays.fill(mask,dy*w+Math.max(0,x-grow),dy*w+Math.min(w,x+grow+1),true);
        return mask;
    }
    /** Share of pixels outside the footprint that the blocks changed visibly. */
    static double stray(BufferedImage with,BufferedImage without,boolean[] mask){
        int w=with.getWidth(),h=with.getHeight(),changed=0,total=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++)if(!mask[y*w+x]){total++;if(diff(with.getRGB(x,y),without.getRGB(x,y))>48)changed++;}
        return total==0?1:(double)changed/total;
    }
}
