package org.apache.kafka.common.requests;

import org.apache.kafka.common.message.FetchResponseData;

public class FetchResponse extends AbstractResponse {

    private final FetchResponseData data;

    public FetchResponse(FetchResponseData data) {
        super(ApiKeys.FETCH);
        this.data = data;
    }

    @Override
    public FetchResponseData data() {
        return data;
    }
}
