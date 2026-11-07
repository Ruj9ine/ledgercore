package com.ledgercore.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Every state-changing admin action must say why. The reason goes into the audit log. */
public record AdminActionRequest(@NotBlank @Size(max = 500) String reason) {
}
