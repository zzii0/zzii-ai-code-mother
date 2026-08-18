package com.nylg.zziiaicodemother.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 原生 HTML / 多文件模式行内编辑请求
 */
@Data
public class AppInlineEditRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 目标文件，默认 index.html
     */
    private String file;

    /**
     * CSS 选择器
     */
    private String selector;

    /**
     * 编辑前的文本内容（用于并发校验）
     */
    private String oldContent;

    /**
     * 编辑后的文本内容
     */
    private String newContent;

    /**
     * 编辑后的 innerHTML，用于保留图标等子节点
     */
    private String innerHtml;

    @Serial
    private static final long serialVersionUID = 1L;
}
