package com.onextel.CallServiceApplication.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonUtil {

    private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Serializes an object of any type into its JSON representation.
     *
     * @param obj the object to serialize
     * @param <T> the type of the object
     * @return the JSON string or null if serialization fails
     */
    public static <T> String serialize(T obj) throws JsonProcessingException {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object: {}", obj, e);
            throw e;
        }
    }

    /**
     * Deserializes a JSON string into an object of the specified type.
     *
     * @param json the JSON string
     * @param clazz the target class to deserialize into
     * @param <T> the type of the target object
     * @return an instance of T or null if deserialization fails
     */
    public static <T> T deserialize(String json, Class<T> clazz) throws JsonProcessingException {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize JSON: {}", json, e);
            throw e;
        }
    }
}
