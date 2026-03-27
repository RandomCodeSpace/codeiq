package org.example.serde;

import java.io.Closeable;

public interface Serializer<T> extends Closeable {
    byte[] serialize(String topic, T data);
    default void close() {}
}
