package com.scaffold.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 将 true/false、1/0、"true"/"1" 统一反序列化为 Integer 标志位 */
public class FlagDeserializer extends JsonDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        return switch (parser.currentToken()) {
            case VALUE_NULL -> null;
            case VALUE_TRUE -> 1;
            case VALUE_FALSE -> 0;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getIntValue() != 0 ? 1 : 0;
            case VALUE_STRING -> {
                String text = parser.getText().trim();
                if (text.isEmpty()) {
                    yield null;
                }
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
                    yield 1;
                }
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
                    yield 0;
                }
                yield null;
            }
            default -> null;
        };
    }
}
