package com.nylg.zziiaicodemother.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用版本信息
 */
@Data
public class AppVersionVO implements Serializable {

    /**
     * 版本标识（current 或时间戳）
     */
    private String versionKey;

    /**
     * 展示名称
     */
    private String versionName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前工作版本
     */
    private Boolean current;

    @Serial
    private static final long serialVersionUID = 1L;
}
