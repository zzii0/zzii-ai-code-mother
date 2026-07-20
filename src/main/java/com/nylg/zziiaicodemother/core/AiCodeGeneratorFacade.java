package com.nylg.zziiaicodemother.core;

import com.nylg.zziiaicodemother.ai.AiCodeGeneratorService;
import com.nylg.zziiaicodemother.ai.model.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.MultiFileCodeResult;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * 代码生成器外观类
 */
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> generateHtmlCode(userMessage);
            case MULTI_FILE -> generateMultiFileCode(userMessage);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 生成多文件代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateMultiFileCode(String userMessage) {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
    }

    /**
     * 生成HTML代码并保存
     *
     * @param userMessage 用户提示词
     * @return 保存的目录
     */
    private File generateHtmlCode(String userMessage) {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
    }
}
