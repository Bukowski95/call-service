package com.onextel.CallServiceApplication.service.redis;

/**
 *
 */

/**
 * Centralized Redis key and configuration management
 * --------------------------------------------------------------------------------------------------
 * Key Pattern	                        Type        Purpose	                            TTL
 * --------------------------------------------------------------------------------------------------
 * Call Service Instance Management
 * ***************************************************************************************************
 * cluster:registry	                    Set	        All registered instance IDs	        Persistent
 * cluster:heartbeats	                ZSet	    InstanceID → timestamp	            180s (auto-refreshed)
 * cluster:instance:{id}:metadata	    Hash	    Instance details	                86400s (24h)
 * ***************************************************************************************************
 *  Call Management
 * ***************************************************************************************************
 * cluster:instance:{id}:calls	        Set	        Call UUIDs owned by instance	    Removed when instance dies
 * cluster:call:jsondoc:{uuid}	        JSON	    Call data                           86400s (24h)
 * ***************************************************************************************************
 *  Call Metrics
 * ***************************************************************************************************
 * cluster:call:state:{uuid}	        String	    Current call state	                24h
 * cluster:call:history:{uuid}	        List	    State transition history	        24h
 * cluster:calls:active	                ZSet	    Active calls by timestamp	        Persistent
 * cluster:campaign:{id}:{instance}	    Hash	    Per-instance campaign stats	        24h
 * cluster:campaign:{id}:total	        Hash	    Aggregate campaign stats	        24h
 * cluster:stats:standalone	            Hash	    Non-campaign call stats	            24h
 * cluster:calls:global                 ZSet        All calls by timestamp              Persistent
 * cluster:calls:orphaned               ZSet        Orphan call ids                     24h
 */

public final class RedisKeys {
    // Private constructor to prevent instantiation
    private RedisKeys() {}

    /**
     * ================== CALL KEYS ==================
     */

    public static final String CALL_KEY_PREFIX = "cluster:call:jsondoc:";
    public static String callKey(String callUuid) {
        return CALL_KEY_PREFIX + callUuid;
    }

    public static final String CHANNEL_MAPPING_PREFIX = "cluster:channel:";
    public static String channelMappingKey(String channelUuid) {
        return CHANNEL_MAPPING_PREFIX + channelUuid;
    }

    public static final String INSTANCE_CALLS_PREFIX = "cluster:instance:%s:calls";
    public static String instanceCallsKey(String instanceId) {
        return String.format(INSTANCE_CALLS_PREFIX, instanceId);
    }

    public static final String GLOBAL_CALLS_KEY = "cluster:calls:global";
    public static final String ORPHANED_CALLS_ZSET = "cluster:calls:orphaned";

    /**
     * ================== APPLICATION INSTANCE KEYS ==================
     */
    public static final String INSTANCE_REGISTRY_KEY = "cluster:registry";

    public static final String INSTANCE_METADATA_PREFIX = "cluster:instance:%s:metadata";
    public static String instanceMetadataKey(String instanceId) {
        return String.format(INSTANCE_METADATA_PREFIX, instanceId);
    }

    public static final String HEARTBEAT_KEY = "cluster:heartbeats";

    /**
     * ================== APPLICATION INSTANCE METADATA FIELDS ==================
     */
    public static final class InstanceMetadata {
        public static final String ID = "id";
        public static final String HOST = "host";
        public static final String PORT = "port";
        public static final String VERSION = "version";
        public static final String LAST_SEEN = "lastSeen";
        public static final String STATUS = "status";
        public static final String REMOVED_AT = "removedAt";

        public static final String STATUS_ACTIVE = "ACTIVE";
        public static final String STATUS_DOWN = "DOWN";
        public static final String STATUS_WARNING = "WARNING";
    }

    /**
     * ================== CALL STATE & STATISTICS KEYS ==================
     */

    public static final String GLOBAL_STATS_KEY = "cluster:stats:global";  // calls stats for all instances
    public static final String STANDALONE_STATS_KEY = "cluster:stats:standalone"; // non-campaign calls stats

    public static final String CALL_STATE_PREFIX = "cluster:call:state:";
    public static String callStateKey(String callUuid) {
        return CALL_STATE_PREFIX + callUuid;
    }

    public static final String STATE_HISTORY_PREFIX = "cluster:call:history:";
    public static String stateHistoryKey(String callUuid) {
        return STATE_HISTORY_PREFIX + callUuid;
    }

    public static final String ACTIVE_CALLS_ZSET = "cluster:calls:active";

    public static final String CAMPAIGN_PREFIX = "cluster:campaign:";
    public static String campaignKey(String campaignId) {
        return CAMPAIGN_PREFIX + campaignId;
    }

    /**
     * ================== CALL STATE FIELDS ==================
     */
    public static final class CallFields {
        public static final String IS_ORPHAN = "isOrphan";
        public static final String ORIGINAL_INSTANCE = "originalInstance";
        public static final String ORPHANED_AT = "orphanedAt";
        public static final String LAST_UPDATE = "lastUpdateTimestamp";
        public static final String CURRENT_INSTANCE = "currentInstance";
    }

    /**
     * ================== LOCK KEYS ==================
     */
    public static final String CALL_RECOVERY_LOCK_PREFIX = "cluster:lock:call_recovery:";
    public static String callRecoveryLockKey(String callUuid) {
        return CALL_RECOVERY_LOCK_PREFIX + callUuid;
    }

    public static final String ORPHAN_CLEANUP_LOCK = "cluster:lock:orphan_cleanup";


    /**
     * ================== TIME CONSTANTS ==================
     */
    public static final class TTL {
        // Call retention
        public static final int CALL_SECONDS = 86400; // 24 hours
        public static final int ORPHANED_CALL_SECONDS = 86400; // 24 hours

        // Instance tracking
        public static final int INSTANCE_SECONDS = 90; // 1.5 minutes
        public static final int METADATA_RETENTION_SECONDS = 86400; // 24 hours

        // Call State tracking
        public static final int STATE_SECONDS = 86400; // 24 hours

        // Lock durations
        public static final int RECOVERY_LOCK_SECONDS = 30;
        public static final int CLEANUP_LOCK_SECONDS = 60;
    }

    /**
     * ================== INTERVALS ==================
     */
    public static final class Intervals {
        // Heartbeat timing
        public static final int HEARTBEAT_MS = 30000; // 30 seconds

        // Orphan management
        public static final int ORPHAN_CHECK_MS = 60000; // 15 minutes (900000 ms) 1 min (60000 ms)
        public static final int ORPHAN_MARKING_TIMEOUT_SECONDS = 15;
        public static final int BATCH_TIMEOUT_SECONDS = 5; // 5 seconds

        // Recovery settings
        public static final int RECOVERY_RETRY_MS = 5000; // 5 seconds

        // Cleanup settings
        public static final int CLEANUP_BATCH_SIZE = 100;
        public static final int CLEANUP_PAUSE_MS = 50;
    }

    /**
     * ================== THRESHOLDS ==================
     */
    public static final class Thresholds {
        public static final double HIGH_ORPHAN_RATE = 0.05; // 5%
        public static final int MAX_ORPHAN_RECOVERY_ATTEMPTS = 3;
        public static final int DEAD_INSTANCE_GRACE_PERIOD = 2; // multiplier
    }

    /**
     * ================== METRIC KEYS ==================
     */
    public static final class Metrics {
        public static final String ORPHANED_CALLS = "cluster.metrics.orphaned.count";
        public static final String RECOVERY_ATTEMPTS = "cluster.metrics.recovery.attempts";
        public static final String RECOVERY_FAILURES = "cluster.metrics.recovery.failures";
        public static final String ACTIVE_INSTANCES = "cluster.metrics.instances.active";
    }
}
