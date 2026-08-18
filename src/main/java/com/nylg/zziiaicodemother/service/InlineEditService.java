package com.nylg.zziiaicodemother.service;

import com.nylg.zziiaicodemother.model.dto.app.AppInlineEditRequest;
import com.nylg.zziiaicodemother.model.entity.User;

/**
 * 原生 HTML / 多文件模式行内编辑服务
 */
public interface InlineEditService {

    /**
     * 按 CSS 选择器更新目标元素的文本内容并写回磁盘
     *
     * @param request   编辑请求
     * @param loginUser 当前登录用户
     * @return 是否成功
     */
    Boolean inlineEdit(AppInlineEditRequest request, User loginUser);
}
