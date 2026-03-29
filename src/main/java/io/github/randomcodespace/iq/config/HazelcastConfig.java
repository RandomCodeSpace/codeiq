package io.github.randomcodespace.iq.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MaxSizePolicy;
import com.hazelcast.config.NearCacheConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Hazelcast cache configuration, active only on the "serving" profile.
 *
 * Configures near-cache for hot data and optionally enables Kubernetes pod
 * discovery when {@code codeiq.hazelcast.k8s-discovery} is set to {@code true}.
 */
@Configuration
@Profile("serving")
public class HazelcastConfig {

    @Value("${codeiq.hazelcast.k8s-discovery:false}")
    private boolean k8sDiscovery;

    @Value("${codeiq.hazelcast.k8s-service-dns:}")
    private String k8sServiceDns;

    @Bean
    Config hazelcastConfig() {
        var config = new Config();
        config.setInstanceName("code-iq-cache");
        config.setClusterName("code-iq");

        // Near-cache for hot graph data — reduces latency for repeated reads
        var nearCacheConfig = new NearCacheConfig()
                .setName("graph-nodes")
                .setTimeToLiveSeconds(300)
                .setMaxIdleSeconds(120)
                .setEvictionConfig(
                        new EvictionConfig()
                                .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT)
                                .setSize(10_000)
                                .setEvictionPolicy(EvictionPolicy.LRU)
                );

        // Map config for graph node cache
        var graphNodeMapConfig = new MapConfig("graph-nodes")
                .setTimeToLiveSeconds(600)
                .setEvictionConfig(
                        new EvictionConfig()
                                .setMaxSizePolicy(MaxSizePolicy.FREE_HEAP_PERCENTAGE)
                                .setSize(25)
                                .setEvictionPolicy(EvictionPolicy.LRU)
                )
                .setNearCacheConfig(nearCacheConfig);

        config.addMapConfig(graphNodeMapConfig);

        // Map config for search results
        var searchMapConfig = new MapConfig("search-results")
                .setTimeToLiveSeconds(120)
                .setEvictionConfig(
                        new EvictionConfig()
                                .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT)
                                .setSize(1_000)
                                .setEvictionPolicy(EvictionPolicy.LRU)
                );

        config.addMapConfig(searchMapConfig);

        // K8s pod discovery — when running in Kubernetes, use DNS-based discovery
        if (k8sDiscovery) {
            var networkConfig = config.getNetworkConfig();
            var joinConfig = networkConfig.getJoin();
            joinConfig.getMulticastConfig().setEnabled(false);
            joinConfig.getTcpIpConfig().setEnabled(false);

            // Use Hazelcast Kubernetes plugin via DNS lookup
            // Requires the hazelcast-kubernetes plugin on the classpath
            if (k8sServiceDns != null && !k8sServiceDns.isBlank()) {
                joinConfig.getTcpIpConfig().setEnabled(true);
                joinConfig.getTcpIpConfig().addMember(k8sServiceDns);
            }
        }

        return config;
    }
}
