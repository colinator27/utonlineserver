package me.colinator27;

/*
 * Represents a key-value pair.
 * This removes the dependency on JavaFX
 *
 */
public class Pair<K, V> {

    private K key;
    private V value;

    public Pair(K key, V value) {
        this.value = value;
        this.key = key;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "[" + key + ", " + value + "]";
    }
}
