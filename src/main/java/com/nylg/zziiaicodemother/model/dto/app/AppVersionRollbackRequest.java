package com.nylg.zziiaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 应用版本回退请求
 */
@Data
public class AppVersionRollbackRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 要回退到的历史版本标识
     */
    private String versionKey;

    @Serial
    private static final long serialVersionUID = 1L;
}
