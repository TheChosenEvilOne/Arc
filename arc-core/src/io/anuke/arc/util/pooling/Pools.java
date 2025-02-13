package io.anuke.arc.util.pooling;

import io.anuke.arc.collection.Array;
import io.anuke.arc.collection.ObjectMap;
import io.anuke.arc.function.Supplier;

/**
 * Stores a map of {@link Pool}s by type for convenient static access.
 * @author Nathan Sweet
 */
@SuppressWarnings("unchecked")
public class Pools{
    static private final ObjectMap<Class, Pool> typePools = new ObjectMap<>();

    private Pools(){
    }

    /**
     * Returns a new or existing pool for the specified type, stored in a Class to {@link Pool} map. Note that the max size is ignored for some reason.
     * if this is not the first time this pool has been requested.
     */
    public static <T> Pool<T> get(Class<T> type, Supplier<T> supplier, int max){
        Pool<T> pool = typePools.get(type);
        if(pool == null){
            pool = new Pool<T>(4, max){
                @Override
                protected T newObject(){
                    return supplier.get();
                }
            };
            typePools.put(type, pool);
        }
        return pool;
    }

    /**
     * Returns a new or existing pool for the specified type, stored in a Class to {@link Pool} map. The max size of the pool used
     * is 100.
     */
    public static <T> Pool<T> get(Class<T> type, Supplier<T> supplier){
        return get(type, supplier, 100);
    }

    /** Sets an existing pool for the specified type, stored in a Class to {@link Pool} map. */
    public static <T> void set(Class<T> type, Pool<T> pool){
        typePools.put(type, pool);
    }

    /** Obtains an object from the {@link #get(Class, Supplier) pool}. */
    public static synchronized <T> T obtain(Class<T> type, Supplier<T> supplier){
        return get(type, supplier).obtain();
    }

    /** Frees an object from the {@link #get(Class, Supplier) pool}. */
    public static synchronized void free(Object object){
        if(object == null) throw new IllegalArgumentException("Object cannot be null.");
        Pool pool = typePools.get(object.getClass());
        if(pool == null) return; // Ignore freeing an object that was never retained.
        pool.free(object);
    }

    /**
     * Frees the specified objects from the {@link #get(Class, Supplier) pool}. Null objects within the array are silently ignored. Objects
     * don't need to be from the same pool.
     */
    public static void freeAll(Array objects){
        freeAll(objects, false);
    }

    /**
     * Frees the specified objects from the {@link #get(Class, Supplier) pool}. Null objects within the array are silently ignored.
     * @param samePool If true, objects don't need to be from the same pool but the pool must be looked up for each object.
     */
    public static void freeAll(Array objects, boolean samePool){
        if(objects == null) throw new IllegalArgumentException("Objects cannot be null.");
        Pool pool = null;
        for(int i = 0, n = objects.size; i < n; i++){
            Object object = objects.get(i);
            if(object == null) continue;
            if(pool == null){
                pool = typePools.get(object.getClass());
                if(pool == null) continue; // Ignore freeing an object that was never retained.
            }
            pool.free(object);
            if(!samePool) pool = null;
        }
    }
}
