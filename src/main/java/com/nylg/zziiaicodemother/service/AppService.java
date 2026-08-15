package com.nylg.zziiaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.nylg.zziiaicodemother.model.dto.app.AppQueryRequest;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 *  服务层。
 *
 * @author zzii
 */
public interface AppService extends IService<App> {

    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 获取应用封装类。
     *
     * @param app
     */
    AppVO getAppVO(App app);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 聊天生成代码
      * @param appId 应用id
     * @param userMessage 用户消息
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String userMessage, User loginUser);

    /**
 * 停止当前进行中的 AI 生成。
 * 由 POST /app/chat/gen/stop 调用；仅应用创建者可操作。
 * 返回 true 表示找到进行中的任务并已触发 cancel。
 *
 * @param appId     应用 id
 * @param loginUser 登录用户
 * @return 是否成功触发停止
 */
    boolean stopChatGeneration(Long appId, User loginUser);
    /**
     * 部署应用
     * @param appId 应用id
     * @param loginUser 登录用户
     * @return
     */
    String deployApp(Long appId,User loginUser);
}
