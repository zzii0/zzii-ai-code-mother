package com.nylg.zziiaicodemother.core.validator;

import lombok.Builder;
import lombok.Data;

/**
 * 单个产物文件的结构化校验问题。
 */
@Data
@Builder
public class ArtifactIssue {

    /** 文件名，如 index.html / style.css / script.js */
    private String fileName;

    private String message;
}
