package com.b101.butterfly.tenant.dto.request;

import com.b101.butterfly.auth.constant.RoleType;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull RoleType roleType) {
}
