package com.college.selection.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 两件事：
 * 1. LocalDateTime 统一输出 "2026-11-02 09:00:00"，前端不用再格式化
 * 2. null 字段不输出（Lab.mentors、Stage.remainingSeconds 这些按需出现）
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .serializers(new LocalDateTimeSerializer(FMT))
                .deserializers(new LocalDateTimeDeserializer(FMT));
    }
}
