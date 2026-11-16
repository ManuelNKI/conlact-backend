package com.conlact.conlact_backend.dto.user;

import java.util.List;

public record UserListResponse(
        List<StaffUserResponse> users
) {}
