package org.apache.kafka.clients.consumer;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.common.config.ConfigDef.Importance;

public class ConsumerConfig extends AbstractConfig {

    public static final String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";
    public static final String GROUP_ID_CONFIG = "group.id";
    public static final String AUTO_OFFSET_RESET_CONFIG = "auto.offset.reset";

    private static final ConfigDef CONFIG = new ConfigDef()
        .define(BOOTSTRAP_SERVERS_CONFIG, Type.LIST, Importance.HIGH,
                "A list of host/port pairs")
        .define("group.id", Type.STRING, "", Importance.HIGH,
                "A unique string that identifies the consumer group")
        .define("auto.offset.reset", Type.STRING, "latest", Importance.MEDIUM,
                "What to do when there is no initial offset");

    public ConsumerConfig(Map<String, Object> props) {
        super(CONFIG, props);
    }
}
