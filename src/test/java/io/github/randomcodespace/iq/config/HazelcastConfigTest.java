package io.github.randomcodespace.iq.config;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Hazelcast configuration in both local and k8s modes.
 */
class HazelcastConfigTest {

    private HazelcastConfig createInstance(boolean k8sDiscovery, String k8sServiceDns) throws Exception {
        HazelcastConfig hazelcastConfig = new HazelcastConfig();
        setField(hazelcastConfig, "k8sDiscovery", k8sDiscovery);
        setField(hazelcastConfig, "k8sServiceDns", k8sServiceDns);
        return hazelcastConfig;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // --- Local profile tests ---

    @Test
    void localProfileShouldDisableMulticast() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        assertFalse(config.getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
        assertFalse(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
    }

    @Test
    void localProfileShouldSetClusterName() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        assertEquals("code-iq", config.getClusterName());
    }

    @Test
    void localProfileShouldSetInstanceName() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        assertEquals("code-iq-cache", config.getInstanceName());
    }

    // --- Cache map configs ---

    @Test
    void shouldConfigureGraphStatsCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("graph-stats");
        assertNotNull(mapConfig);
        assertEquals(600, mapConfig.getTimeToLiveSeconds());
    }

    @Test
    void shouldConfigureKindsListCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("kinds-list");
        assertNotNull(mapConfig);
        assertEquals(600, mapConfig.getTimeToLiveSeconds());
    }

    @Test
    void shouldConfigureKindNodesCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("kind-nodes");
        assertNotNull(mapConfig);
        assertEquals(300, mapConfig.getTimeToLiveSeconds());
    }

    @Test
    void shouldConfigureNodeDetailCacheWithNearCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("node-detail");
        assertNotNull(mapConfig);
        assertEquals(300, mapConfig.getTimeToLiveSeconds());
        assertNotNull(mapConfig.getNearCacheConfig());
        assertEquals("graph-nodes", mapConfig.getNearCacheConfig().getName());
    }

    @Test
    void shouldConfigureSearchResultsCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("search-results");
        assertNotNull(mapConfig);
        assertEquals(120, mapConfig.getTimeToLiveSeconds());
    }

    @Test
    void shouldConfigureImpactTraceCache() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(false, "");
        Config config = hazelcastConfig.hazelcastConfig();

        MapConfig mapConfig = config.getMapConfig("impact-trace");
        assertNotNull(mapConfig);
        assertEquals(300, mapConfig.getTimeToLiveSeconds());
    }

    // --- K8s profile tests ---

    @Test
    void k8sProfileShouldDisableMulticast() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(true, "code-iq-hazelcast.default.svc.cluster.local");
        Config config = hazelcastConfig.hazelcastConfig();

        assertFalse(config.getNetworkConfig().getJoin().getMulticastConfig().isEnabled());
    }

    @Test
    void k8sProfileShouldEnableTcpIpWithServiceDns() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(true, "code-iq-hazelcast.default.svc.cluster.local");
        Config config = hazelcastConfig.hazelcastConfig();

        assertTrue(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
        assertTrue(config.getNetworkConfig().getJoin().getTcpIpConfig().getMembers()
                .contains("code-iq-hazelcast.default.svc.cluster.local"));
    }

    @Test
    void k8sProfileShouldNotEnableTcpIpWithBlankDns() throws Exception {
        HazelcastConfig hazelcastConfig = createInstance(true, "");
        Config config = hazelcastConfig.hazelcastConfig();

        assertFalse(config.getNetworkConfig().getJoin().getTcpIpConfig().isEnabled());
    }

    // --- All modes should produce same cache maps ---

    @Test
    void bothModesShouldHaveSameCacheMaps() throws Exception {
        HazelcastConfig local = createInstance(false, "");
        HazelcastConfig k8s = createInstance(true, "svc.cluster.local");

        Config localConfig = local.hazelcastConfig();
        Config k8sConfig = k8s.hazelcastConfig();

        // Both should have the same set of explicitly configured maps
        for (String mapName : new String[]{"graph-stats", "kinds-list", "kind-nodes",
                "node-detail", "search-results", "impact-trace"}) {
            assertNotNull(localConfig.getMapConfig(mapName),
                    "Local config missing map: " + mapName);
            assertNotNull(k8sConfig.getMapConfig(mapName),
                    "K8s config missing map: " + mapName);
        }
    }
}
