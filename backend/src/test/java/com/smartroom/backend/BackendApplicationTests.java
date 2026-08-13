package com.smartroom.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the wiring holds together and the H2 schema is created. */
@SpringBootTest
@ActiveProfiles("h2")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
