package com.livehouse.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * JSON 序列化配置
 */
@Configuration
public class JacksonConfig {

    /**
     * 将订单ID序列化成字符串，防止前端解析时的精度丢失产生前后端id号显示不一致问题。
     * @return
     */
    private static final String PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(PATTERN);
        DateTimeFormatter df = DateTimeFormatter.ofPattern(DATE_PATTERN);
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance)
                .serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dtf))
                .deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dtf))
                .serializerByType(LocalDate.class, new LocalDateSerializer(df))
                .deserializerByType(LocalDate.class, new LocalDateDeserializer(df));
    }
}
