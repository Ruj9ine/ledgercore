package com.ledgercore.auth.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {

    @Override
    public String toString() {
        return "AuthResponse[accessToken=***, tokenType=" + tokenType + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
