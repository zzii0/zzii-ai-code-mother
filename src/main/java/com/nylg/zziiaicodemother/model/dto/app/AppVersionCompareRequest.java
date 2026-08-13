package com.nylg.zziiaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用版本对比请求
 */
@Data
public class AppVersionCompareRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 旧版本标识（current 表示当前版本）
     */
    private String oldVersionKey;

    /**
     * 新版本标识（current 表示当前版本）
     */
    private String newVersionKey;

    /**
     * 对比文件相对路径
     */
    private String filePath;

    @Serial
    private static final long serialVersionUID = 1L;
}
