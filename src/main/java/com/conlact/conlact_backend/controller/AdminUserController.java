package com.conlact.conlact_backend.controller;

import com.conlact.conlact_backend.dto.user.CreateUserRequest;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;
import com.conlact.conlact_backend.dto.user.UpdateUserRequest;
import com.conlact.conlact_backend.dto.user.UserListResponse;
import com.conlact.conlact_backend.security.UserPrincipal;
import com.conlact.conlact_backend.service.UserManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/admin/usuarios", "/api/admin/users"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserManagementService userManagementService;

    @GetMapping
    public ResponseEntity<UserListResponse> listUsers() {
        return ResponseEntity.ok(userManagementService.listUsers());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@Valid @RequestBody CreateUserRequest request) {
        StaffUserResponse user = userManagementService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "user", user
        ));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable UUID id,
            @RequestBody UpdateUserRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        StaffUserResponse user = userManagementService.updateUser(id, request, actorId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", user
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID actorId = principal != null ? principal.getId() : null;
        userManagementService.deleteUser(id, actorId);
        return ResponseEntity.ok(Map.of(
                "success", true
        ));
    }
}
