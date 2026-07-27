package com.nylg.zziiaicodemother.core;

import com.nylg.zziiaicodemother.ai.AiCodeGeneratorService;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.nylg.zziiaicodemother.ai.model.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.MultiFileCodeResult;
import com.nylg.zziiaicodemother.core.parser.CodeParserExecutor;
import com.nylg.zziiaicodemother.core.saver.CodeFileSaverExecutor;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 代码生成器外观类
 */
@Slf4j
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        //根据appId创建AI服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(htmlCodeResult, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(multiFileCodeResult, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        //根据appId创建AI服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                Flux<String> result = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processCodeStream(result, CodeGenTypeEnum.VUE_PROJECT, appId);
            }
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        };
    }

    /**
     * 处理代码流通用方法
     *
     * @param codeStream      代码流
     * @param codeGenTypeEnum 生成类型
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        StringBuilder stringBuilder = new StringBuilder();
        //实时收集代码片段
        return codeStream
                .doOnNext(stringBuilder::append)
                .doOnComplete(() -> {
                    try {
                        String completeCode = stringBuilder.toString();
                        //使用执行器解析代码
                        Object parserResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                        //使用执行器保存代码
                        File file = CodeFileSaverExecutor.executeSaver(parserResult, codeGenTypeEnum, appId);
                        log.info("代码保存成功，路径为：{}", file.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("代码保存失败", e);
                    }
                });
    }
}