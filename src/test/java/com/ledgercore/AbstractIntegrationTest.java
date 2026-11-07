package com.ledgercore;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = "ledgercore.jwt.secret=integration-test-secret-integration-test-secret")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Every integration test starts from empty tables. CASCADE also clears tables added by later milestones. */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users, audit_log CASCADE");
    }

    /** Makes an existing user an admin directly in the database, which is how admins are created outside the bootstrap. */
    protected void promoteToAdmin(String email) {
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
    }
}