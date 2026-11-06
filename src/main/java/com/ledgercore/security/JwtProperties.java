package com.ledgercore.security;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ledgercore.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @Min(1) @DefaultValue("60") long expirationMinutes) {

    @Override
    public String toString() {
        return "JwtProperties[secret=***, expirationMinutes=" + expirationMinutes + "]";
    }
}
