package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTaskRegistry;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.AppCleanupService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 应用关联资源清理实现。
 * 清理范围（方案 B，不含 COS）：
 *   取消进行中的 AI 生成任务
 *   删除 code_output / code_versions / code_deploy 目录
 *   清除 Redis 对话记忆与 Caffeine AI 服务缓存
 * 任一步失败只记日志，不向外抛出，避免阻断应用软删。
 */
@Slf4j
@Service
public class AppCleanupServiceImpl implements AppCleanupService {

    @Resource
    private AiGenerationTaskRegistry aiGenerationTaskRegistry;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Override
    public void cleanupAppResources(App app) {
        if (app == null || app.getId() == null) {
            return;
        }
        Long appId = app.getId();
        log.info("开始清理应用关联资源，appId={}", appId);

        // 1. 先停生成，避免边写边删
        cancelRunningGeneration(app);

        // 2. 磁盘：生成目录 + 版本目录 + 部署目录
        cleanupCodeDirectories(appId, app.getCodeGenType());
        cleanupDeployDirectory(app.getDeployKey());

        // 3. Redis 对话记忆 + AI 服务实例缓存
        cleanupChatMemory(appId);
        invalidateAiServiceCache(appId, app.getCodeGenType());

        log.info("应用关联资源清理完成，appId={}", appId);
    }

    /**
     * 取消该应用下进行中的生成任务（按 appId 匹配所有用户）。
     */
    private void cancelRunningGeneration(App app) {
        try {
            int cancelled = aiGenerationTaskRegistry.cancelByAppId(app.getId());
            if (cancelled > 0) {
                log.info("已取消应用进行中的生成任务，appId={}, count={}", app.getId(), cancelled);
            }
        } catch (Exception e) {
            log.warn("取消应用生成任务失败，appId={}", app.getId(), e);
        }
    }

    /**
     * 删除代码输出目录与历史版本目录。
     * 若 codeGenType 已知则只删对应类型；未知则兜底尝试所有类型，避免漏删。
     */
    private void cleanupCodeDirectories(Long appId, String codeGenType) {
        CodeGenTypeEnum knownType = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (knownType != null) {
            deleteDirQuietly(buildOutputDir(knownType, appId));
            deleteDirQuietly(buildVersionDir(knownType, appId));
            return;
        }
        log.warn("应用 codeGenType 未知，按全部类型尝试清理磁盘，appId={}, type={}", appId, codeGenType);
        for (CodeGenTypeEnum type : CodeGenTypeEnum.values()) {
            deleteDirQuietly(buildOutputDir(type, appId));
            deleteDirQuietly(buildVersionDir(type, appId));
        }
    }

    /** 部署目录按 deployKey 命名，与 appId 无关 */
    private void cleanupDeployDirectory(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return;
        }
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey);
        deleteDirQuietly(deployDir);
    }

    private void cleanupChatMemory(Long appId) {
        try {
            redisChatMemoryStore.deleteMessages(appId);
            log.info("已清除 Redis 对话记忆，appId={}", appId);
        } catch (Exception e) {
            log.warn("清除 Redis 对话记忆失败，appId={}", appId, e);
        }
    }

    private void invalidateAiServiceCache(Long appId, String codeGenType) {
        try {
            aiCodeGeneratorServiceFactory.invalidateByAppId(appId, codeGenType);
        } catch (Exception e) {
            log.warn("失效 AI 服务缓存失败，appId={}", appId, e);
        }
    }

    private static File buildOutputDir(CodeGenTypeEnum type, Long appId) {
        String dirName = type.getValue() + "_" + appId;
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dirName);
    }

    private static File buildVersionDir(CodeGenTypeEnum type, Long appId) {
        String dirName = type.getValue() + "_" + appId;
        return new File(AppConstant.CODE_VERSION_ROOT_DIR + File.separator + dirName);
    }

    private void deleteDirQuietly(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try {
            boolean deleted = FileUtil.del(dir);
            if (deleted) {
                log.info("已删除目录：{}", dir.getAbsolutePath());
            } else {
                log.warn("删除目录未完全成功：{}", dir.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("删除目录失败：{}", dir.getAbsolutePath(), e);
        }
    }
}
