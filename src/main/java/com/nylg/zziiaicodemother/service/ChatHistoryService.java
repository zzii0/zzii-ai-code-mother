package com.nylg.zziiaicodemother.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.nylg.zziiaicodemother.model.dto.chatHistory.ChatHistoryQueryRequest;
import com.nylg.zziiaicodemother.model.entity.ChatHistory;
import com.nylg.zziiaicodemother.model.entity.User;

import java.time.LocalDateTime;

/**
 *  服务层。
 *
 * @author zzii
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史信息
     * @param appId 应用id
     * @param message 消息
     * @param userId 用户id
     * @param messageType 消息类型
     * @return
     */
    boolean addChatMessage(Long appId,String message,String messageType,Long userId);

    /**
     * 根据应用id删除对话历史信息
     * @param appId 应用id
     * @return
     */
    boolean deleteByAppId(Long appId);

    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);

    /**
     * 根据应用id分页获取对话历史信息
     * @param appId 应用id
     * @param pageSize 每页大小
     * @param lastCreateTime 上次创建时间
     * @param loginUser 登录用户
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
