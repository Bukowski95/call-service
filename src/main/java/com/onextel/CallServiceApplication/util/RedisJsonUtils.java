package com.onextel.CallServiceApplication.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onextel.CallServiceApplication.model.CallState;
import com.onextel.CallServiceApplication.model.Channel;
import com.onextel.CallServiceApplication.model.ChannelState;
import com.onextel.CallServiceApplication.model.DTMFEvent;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.IntegerOutput;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.CommandArgs;
import io.lettuce.core.protocol.CommandType;

import java.time.Instant;
import java.util.Map;

/**
 * 1. Updating a single call state
 *  RedisFuture<String> future = RedisJsonUtils.updateCallState(
 *     asyncCommands,
 *     "call:" + callUuid,
 *     CallState.ACTIVE
 *  );
 * 2. Updating multiple call fields at once
 *  Map<String, Object> updates = new ConcurrentHashMap<>();
 *      updates.put("currentState", CallState.ACTIVE.name());
 *      updates.put("lastUpdateTimestamp", Instant.now());
 *      updates.put("customVariables.status", "premium");
 *  RedisFuture<String> future = RedisJsonUtils.updateMultipleCallFields(
 *     asyncCommands,
 *     "call:" + callUuid,
 *     updates
 *  );
 * 3. Updating a channel variable
 *  RedisFuture<String> future = RedisJsonUtils.updateChannelVariable(
 *     asyncCommands,
 *     "channel:" + channelUuid,
 *     "call_direction",
 *     "outbound"
 *  );
 */

public class RedisJsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Generic partial update for any field
    public static <T> RedisFuture<String> partialUpdate(
            RedisAsyncCommands<String, String> async,
            String key,
            String fieldPath,
            T newValue) {
        try {
            String jsonValue = objectMapper.writeValueAsString(newValue);
            return async.dispatch(
                    CommandType.JSON_SET,
                    new StatusOutput<>(StringCodec.UTF8),
                    new CommandArgs<>(StringCodec.UTF8)
                            .add(key)
                            .add(fieldPath)
                            .add(jsonValue));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize value for partial update", e);
        }
    }

    // Call-specific partial updates
    public static RedisFuture<String> updateCallState(
            RedisAsyncCommands<String, String> async,
            String callKey,
            CallState newState) {
        return partialUpdate(async, callKey, "$.currentState", newState.name());
    }

    public static RedisFuture<String> updateCallTimestamp(
            RedisAsyncCommands<String, String> async,
            String callKey,
            String timestampField,
            Instant newTimestamp) {
        return partialUpdate(async, callKey, "$." + timestampField, newTimestamp);
    }

    public static RedisFuture<String> updateCallStateWithTimestamp(
            RedisAsyncCommands<String, String> async,
            String callKey,
            CallState newState) {
        return updateMultipleCallFields(async, callKey, Map.of(
                "currentState", newState.name(),
                "lastUpdateTimestamp", Instant.now().toString()
        ));
    }

    public static RedisFuture<String> updateCallVariable(
            RedisAsyncCommands<String, String> async,
            String callKey,
            String variableName,
            String variableValue) {
        return partialUpdate(async, callKey, "$.customVariables." + variableName, variableValue);
    }

    // Channel-specific partial updates
    public static RedisFuture<String> updateChannelState(
            RedisAsyncCommands<String, String> async,
            String channelKey,
            ChannelState newState) {
        return partialUpdate(async, channelKey, "$.state", newState.name());
    }

    public static RedisFuture<String> addChannelToCall(
            RedisAsyncCommands<String, String> async,
            String callKey,
            String channelUuid,
            Channel channel) throws JsonProcessingException {
        return partialUpdate(async, callKey, "$.channels." + channelUuid, channel);
    }

    public static RedisFuture<String> updateChannelVariable(
            RedisAsyncCommands<String, String> async,
            String channelKey,
            String variableName,
            String variableValue) {
        return partialUpdate(async, channelKey, "$.variables." + variableName, variableValue);
    }

    // Batch updates
    public static RedisFuture<String> updateMultipleCallFields(
            RedisAsyncCommands<String, String> async,
            String callKey,
            Map<String, Object> fieldUpdates) {
        CommandArgs<String, String> args = new CommandArgs<>(StringCodec.UTF8)
                .add(callKey);

        fieldUpdates.forEach((path, value) -> {
            try {
                args.add("$." + path)
                        .add(objectMapper.writeValueAsString(value));
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize value for path: " + path, e);
            }
        });

        return async.dispatch(
                CommandType.JSON_MSET,
                new StatusOutput<>(StringCodec.UTF8),
                args);
    }

    // DTMF-specific updates
    public static RedisFuture<Long> appendDTMFEvent(
            RedisAsyncCommands<String, String> async,
            String callKey,
            DTMFEvent dtmfEvent) throws JsonProcessingException {
        String jsonValue = objectMapper.writeValueAsString(dtmfEvent);
        return async.dispatch(
                CommandType.JSON_ARRAPPEND,
                new IntegerOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(callKey)
                        .add("$.dtmfHistory")
                        .add(jsonValue));
    }

    public static RedisFuture<String> clearDtmfHistory(
            RedisAsyncCommands<String, String> async,
            String callKey) {
        return async.dispatch(
                CommandType.JSON_SET,
                new StatusOutput<>(StringCodec.UTF8),
                new CommandArgs<>(StringCodec.UTF8)
                        .add(callKey)
                        .add("$.dtmfHistory")
                        .add("[]")); // Empty array
    }
}