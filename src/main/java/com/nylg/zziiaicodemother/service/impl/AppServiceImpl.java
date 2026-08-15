package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.core.AiCodeGeneratorFacade;
import com.nylg.zziiaicodemother.core.builder.VueProjectBuilder;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTaskRegistry;
import com.nylg.zziiaicodemother.core.handler.StreamHandlerExecutor;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import com.nylg.zziiaicodemother.model.dto.app.AppQueryRequest;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.mapper.AppMapper;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.model.vo.AppVO;
import com.nylg.zziiaicodemother.model.vo.UserVO;
import com.nylg.zziiaicodemother.service.AppCleanupService;
import com.nylg.zziiaicodemother.service.AppService;
import com.nylg.zziiaicodemother.service.AppVersionService;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import com.nylg.zziiaicodemother.service.ScreenshotService;
import com.nylg.zziiaicodemother.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *  服务层实现。
 *
 * @author zzii
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    @Resource
    VueProjectBuilder vueProjectBuilder;

    @Resource
    private ScreenshotService screenshotService;

    @Resource
    private AppVersionService appVersionService;

    @Resource
    private AiGenerationTaskRegistry aiGenerationTaskRegistry;

    @Resource
    private AppCleanupService appCleanupService;

    /**
     * 调用AI生成代码
     * @param appId 应用ID
     * @param userMessage 用户消息
     * @param loginUser 登录用户
     * @return AI生成的代码
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String userMessage, User loginUser) {
        //1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(userMessage == null || userMessage.isEmpty(), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        //2.获取应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.验证用户权限，只有应用的创建者才能和应用对话
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问应用");
        //4.获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "代码生成类型错误");
        //4.1 生成前归档当前代码，支持后续版本对比与回退（HTML/多文件/Vue 统一入口）
        appVersionService.archiveCurrentVersion(appId, codeGenTypeEnum);
        //5.在调用AI前，把用户消息保存到对话历史中
        boolean chatMessage = chatHistoryService.addChatMessage(appId, userMessage, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        ThrowUtils.throwIf(!chatMessage, ErrorCode.SYSTEM_ERROR, "用户消息保存失败");
        //6. 注册生成任务，用于支持 POST /app/chat/gen/stop 手动停止
        AiGenerationTask generationTask = aiGenerationTaskRegistry.register(appId, loginUser.getId());
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                userMessage, codeGenTypeEnum, appId, generationTask);
        //7. 收集 AI 响应并写入对话历史；绑定 SSE 订阅供停止时 cancel
        return streamHandlerExecutor.doExecute(contentFlux, chatHistoryService, appId, loginUser, codeGenTypeEnum, generationTask)
                .doOnSubscribe(generationTask::bindSubscription)
                // 正常结束或取消后，从注册表移除，避免内存泄漏
                .doFinally(signal -> aiGenerationTaskRegistry.remove(appId, loginUser.getId()));
    }

    /**
     * 停止当前用户在某应用下进行中的 AI 生成。
     * 仅应用创建者可调用；实际中断逻辑在 AiGenerationTaskRegistry.cancel。
     */
    @Override
    public boolean stopChatGeneration(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限停止该应用的生成");
        return aiGenerationTaskRegistry.cancel(appId, loginUser.getId());
    }

    /**
     * 部署应用
     * @param appId 应用ID
     * @param loginUser 登录用户
     * @return 应用部署地址
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        //1。参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        //2.获取应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.验证用户权限，只有应用的创建者才能部署应用
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限部署应用");
        //4.检查是否有deployKey, 如果没有则生成6位的deployKey
        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        //5.获取代码生成类型，构建应用生成目录路径
        String codeGenType = app.getCodeGenType();
        String dieName = codeGenType + "_" + appId;
        String dirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dieName;
        //6.检查应用生成目录是否存在
        File sourcesDir = new File(dirPath);
        if (!sourcesDir.exists() || !sourcesDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用生成目录不存在");
        }
        //7.vue项目特殊处理：等待预览构建完成，确保 dist 与源码一致后再部署
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean projectResult = vueProjectBuilder.ensureBuiltForDeploy(dirPath);
            ThrowUtils.throwIf(!projectResult, ErrorCode.SYSTEM_ERROR, "vue项目构建失败，请重试!");
            File distDir = new File(dirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "dist目录不存在");
            sourcesDir = distDir;
            log.info("vue项目准备部署，dist目录: {}", distDir.getAbsolutePath());
        }
        //8.复制应用生成目录到部署目录（整目录替换，避免旧资源残留）
        String deployDir = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        File deployDirFile = new File(deployDir);
        try {
            if (deployDirFile.exists()) {
                FileUtil.del(deployDirFile);
            }
            FileUtil.mkdir(deployDirFile);
            FileUtil.copyContent(sourcesDir, deployDirFile, true);
            log.info("应用部署文件已复制: {} -> {}", sourcesDir.getAbsolutePath(), deployDirFile.getAbsolutePath());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败" + e.getMessage());
        }
        //9.更新数据库的应用信息
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "应用部署信息更新失败");
        //10.返回可以访问的应用部署地址（附带时间戳，避免浏览器缓存旧版本）
        String appUrl = String.format("%s/%s/?t=%d", AppConstant.CODE_DEPLOY_HOST, deployKey, System.currentTimeMillis());
        //异步调用生成截图服务并更新应用封面
        generateAppScreenshotAsync(appId, appUrl);
        return appUrl;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(() -> {
            try {
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("应用截图生成失败，跳过封面更新，appId={}，appUrl={}", appId, appUrl);
                    return;
                }
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updated = this.updateById(updateApp);
                if (!updated) {
                    log.error("更新应用封面失败，appId={}", appId);
                }
            } catch (Exception e) {
                log.error("异步生成应用截图失败，appId={}，appUrl={}", appId, appUrl, e);
            }
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    /**
     * 删除应用：先清理关联资源（磁盘/Redis/任务），再软删对话历史与应用。
     * 资源清理为 best-effort，失败只打日志，不阻断软删。
     * 软删前清空 deployKey，避免唯一索引被软删行长期占用。
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 软删前必须先查出元数据（软删后 getById 会被逻辑删除过滤）
        App app = this.getById(appId);
        if (app == null) {
            return false;
        }

        // 1. 清理磁盘、Redis 记忆、进行中的生成任务等（不含 COS）
        try {
            appCleanupService.cleanupAppResources(app);
        } catch (Exception e) {
            log.error("清理应用{}关联资源失败：{}", appId, e.getMessage(), e);
        }

        // 2. 释放 deployKey 唯一约束，便于后续应用复用（null 不占用 uk）
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            try {
                UpdateChain.of(App.class)
                        .set(App::getDeployKey, null)
                        .where(App::getId).eq(appId)
                        .update();
            } catch (Exception e) {
                log.warn("清空应用{}的 deployKey 失败：{}", appId, e.getMessage());
            }
        }

        // 3. 软删关联对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用{}关联的对话历史失败：{}", appId, e.getMessage());
        }

        // 4. 软删应用本身
        return super.removeById(id);
    }

}
