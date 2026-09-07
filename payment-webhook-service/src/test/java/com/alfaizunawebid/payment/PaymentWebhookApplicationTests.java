package com.alfaizunawebid.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/payment_db_test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none",
    "payment.webhook.secret-key=test-secret-key"
})
class PaymentWebhookApplicationTests {

    @Test
    void contextLoads() {
    }

}
