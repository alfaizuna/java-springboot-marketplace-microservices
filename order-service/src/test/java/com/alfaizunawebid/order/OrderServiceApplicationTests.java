package com.alfaizunawebid.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/order_db_test",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=none"
})
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
