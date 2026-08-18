package com.nylg.zziiaicodemother.core.builder;

import lombok.Builder;
import lombok.Data;

/**
 * Vue 项目构建结果，携带失败时的定位信息供自动修复使用。
 */
@Data
@Builder
public class BuildResult {

    private boolean success;

    /** 合并后的 stdout/stderr */
    private String output;

    private int exitCode;

    /** 相对项目根目录的出错文件，如 src/components/NavBar.vue */
    private String errorFile;

    private Integer errorLine;

    private String errorMessage;

    public static BuildResult ok(String output) {
        return BuildResult.builder()
                .success(true)
                .output(output)
                .exitCode(0)
                .build();
    }

    public static BuildResult fail(int exitCode, String output, String errorFile, Integer errorLine, String errorMessage) {
        return BuildResult.builder()
                .success(false)
                .exitCode(exitCode)
                .output(output)
                .errorFile(errorFile)
                .errorLine(errorLine)
                .errorMessage(errorMessage)
                .build();
    }
}
