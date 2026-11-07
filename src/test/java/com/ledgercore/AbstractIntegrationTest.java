package com.ledgercore;

import com.ledgercore.reconciliation.ReconciliationReport;
import com.ledgercore.reconciliation.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ledgercore.jwt.secret=integration-test-secret-integration-test-secret")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReconciliationService reconciliationService;

    /** Every integration test starts from empty tables. CASCADE also clears tables added by later milestones. */
    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE users, audit_log CASCADE");
    }

    /** Makes an existing user an admin directly in the database, which is how admins are created outside the bootstrap. */
    protected void promoteToAdmin(String email) {
        jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", email);
    }

    /** Fails the test, printing the full report, unless every reconciliation check passes. */
    protected void assertBooksBalance() {
        ReconciliationReport report = reconciliationService.run();
        assertThat(report.balanced()).as("reconciliation report: %s", report).isTrue();
    }
}