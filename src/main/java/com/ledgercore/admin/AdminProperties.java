package com.ledgercore.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Optional credentials for the first admin account. Both stay empty unless an operator sets them. */
@ConfigurationProperties(prefix = "ledgercore.admin")
public record AdminProperties(String email, String password) {

    @Override
    public String toString() {
        return "AdminProperties[email=" + email + ", password=***]";
    }
}
