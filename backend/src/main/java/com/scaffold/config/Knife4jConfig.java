package com.scaffold.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智购商城 · AI 智能电商平台 API")
                        .description("基于 Spring Boot 3 + Spring AI 的多角色（用户 / 商家 / 管理员）电商系统接口文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("邵磊")
                                .email("3236482411@qq.com"))
                        .license(new License().name("Apache 2.0")));
    }
}
