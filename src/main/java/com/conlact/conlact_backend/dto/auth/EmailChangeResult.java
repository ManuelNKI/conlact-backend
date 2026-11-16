package com.conlact.conlact_backend.dto.auth;

import com.conlact.conlact_backend.dto.auth.SessionAccount;
import com.conlact.conlact_backend.dto.user.StaffUserResponse;

public record EmailChangeResult(
        SessionAccount user,
        StaffUserResponse profile,
        String sessionToken
) {
}
