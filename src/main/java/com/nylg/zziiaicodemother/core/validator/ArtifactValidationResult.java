package com.nylg.zziiaicodemother.core.validator;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * HTML / 多文件产物的校验结果。
 */
@Data
@Builder
public class ArtifactValidationResult {

    private boolean valid;

    @Builder.Default
    private List<ArtifactIssue> issues = new ArrayList<>();

    public static ArtifactValidationResult ok() {
        return ArtifactValidationResult.builder().valid(true).build();
    }

    public static ArtifactValidationResult fail(List<ArtifactIssue> issues) {
        return ArtifactValidationResult.builder()
                .valid(false)
                .issues(issues == null ? new ArrayList<>() : issues)
                .build();
    }

    public ArtifactIssue firstIssue() {
        return issues.isEmpty() ? null : issues.get(0);
    }
}
