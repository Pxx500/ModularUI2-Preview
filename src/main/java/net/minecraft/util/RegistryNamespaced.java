package net.minecraft.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class RegistryNamespaced implements Iterable<Object> {

    protected final Map<Object, Object> registryObjects = new HashMap<>();
    private final Map<Object, String> names = new IdentityHashMap<>();
    protected final ObjectIntIdentityMap underlyingIntegerMap = new ObjectIntIdentityMap();

    public Object getObject(Object key) {
        return getObject((String) key);
    }

    public void putObject(Object key, Object value) {
        Object previous = registryObjects.put(key, value);
        if (previous != null) {
            names.remove(previous);
        }
        names.put(value, String.valueOf(key));
    }

    public Set<Object> getKeys() {
        return Collections.unmodifiableSet(registryObjects.keySet());
    }

    public Object getObject(String key) {
        return registryObjects.get(ensureNamespaced(key));
    }

    public void addObject(int id, String key, Object value) {
        underlyingIntegerMap.func_148746_a(value, id);
        putObject(ensureNamespaced(key), value);
    }

    public String getNameForObject(Object value) {
        return names.get(value);
    }

    public boolean containsKey(String key) {
        return registryObjects.containsKey(ensureNamespaced(key));
    }

    public boolean containsKey(Object key) {
        return containsKey((String) key);
    }

    public boolean containsId(int id) {
        return underlyingIntegerMap.func_148744_b(id);
    }

    public int getIDForObject(Object value) {
        return underlyingIntegerMap.func_148747_b(value);
    }

    public Object getObjectById(int id) {
        return underlyingIntegerMap.func_148745_a(id);
    }

    @Override
    public java.util.Iterator<Object> iterator() {
        return underlyingIntegerMap.iterator();
    }

    protected static String ensureNamespaced(String key) {
        return key.indexOf(':') < 0 ? "minecraft:" + key : key;
    }
}
