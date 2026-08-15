package com.nylg.zziiaicodemother.service;

import com.nylg.zziiaicodemother.model.entity.App;

/**
 * 应用关联资源清理服务。
 * 删除应用时清理磁盘文件、Redis 对话记忆、进行中的生成任务等（不含 COS 封面）。
 */
public interface AppCleanupService {

    /**
     * 清理应用关联的磁盘与运行时资源（best-effort，失败只打日志）。
     * 必须在软删 DB 之前调用，以便读取 codeGenType / deployKey / userId。
     *
     * @param app 尚未软删的应用实体
     */
    void cleanupAppResources(App app);
}
