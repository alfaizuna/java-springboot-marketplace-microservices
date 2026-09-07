package com.alfaizunawebid.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Marketplace Payment Webhook Service API")
                        .description("Payment Webhook, HMAC Verification, and Idempotency API Documentation")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Engineering Team")
                                .email("dev@alfaizunawebid.com")));
    }
}
