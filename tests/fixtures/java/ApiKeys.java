package org.example.protocol;

public enum ApiKeys {
    PRODUCE(0),
    FETCH(1),
    LIST_OFFSETS(2);

    private final int id;

    ApiKeys(int id) {
        this.id = id;
    }

    public int getId() { return id; }
}
