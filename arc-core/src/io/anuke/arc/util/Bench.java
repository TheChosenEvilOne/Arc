package io.anuke.arc.util;

import io.anuke.arc.collection.*;

public class Bench{
    private static long totalStart;
    private static String lastName;
    private static ObjectMap<String, Long> times = new ObjectMap<>();
    private static long last;

    public static void begin(String name){
        if(lastName != null){
            endi();
        }else{
            totalStart = Time.millis();
        }
        last = Time.millis();
        lastName = name;
    }

    public static void end(){
        endi();
        long total = Time.timeSinceMillis(totalStart);

        times.each((name, time) -> {
            Log.info("[PERF] {0}: {1}ms ({2}%)", name, time, (int)((float)time / total * 100));
        });
        Log.info("[PERF] TOTAL: {0}ms", total);
    }

    private static void endi(){
        times.put(lastName, Time.timeSinceMillis(last));
        lastName = null;
    }
}
