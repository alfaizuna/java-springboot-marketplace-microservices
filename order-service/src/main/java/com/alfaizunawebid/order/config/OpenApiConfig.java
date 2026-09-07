package com.alfaizunawebid.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orderServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketplace Order Service API")
                        .description("Order Management Service API Documentation")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .email("dev@alfaizunawebid.com")));
    }
}
