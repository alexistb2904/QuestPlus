package dev.questplus.fabric.client;

import com.google.gson.*;
import dev.questplus.clientdata.ClientProjection;
import dev.questplus.core.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import java.nio.file.*;
import java.util.*;

/** Real render-loop measurements; synthetic catalogue bypasses network and server mutations. */
public final class QuestClientLoadTest implements FabricClientGameTest {
    private static String phase;
    private static long start;
    private static final List<Double> frames=new ArrayList<>();
    private static final List<Map<String,Object>> results=new ArrayList<>();
    public static void frameStart() { if(phase!=null)start=System.nanoTime(); }
    public static void frameEnd() { if(phase!=null)frames.add((System.nanoTime()-start)/1e6); }
    @Override public void runTest(ClientGameTestContext context) {
        Path root=Path.of(System.getProperty("questplus.benchmark.root"));
        boolean dense=Boolean.getBoolean("questplus.benchmark.dense");
        try(var world=context.worldBuilder().create()) {
            context.waitFor(client->QuestPlusClient.STATE.ready,600);
            context.getInput().resizeWindow(1280,720);
            context.runOnClient(client->{
                client.options.enableVsync().set(false);client.options.framerateLimit().set(260);
                client.options.guiScale().set(2);client.gui.setScreen(null);
                QuestPlusClient.settings.blur=false;QuestPlusClient.settings.hud=true;
            });
            if(!dense)sample(context,"closed_original_catalogue",200);
            for(int count:dense?new int[]{1000}:new int[]{100,1000,5000}) {
                var registry=QuestRegistry.load(root.resolve("current-1/fixtures/"+count+"-1/quests"),(kind,id)->{});
                var packet=new JsonObject();packet.addProperty("type","snapshot");
                packet.addProperty("generation",100+count);packet.addProperty("sequence",1);
                packet.add("quests",ClientProjection.quests(registry.all()));packet.add("progress",new JsonObject());
                if(dense) {
                    int i=0;
                    for(var entry:packet.getAsJsonArray("quests")) {
                        var position=new JsonObject();position.addProperty("x",(i%20)*8);
                        position.addProperty("y",(i/20%20)*8);i++;
                        entry.getAsJsonObject().add("layout",position);
                    }
                }
                var category=new ClientProjection.Category("bench:general",Map.of("en_us","Load test "+count),Map.of("en_us","Synthetic benchmark"),"minecraft:stone");
                packet.add("categories",ClientProjection.categories(List.of(category),registry.all()));
                context.runOnClient(client->{
                    long t=System.nanoTime();
                    if(!QuestPlusClient.STATE.apply(packet))throw new AssertionError("Snapshot rejected");
                    results.add(Map.of("scenario","client_apply_snapshot","quests",count,"ms",(System.nanoTime()-t)/1e6));
                    if(QuestPlusClient.STATE.quests.size()!=count)throw new AssertionError("Catalogue size");
                    QuestPlusClient.settings.pinned.clear();QuestPlusClient.settings.pinned.add("bench:q0");
                    QuestPlusClient.settings.pinned.add("bench:q1");QuestPlusClient.settings.pinned.add("bench:q2");
                });
                if(!dense)sample(context,"closed_hud_"+count,200);
                context.runOnClient(client->QuestClientTabletFlow.toggle(client));
                context.waitFor(client->TabletSurfaceRenderer.active(),120);
                sample(context,"native_tablet_"+(dense?"overlapping_":"")+count,200);
                context.takeScreenshot("benchmark-tablet-"+(dense?"overlapping-":"")+count);
                context.runOnClient(client->client.gui.screen().onClose());
                context.waitFor(client->!QuestClientTabletFlow.nativeHandsActive(),120);
            }
        } catch(Exception error) {throw new AssertionError(error);}
        finally {
            try {Files.writeString(root.resolve((dense?"client-dense":"client")+"-results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(results));}
            catch(Exception error) {throw new AssertionError(error);}
        }
    }
    private static void sample(ClientGameTestContext context,String name,int ticks) {
        context.waitTicks(40);
        context.runOnClient(client->{
            client.getFramerateLimitTracker().onInputReceived();
            if(!client.getFramerateLimitTracker().getThrottleReason().name().equals("NONE"))
                throw new AssertionError("Benchmark throttled: "+client.getFramerateLimitTracker().getThrottleReason());
            frames.clear();phase=name;
        });
        context.waitTicks(ticks);
        context.runOnClient(client->{
            phase=null;
            if(!client.getFramerateLimitTracker().getThrottleReason().name().equals("NONE"))
                throw new AssertionError("Benchmark became throttled");
            if(frames.size()<30)throw new AssertionError("Too few rendered frames: "+frames.size());
            Collections.sort(frames);double total=frames.stream().mapToDouble(Double::doubleValue).sum();
            var row=Map.<String,Object>of("scenario",name,"frames",frames.size(),"p50_ms",p(.5),"p95_ms",p(.95),"p99_ms",p(.99),"max_ms",frames.getLast(),"mean_ms",total/frames.size());
            results.add(row);System.out.println("CLIENT BENCH "+row);
        });
    }
    private static double p(double fraction) {return frames.get(Math.max(0,(int)Math.ceil(frames.size()*fraction)-1));}
}
