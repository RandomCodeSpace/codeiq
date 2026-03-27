package org.example.serde;

public class StringSerializer implements Serializer<String> {
    @Override
    public byte[] serialize(String topic, String data) {
        return data != null ? data.getBytes() : null;
    }
}
