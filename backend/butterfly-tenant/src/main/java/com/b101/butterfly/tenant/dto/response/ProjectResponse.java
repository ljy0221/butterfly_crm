package com.b101.butterfly.tenant.dto.response;

import com.b101.butterfly.tenant.constant.ProjectPlan;
import com.b101.butterfly.tenant.entity.Project;

public record ProjectResponse(
        Long projectId,
        String name,
        String businessNo,
        String companyAuthNo,
        Long ownerId,
        boolean hasApiKey,
        ProjectPlan plan,
        Integer dailyLimit
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getBusinessNo(),
                project.getCompanyAuthNo(),
                project.getOwnerId(),
                project.getPublicApiKey() != null,
                project.getPlan(),
                project.getDailyLimit()
        );
    }
}
