package com.testforge.ai.scenario;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StepDataContext {

    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        if (key == null || value == null) return;
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> snapshot() {
        return Map.copyOf(data);
    }

    public void clear() {
        data.clear();
    }
}
