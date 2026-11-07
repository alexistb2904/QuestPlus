package dev.questplus.benchmark;

import com.google.gson.GsonBuilder;
import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;

/** Aggregate private JFR recordings without exporting raw stacks or environment data. */
public final class JfrSummary {
    public static void main(String[] args) throws Exception {
        Map<String,Long> cpu=new HashMap<>(),allocations=new HashMap<>(),threads=new HashMap<>();
        long samples=0,matched=0;
        try(var recording=new RecordingFile(Path.of(args[0]))) {
            while(recording.hasMoreEvents()) {
                var event=recording.readEvent();String type=event.getEventType().getName();
                boolean execution=type.equals("jdk.ExecutionSample"),allocation=type.equals("jdk.ObjectAllocationSample");
                if(!execution&&!allocation)continue;
                if(execution)samples++;
                var stack=event.getStackTrace();if(stack==null)continue;
                String caller=null;
                for(var frame:stack.getFrames()) {
                    var method=frame.getMethod();String owner=method.getType().getName();
                    if(owner.startsWith("dev.questplus.")&&!owner.startsWith("dev.questplus.benchmark.")
                        &&!owner.contains("QuestClientLoadTest")&&!owner.contains("benchmarkmixin")) {
                        caller=owner+"."+method.getName();break;
                    }
                }
                if(caller==null)continue;
                if(execution) {
                    matched++;cpu.merge(caller,1L,Long::sum);
                    var thread=event.getThread("sampledThread");
                    threads.merge(thread==null?"unknown":thread.getJavaName(),1L,Long::sum);
                } else allocations.merge(caller,event.getLong("weight"),Long::sum);
            }
        }
        if(matched==0)throw new IllegalArgumentException("No QuestPlus execution samples found");
        var result=new LinkedHashMap<String,Object>();
        result.put("execution_samples_total",samples);result.put("execution_samples_questplus",matched);
        result.put("questplus_samples_by_thread",threads);
        result.put("cpu_first_questplus_frame",top(cpu));
        result.put("allocation_weight_first_questplus_frame",top(allocations));
        String json=new GsonBuilder().setPrettyPrinting().create().toJson(result);
        Files.writeString(Path.of(args[1]),json);System.out.println(json);
    }
    private static List<Map<String,Object>> top(Map<String,Long> values) {
        return values.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed())
            .limit(20).map(e->Map.<String,Object>of("method",e.getKey(),"value",e.getValue())).toList();
    }
}
