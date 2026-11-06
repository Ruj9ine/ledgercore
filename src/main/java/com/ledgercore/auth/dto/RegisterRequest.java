package com.ledgercore.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // 72 is BCrypt's input limit in bytes; longer passwords would be silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String fullName) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=***, fullName=" + fullName + "]";
    }
}
