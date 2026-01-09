package com.github.mcpjavafx.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for creating pre-configured ObjectMapper instances.
 * Centralizes JSON mapper configuration to avoid duplication.
 */
public final class JsonMapperFactory {

    private JsonMapperFactory() {
        // Utility class
    }

    /**
     * Creates a default ObjectMapper configured for MCP JavaFX Debug.
     * 
     * <p>
     * Configuration:
     * <ul>
     * <li>JavaTimeModule registered for Java 8 date/time support</li>
     * <li>Null values excluded from serialization</li>
     * <li>Dates serialized as ISO strings, not timestamps</li>
     * <li>Unknown properties ignored during deserialization</li>
     * </ul>
     *
     * @return a configured ObjectMapper instance
     */
    public static ObjectMapper createDefault() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
