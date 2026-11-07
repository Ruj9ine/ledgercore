package com.ledgercore.admin;

import com.ledgercore.admin.dto.AdminActionRequest;
import com.ledgercore.admin.dto.AdminUserResponse;
import com.ledgercore.common.PageResponse;
import com.ledgercore.security.UserPrincipal;
import com.ledgercore.user.Role;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")   // a second lock behind the URL rule in SecurityConfig
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) String email,
                                                @RequestParam(required = false) Role role,
                                                @RequestParam(required = false) Boolean enabled,
                                                @RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size) {
        return adminUserService.list(UserFilter.of(email, role, enabled, page, size));
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    @PostMapping("/{id}/disable")
    public AdminUserResponse disable(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody AdminActionRequest request) {
        return adminUserService.disable(principal.getId(), id, request.reason());
    }

    @PostMapping("/{id}/enable")
    public AdminUserResponse enable(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody AdminActionRequest request) {
        return adminUserService.enable(principal.getId(), id, request.reason());
    }
}
