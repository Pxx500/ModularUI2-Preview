package net.minecraft.util;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ObjectIntIdentityMap implements Iterable<Object> {

    private final Map<Object, Integer> ids = new IdentityHashMap<>();
    private final List<Object> values = new ArrayList<>();

    public void func_148746_a(Object value, int id) {
        ids.put(value, id);
        while (values.size() <= id) {
            values.add(null);
        }
        values.set(id, value);
    }

    public int func_148747_b(Object value) {
        return ids.getOrDefault(value, -1);
    }

    public Object func_148745_a(int id) {
        return id >= 0 && id < values.size() ? values.get(id) : null;
    }

    public boolean func_148744_b(int id) {
        return func_148745_a(id) != null;
    }

    @Override
    public Iterator<Object> iterator() {
        return values.stream()
            .filter(value -> value != null)
            .iterator();
    }
}
