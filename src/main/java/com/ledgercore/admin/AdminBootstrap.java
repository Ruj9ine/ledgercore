package com.ledgercore.admin;

import com.ledgercore.audit.AuditAction;
import com.ledgercore.audit.AuditService;
import com.ledgercore.audit.AuditTargetType;
import com.ledgercore.user.Role;
import com.ledgercore.user.User;
import com.ledgercore.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/** Creates the first admin account at startup, if one is configured and does not exist yet. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final int BCRYPT_MAX_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminBootstrap(AdminProperties properties,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          AuditService auditService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.email() == null ? "" : properties.email().trim().toLowerCase(Locale.ROOT);
        String password = properties.password() == null ? "" : properties.password();

        if (email.isEmpty() && password.isEmpty()) {
            log.info("No admin bootstrap configured");
            return;
        }
        // A half-configured admin is almost certainly a mistake, so the application refuses to start.
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("ADMIN_EMAIL and ADMIN_PASSWORD must be set together.");
        }
        if (!email.contains("@")) {
            throw new IllegalStateException("ADMIN_EMAIL is not a valid email address.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new IllegalStateException("ADMIN_PASSWORD must be at most " + BCRYPT_MAX_BYTES + " bytes.");
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN) {
                // Configuration must never be able to grant admin rights to an account that registered normally.
                log.warn("A non-admin user already has the configured admin email; it was NOT promoted.");
            } else {
                log.info("Admin account already exists");
            }
            return;
        }

        User admin = new User(email, passwordEncoder.encode(password), "Administrator", Role.ADMIN);
        userRepository.saveAndFlush(admin);
        auditService.record(null, AuditAction.ADMIN_BOOTSTRAPPED, AuditTargetType.USER, admin.getId(), null, null);
        log.info("Created admin account id={}", admin.getId());
    }
}
