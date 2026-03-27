package org.apache.kafka.common.requests;

import org.apache.kafka.common.message.FetchRequestData;
import org.apache.kafka.common.protocol.ByteBufferAccessor;

public class FetchRequest extends AbstractRequest {

    private final FetchRequestData data;

    public FetchRequest(FetchRequestData data, short version) {
        super(ApiKeys.FETCH, version);
        this.data = data;
    }

    public static class Builder extends AbstractRequest.Builder<FetchRequest> {
        private final FetchRequestData data;

        public Builder(FetchRequestData data) {
            super(ApiKeys.FETCH);
            this.data = data;
        }

        @Override
        public FetchRequest build(short version) {
            return new FetchRequest(data, version);
        }
    }

    @Override
    public FetchRequestData data() {
        return data;
    }
}
