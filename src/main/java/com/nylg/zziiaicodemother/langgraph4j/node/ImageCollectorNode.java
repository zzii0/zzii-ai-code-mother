package com.nylg.zziiaicodemother.langgraph4j.node;

import com.nylg.zziiaicodemother.ai.ImageCollectionService;
import com.nylg.zziiaicodemother.langgraph4j.state.WorkflowContext;
import com.nylg.zziiaicodemother.utils.SpringContextUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = "";
            try {
                // 获取AI图片收集服务
                ImageCollectionService imageCollectionService = SpringContextUtil.getBean(ImageCollectionService.class);
                // 调用AI服务进行图片收集
                imageListStr = imageCollectionService.collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("图片收集失败：{}", e.getMessage(),e);
            }
            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageListStr(imageListStr);
            return WorkflowContext.saveContext(context);
        });
    }
}
