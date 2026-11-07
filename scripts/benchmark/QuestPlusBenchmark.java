package dev.questplus.benchmark;

import com.google.gson.*;
import com.sun.management.ThreadMXBean;
import dev.questplus.core.*;
import dev.questplus.clientdata.ClientProjection;
import dev.questplus.clientmodel.QuestLayout;
import dev.questplus.persistence.JsonProgressStore;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic component load, not Minecraft TPS or connected-player capacity. */
public final class QuestPlusBenchmark {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile Object sink;
    private static long sequence;
    private static final List<Map<String,Object>> results = new ArrayList<>();
    private static Path root;
    private static String mode;
    @FunctionalInterface interface Work { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        if (!QuestPlusBenchmark.class.desiredAssertionStatus()) throw new IllegalStateException("Run with -ea");
        root = Path.of(args[0]).toAbsolutePath();
        Files.createDirectories(root);
        mode = args.length > 1 ? args[1] : "current";
        THREADS.setThreadAllocatedMemoryEnabled(true);
        assert percentile(List.of(1.0,2.0,3.0),.99)==3.0;
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("mode", mode);
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("maxHeap", Runtime.getRuntime().maxMemory());
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("results", results);
        report.put("completed",false);
        try {
            if(args.length>2 && args[2].equals("conditions")) {
                for(int fanout:new int[]{10,100,500,999}) {
                    var registry=fixture(1000,fanout,true);
                    var engine=new QuestEngine(registry);var state=state(registry,false);
                    measure("engine_prerequisites_"+fanout,1000,1,1,()->sink=engine.applyDetailed(state,event()));
                    for(int i=0;i<fanout;i++)assert state.quests.get("bench:q"+i).counters.get("one")>1;
                }
                report.put("completed",true);return;
            }
            for (int quests : new int[]{100,1000,5000}) {
                var registry = fixture(quests, 1);
                var engine = new QuestEngine(registry);
                var state = state(registry, false);
                var detached=copy(state);
                state.quests.get("bench:q0").counters.put("one",2L);
                assert detached.quests.get("bench:q0").counters.get("one")==1;
                state.quests.get("bench:q0").counters.put("one",1L);
                var miss=new QuestEvent("block_break","minecraft:absent",Set.of(),1,Map.of());
                measure("registry_miss", quests, 1, 10000, () -> sink=mode.equals("baseline")
                    ? !registry.candidates(miss).isEmpty() : registry.hasCandidate("block_break","minecraft:absent",Set.of()));
                measure("engine_sparse", quests, 1, 1, () -> sink=engine.applyDetailed(state,event()));
                measure("progress_projection", quests, 1, 1, () -> sink=ClientProjection.progress(registry.all(),engine,state,state));
                measure("full_snapshot", quests, 1, 1, () -> {
                    var packet=new JsonObject(); packet.add("quests",ClientProjection.quests(registry.all(),state));
                    packet.add("progress",ClientProjection.progress(registry.all(),engine,state,state));
                    sink=packet.toString();
                });
                var packet=new JsonObject(); packet.add("quests",ClientProjection.quests(registry.all(),state));
                packet.add("progress",ClientProjection.progress(registry.all(),engine,state,state));
                results.add(Map.of("scenario","snapshot_size","quests",quests,"utf8_bytes",packet.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,"fragments_6000_chars",(packet.toString().length()+5999)/6000));
                measure(mode.equals("baseline")?"copy_json_24":"copy_state_24", quests, 24, 1, () -> {
                    for (int i=0;i<24;i++) sink=copy(state);
                });
                measure("layout_open", quests, 1, 1, () -> sink=QuestLayout.compute(registry.all()));
                var layout=QuestLayout.compute(registry.all());
                measure("layout_visible", quests, 1, 100, () -> sink=layout.visible(-480,-270,480,270));
                try { persistence(registry,state); }
                catch(Exception failure) {
                    results.add(Map.of("scenario","persistence_failure","quests",quests,"error",failure.toString()));
                    failure.printStackTrace();
                }
            }
            for (int fanout : new int[]{10,100,500,1000}) {
                var registry=fixture(1000,fanout);
                var engine=new QuestEngine(registry);var state=state(registry,false);
                measure("engine_fanout_"+fanout,1000,1,1,()->sink=engine.applyDetailed(state,event()));
                for(int i=0;i<fanout;i++) assert state.quests.get("bench:q"+i).counters.get("one")>1;
            }
            for(int histories:new int[]{100,1000}) {
                var registry=fixture(histories,1);var engine=new QuestEngine(registry);
                for(int players:new int[]{100,300,1000,3000}) {
                    var states=new ArrayList<PlayerProgress>();
                    for(int i=0;i<players;i++)states.add(state(registry,true));
                    measure("event_tick",histories,players,1,()->{
                        for(var state:states)sink=engine.applyDetailed(state,event());
                    });
                    for(var state:states) assert state.quests.get("bench:q0").counters.get("one")>1;
                }
            }
            report.put("completed",true);
        } finally {
            report.put("persistence_failures",results.stream().filter(r->r.get("scenario").equals("persistence_failure")).count());
            report.put("gc",ManagementFactory.getGarbageCollectorMXBeans().stream().map(gc->Map.of("name",gc.getName(),"count",gc.getCollectionCount(),"ms",gc.getCollectionTime())).toList());
            Files.writeString(root.resolve("results.json"),JSON.toJson(report));
        }
    }

    private static PlayerProgress copy(PlayerProgress state) throws Exception {
        // The pre-merge implementation had no direct copy or async-serialization API.
        if(mode.equals("baseline"))return JsonProgressStore.detachedCopy(state);
        return state.copyForAutosave();
    }

    private static void measure(String scenario,int quests,int players,int batch,Work work) throws Exception {
        long until=System.nanoTime()+350_000_000L;
        do { for(int i=0;i<batch;i++)work.run(); } while(System.nanoTime()<until);
        var samples=new ArrayList<Double>();
        long allocated=THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId());
        long start=System.nanoTime();until=start+(scenario.equals("event_tick")?3_000_000_000L:1_000_000_000L);
        do {
            long t=System.nanoTime();for(int i=0;i<batch;i++)work.run();
            samples.add((System.nanoTime()-t)/1e6/batch);
        } while((System.nanoTime()<until || scenario.equals("event_tick")&&samples.size()<30)
            && samples.size()<100000 && System.nanoTime()-start<15_000_000_000L);
        long bytes=THREADS.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
        double elapsed=(System.nanoTime()-start)/1e9;
        Collections.sort(samples);
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("scenario",scenario);row.put("quests",quests);row.put("players_or_batch",players);
        row.put("samples",samples.size());row.put("batch",batch);
        row.put("p50_ms",percentile(samples,.50));row.put("p95_ms",percentile(samples,.95));
        row.put("p99_ms",percentile(samples,.99));row.put("max_ms",samples.getLast());
        row.put("ops_per_second",samples.size()*batch/elapsed);
        row.put("caller_bytes_per_op",(double)bytes/samples.size()/batch);
        results.add(row);
        System.out.printf(Locale.ROOT,"%s quests=%d subjects=%d p50=%.3f p99=%.3f ms bytes=%.0f n=%d%n",scenario,quests,players,row.get("p50_ms"),row.get("p99_ms"),row.get("caller_bytes_per_op"),samples.size());
    }
    private static double percentile(List<Double> values,double fraction) {
        return values.get(Math.max(0,(int)Math.ceil(values.size()*fraction)-1));
    }
    private static QuestEvent event() {
        return new QuestEvent("block_break","minecraft:stone",Set.of(),1,Map.of(),new UUID(0,++sequence));
    }
    private static QuestRegistry fixture(int count,int fanout) throws Exception {
        return fixture(count,fanout,false);
    }
    private static QuestRegistry fixture(int count,int fanout,boolean prerequisites) throws Exception {
        Path directory=root.resolve("fixtures/"+count+"-"+fanout+(prerequisites?"-requires":"")+"/quests");Files.createDirectories(directory);
        for(int i=0;i<count;i++) {
            String target=i<fanout?"minecraft:stone":"bench:item"+i;
            Files.writeString(directory.resolve(i+".json"),"""
                {"schemaVersion":1,"id":"bench:q%d","category":"bench:general","icon":"minecraft:stone",
                 "title":{"en_us":"Benchmark %d"},"description":{"en_us":"Synthetic quest"},
                 "objectives":[{"id":"one","event":"block_break","target":"%s","count":1000000000}],
                 "rewards":[],"progression":"once"%s}
                """.formatted(i,i,target,prerequisites&&i<count-1?",\"requires\":{\"started\":\"bench:q"+(count-1)+"\"}":""));
        }
        var registry=QuestRegistry.load(directory,(kind,id)->{});
        assert registry.all().size()==count;
        assert registry.candidates(event()).size()==fanout;
        assert registry.candidates(new QuestEvent("block_break","minecraft:absent",Set.of(),1,Map.of())).isEmpty();
        return registry;
    }
    private static PlayerProgress state(QuestRegistry registry,boolean completedHistory) {
        var state=new PlayerProgress();
        for(var q:registry.all()) {
            var progress=new PlayerProgress.QuestProgress();progress.fingerprint=q.fingerprint();
            progress.counters.put("one",1L);progress.cumulative.put("one",1L);
            progress.attemptId="00000000-0000-0000-0000-000000000001";progress.startedAt=1;
            progress.completed=completedHistory&&!q.id().equals("bench:q0");progress.terminal=progress.completed;
            state.quests.put(q.id(),progress);
        }
        state.validate();return state;
    }
    private static CompletableFuture<Void> save(JsonProgressStore store,Map<UUID,PlayerProgress> states) {
        return mode.equals("baseline")?store.saveBatch(states):store.saveAutosaveBatch(states);
    }
    private static void persistence(QuestRegistry registry,PlayerProgress state) throws Exception {
        Map<UUID,PlayerProgress> batch=new LinkedHashMap<>();
        for(int i=0;i<24;i++)batch.put(new UUID(1,i),state);
        Path directory=root.resolve("store-"+registry.all().size());
        try(var store=new JsonProgressStore(directory)) {
            save(store,batch).get(60,TimeUnit.SECONDS);
            var caller=new ArrayList<Double>();var durable=new ArrayList<Double>();var claimWait=new ArrayList<Double>();
            for(int i=0;i<10;i++) {
                long start=System.nanoTime();var pending=save(store,batch);
                caller.add((System.nanoTime()-start)/1e6);
                long claimStart=System.nanoTime();
                var claim=store.transaction(Map.of(new UUID(2,i),state),Map.of());
                pending.get(60,TimeUnit.SECONDS);durable.add((System.nanoTime()-start)/1e6);
                claim.get(60,TimeUnit.SECONDS);claimWait.add((System.nanoTime()-claimStart)/1e6);
            }
            Collections.sort(caller);Collections.sort(durable);Collections.sort(claimWait);
            results.add(Map.of("scenario","autosave_24","quests",registry.all().size(),"samples",10,
                "caller_p50_ms",percentile(caller,.5),"caller_p99_ms",percentile(caller,.99),
                "durable_p50_ms",percentile(durable,.5),"durable_p99_ms",percentile(durable,.99),
                "queued_transaction_p50_ms",percentile(claimWait,.5),"queued_transaction_p99_ms",percentile(claimWait,.99),
                "file_bytes",Files.size(directory.resolve("players/"+new UUID(1,0)+".json"))));
            var loaded=store.load(new UUID(1,0));assert loaded.quests.size()==state.quests.size();
            assert loaded.quests.get("bench:q0").counters.equals(state.quests.get("bench:q0").counters);
            System.out.println("autosave_24 quests="+registry.all().size()+" caller_p50="+percentile(caller,.5)+" durable_p50="+percentile(durable,.5));
        }
    }
}
