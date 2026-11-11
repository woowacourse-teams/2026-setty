package setty.aisupport;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable JSON object retaining the declared key order, including inside model message strings. */
final class OrderedJson {
    private OrderedJson() {}

    @SuppressWarnings("unchecked")
    static <K, V> Map<K, V> of(Object... entries) {
        if (entries.length % 2 != 0) throw new IllegalArgumentException("Expected key/value pairs");
        Map<K, V> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            K key = (K) Objects.requireNonNull(entries[i]);
            V value = (V) Objects.requireNonNull(entries[i + 1]);
            if (result.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate key");
        }
        return Collections.unmodifiableMap(result);
    }
}
