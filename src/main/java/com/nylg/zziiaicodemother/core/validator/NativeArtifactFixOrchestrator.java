package com.nylg.zziiaicodemother.core.validator;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorService;
import com.nylg.zziiaicodemother.ai.AiCodeGeneratorServiceFactory;
import com.nylg.zziiaicodemother.ai.model.result.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import com.nylg.zziiaicodemother.core.generation.AiGenerationTask;
import com.nylg.zziiaicodemother.core.parser.CodeParserExecutor;
import com.nylg.zziiaicodemother.core.saver.CodeFileSaverExecutor;
import com.nylg.zziiaicodemother.core.sse.SseEventCodec;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 原生 HTML / 多文件：校验产物 → 定向补生成（最多 1 次）→ 保存，并通过 SSE 推送阶段状态。
 */
@Slf4j
@Component
public class NativeArtifactFixOrchestrator {

    @Resource
    private NativeArtifactValidator nativeArtifactValidator;

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 流式路径：AI 文本生成结束后执行校验、补生成与保存。
     */
    public Flux<String> runAfterGeneration(String completeCode,
                                           CodeGenTypeEnum codeGenType,
                                           long appId,
                                           User loginUser,
                                           String userMessage,
                                           AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return Flux.empty();
        }
        return Flux.concat(
                Flux.just(
                        SseEventCodec.encode(SseEventCodec.GENERATION_DONE, Map.of("appId", appId)),
                        "\n\n⚙️ 代码已生成，正在校验并保存...\n",
                        SseEventCodec.encode(SseEventCodec.VALIDATE_START, Map.of("appId", appId))
                ),
                Mono.fromCallable(() -> processArtifacts(completeCode, codeGenType, appId, userMessage, generationTask))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(result -> toResultFlux(result, appId, loginUser))
        );
    }

    /**
     * 非流式路径：已有解析结果时校验、补生成并保存。
     */
    public File prepareAndSaveParsed(Object parsed,
                                     CodeGenTypeEnum codeGenType,
                                     long appId,
                                     String userMessage) {
        ProcessResult result = processParsed(parsed, codeGenType, appId, userMessage, null);
        if (!result.ok()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    StrUtil.blankToDefault(result.errorMessage(), "代码校验未通过"));
        }
        return result.savedDir();
    }

    /**
     * 非流式路径：从原始文本解析后校验、补生成并保存。
     */
    public File prepareAndSave(String completeCode,
                               CodeGenTypeEnum codeGenType,
                               long appId,
                               String userMessage) {
        ProcessResult result = processArtifacts(completeCode, codeGenType, appId, userMessage, null);
        if (!result.ok()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    StrUtil.blankToDefault(result.errorMessage(), "代码校验未通过"));
        }
        return result.savedDir();
    }

    private ProcessResult processArtifacts(String completeCode,
                                           CodeGenTypeEnum codeGenType,
                                           long appId,
                                           String userMessage,
                                           AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return ProcessResult.cancelledResult();
        }
        Object parsed = CodeParserExecutor.executeParser(completeCode, codeGenType);
        return processParsed(parsed, codeGenType, appId, userMessage, generationTask);
    }

    private ProcessResult processParsed(Object parsed,
                                        CodeGenTypeEnum codeGenType,
                                        long appId,
                                        String userMessage,
                                        AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return ProcessResult.cancelledResult();
        }
        AiCodeGeneratorService aiService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);

        ArtifactValidationResult firstValidation = validate(codeGenType, parsed);
        ArtifactValidationResult finalValidation = firstValidation;
        boolean attemptedFix = false;

        if (!firstValidation.isValid()) {
            log.warn("产物校验未通过，appId={}, issues={}", appId, firstValidation.getIssues());
            attemptedFix = true;
            parsed = regenerateProblematicFiles(parsed, codeGenType, firstValidation, aiService, userMessage, generationTask);
            if (generationTask != null && generationTask.isCancelled()) {
                return ProcessResult.cancelledResult();
            }
            finalValidation = validate(codeGenType, parsed);
        }

        if (!finalValidation.isValid()) {
            return ProcessResult.failed(firstValidation, finalValidation, attemptedFix);
        }

        try {
            File savedDir = CodeFileSaverExecutor.executeSaver(parsed, codeGenType, appId);
            log.info("原生模式代码保存成功，路径={}", savedDir.getAbsolutePath());
            return ProcessResult.saved(savedDir, firstValidation, attemptedFix);
        } catch (Exception e) {
            log.error("原生模式代码保存失败，appId={}", appId, e);
            ArtifactValidationResult saveFail = ArtifactValidationResult.fail(List.of(
                    ArtifactIssue.builder().fileName("save").message("保存失败: " + e.getMessage()).build()
            ));
            return ProcessResult.failed(firstValidation, saveFail, attemptedFix);
        }
    }

    private Flux<String> toResultFlux(ProcessResult result, long appId, User loginUser) {
        if (result.wasCancelled()) {
            return Flux.empty();
        }

        List<String> chunks = new ArrayList<>();

        if (result.attemptedFix() && result.firstValidation() != null && !result.firstValidation().isValid()) {
            Map<String, Object> failPayload = buildFailPayload(result.firstValidation());
            chunks.add(SseEventCodec.encode(SseEventCodec.VALIDATE_FAILED, failPayload));
            chunks.add(formatValidationFailedText(result.firstValidation()));
            for (Map.Entry<String, List<ArtifactIssue>> entry : groupIssuesByFile(result.firstValidation().getIssues()).entrySet()) {
                String fileName = entry.getKey();
                String message = entry.getValue().stream()
                        .map(ArtifactIssue::getMessage)
                        .collect(Collectors.joining("；"));
                chunks.add(SseEventCodec.encode(SseEventCodec.ARTIFACT_FIXING, Map.of(
                        "errorFile", fileName,
                        "errorMessage", message
                )));
                chunks.add("\n\n🔧 正在自动补生成 " + fileName + "...\n");
            }
        }

        if (result.ok()) {
            String successText = result.attemptedFix()
                    ? "\n\n✅ 已自动补生成问题文件，校验通过，预览已就绪。\n"
                    : "\n\n✅ 代码校验通过，预览已就绪。\n";
            chatHistoryService.addChatMessage(appId, successText.trim(),
                    ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
            chunks.add(SseEventCodec.encode(SseEventCodec.SAVE_SUCCESS, Map.of("appId", appId)));
            chunks.add(successText);
            return Flux.fromIterable(chunks);
        }

        ArtifactValidationResult validation = result.finalValidation() != null
                ? result.finalValidation()
                : result.firstValidation();
        Map<String, Object> giveUpPayload = buildFailPayload(validation);
        String failText = formatValidationFailedText(validation);
        String giveUpText = "\n\n🚫 自动补生成后仍未通过校验，请根据错误信息手动描述修复需求。\n";
        chatHistoryService.addChatMessage(appId, failText + giveUpText,
                ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        chunks.add(SseEventCodec.encode(SseEventCodec.SAVE_GIVE_UP, giveUpPayload));
        chunks.add(failText);
        chunks.add(giveUpText);
        return Flux.fromIterable(chunks);
    }

    private Object regenerateProblematicFiles(Object parsed,
                                              CodeGenTypeEnum codeGenType,
                                              ArtifactValidationResult validation,
                                              AiCodeGeneratorService aiService,
                                              String userMessage,
                                              AiGenerationTask generationTask) {
        Set<String> targetFiles = validation.getIssues().stream()
                .map(ArtifactIssue::getFileName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (codeGenType == CodeGenTypeEnum.HTML) {
            HtmlCodeResult htmlResult = parsed instanceof HtmlCodeResult hr ? hr : new HtmlCodeResult();
            ArtifactIssue issue = validation.getIssues().stream()
                    .filter(i -> "index.html".equals(i.getFileName()))
                    .findFirst()
                    .orElse(validation.firstIssue());
            String fixedHtml = regenerateHtmlContent(aiService, userMessage, issue, null, generationTask);
            if (StrUtil.isNotBlank(fixedHtml)) {
                htmlResult.setHtmlCode(fixedHtml);
            }
            return htmlResult;
        }

        MultiFileCodeResult multiResult = parsed instanceof MultiFileCodeResult mr
                ? copyMultiFile(mr) : new MultiFileCodeResult();
        Map<String, List<ArtifactIssue>> issuesByFile = groupIssuesByFile(validation.getIssues());

        for (String fileName : targetFiles) {
            if (generationTask != null && generationTask.isCancelled()) {
                break;
            }
            List<ArtifactIssue> fileIssues = issuesByFile.getOrDefault(fileName, List.of());
            if (fileIssues.isEmpty()) {
                continue;
            }
            String mergedMessage = fileIssues.stream()
                    .map(ArtifactIssue::getMessage)
                    .collect(Collectors.joining("；"));
            ArtifactIssue issue = ArtifactIssue.builder().fileName(fileName).message(mergedMessage).build();
            applyMultiFileFix(multiResult, aiService, userMessage, issue, generationTask);
        }
        return multiResult;
    }

    private void applyMultiFileFix(MultiFileCodeResult multiResult,
                                   AiCodeGeneratorService aiService,
                                   String userMessage,
                                   ArtifactIssue issue,
                                   AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return;
        }
        String fileName = issue.getFileName();
        switch (fileName) {
            case "index.html" -> {
                String context = """
                        当前项目另有 style.css 与 script.js，请在 HTML 中正确引用：
                        <link rel="stylesheet" href="style.css">
                        <script src="script.js"></script>
                        """;
                String fixedHtml = regenerateHtmlContent(aiService, userMessage, issue, context, generationTask);
                if (StrUtil.isNotBlank(fixedHtml)) {
                    multiResult.setHtmlCode(fixedHtml);
                }
            }
            case "style.css" -> {
                String css = regenerateCssOrJs(aiService, userMessage, issue, "css", multiResult, generationTask);
                if (StrUtil.isNotBlank(css)) {
                    multiResult.setCssCode(css);
                }
            }
            case "script.js" -> {
                String js = regenerateCssOrJs(aiService, userMessage, issue, "javascript", multiResult, generationTask);
                if (StrUtil.isNotBlank(js)) {
                    multiResult.setJsCode(js);
                }
            }
            default -> log.warn("未知待修复文件: {}", fileName);
        }
    }

    private String regenerateHtmlContent(AiCodeGeneratorService aiService,
                                         String userMessage,
                                         ArtifactIssue issue,
                                         String extraContext,
                                         AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return null;
        }
        String prompt = buildHtmlFixPrompt(userMessage, issue, extraContext);
        log.info("定向补生成 index.html，原因={}", issue.getMessage());
        HtmlCodeResult fixed = aiService.generateHtmlCode(prompt);
        if (fixed != null && StrUtil.isNotBlank(fixed.getHtmlCode())) {
            String html = fixed.getHtmlCode().trim();
            if (!html.toLowerCase().contains("<html")) {
                Object reparsed = CodeParserExecutor.executeParser(html, CodeGenTypeEnum.HTML);
                if (reparsed instanceof HtmlCodeResult hr && StrUtil.isNotBlank(hr.getHtmlCode())) {
                    return hr.getHtmlCode().trim();
                }
            }
            return html;
        }
        return null;
    }

    private String regenerateCssOrJs(AiCodeGeneratorService aiService,
                                     String userMessage,
                                     ArtifactIssue issue,
                                     String fenceLang,
                                     MultiFileCodeResult existing,
                                     AiGenerationTask generationTask) {
        if (generationTask != null && generationTask.isCancelled()) {
            return null;
        }
        String prompt = buildSingleFileFixPrompt(userMessage, issue, fenceLang, existing);
        log.info("定向补生成 {}，原因={}", issue.getFileName(), issue.getMessage());

        StringBuilder raw = new StringBuilder();
        try {
            aiService.generateMultiFileCodeStream(prompt)
                    .doOnNext(raw::append)
                    .blockLast();
        } catch (Exception e) {
            log.warn("流式补生成失败，回退同步接口: {}", e.getMessage());
            MultiFileCodeResult sync = aiService.generateMultiFileCode(prompt);
            return pickField(sync, issue.getFileName());
        }

        Object parsed = CodeParserExecutor.executeParser(raw.toString(), CodeGenTypeEnum.MULTI_FILE);
        if (parsed instanceof MultiFileCodeResult mr) {
            String picked = pickField(mr, issue.getFileName());
            if (StrUtil.isNotBlank(picked)) {
                return picked;
            }
        }
        MultiFileCodeResult sync = aiService.generateMultiFileCode(prompt);
        return pickField(sync, issue.getFileName());
    }

    private static String pickField(MultiFileCodeResult result, String fileName) {
        if (result == null) {
            return null;
        }
        return switch (fileName) {
            case "style.css" -> StrUtil.trim(result.getCssCode());
            case "script.js" -> StrUtil.trim(result.getJsCode());
            case "index.html" -> StrUtil.trim(result.getHtmlCode());
            default -> null;
        };
    }

    static String buildHtmlFixPrompt(String userMessage, ArtifactIssue issue, String extraContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("请重新生成完整的 index.html 文件。\n");
        sb.append("上次生成存在问题：").append(issue.getMessage()).append("\n\n");
        sb.append("原始需求：\n").append(userMessage).append("\n\n");
        if (StrUtil.isNotBlank(extraContext)) {
            sb.append(extraContext).append("\n\n");
        }
        sb.append("""
                要求：
                - 只输出一个 ```html 代码块
                - 必须是完整可运行的 HTML 页面（含 <html> 与 </html>）
                - 不要输出说明文字
                """);
        return sb.toString();
    }

    static String buildSingleFileFixPrompt(String userMessage,
                                           ArtifactIssue issue,
                                           String fenceLang,
                                           MultiFileCodeResult existing) {
        String fileName = issue.getFileName();
        StringBuilder sb = new StringBuilder();
        sb.append("请只重新生成 ").append(fileName).append(" 文件，不要输出其他文件。\n");
        sb.append("上次生成存在问题：").append(issue.getMessage()).append("\n\n");
        sb.append("原始需求：\n").append(userMessage).append("\n\n");
        if (existing != null && StrUtil.isNotBlank(existing.getHtmlCode())) {
            sb.append("当前 index.html 参考（请与之配合）：\n```html\n")
                    .append(existing.getHtmlCode())
                    .append("\n```\n\n");
        }
        sb.append("要求：\n");
        sb.append("- 只输出一个 ```").append(fenceLang).append(" 代码块\n");
        sb.append("- 内容必须完整可用\n");
        sb.append("- 不要输出说明文字\n");
        return sb.toString();
    }

    private ArtifactValidationResult validate(CodeGenTypeEnum codeGenType, Object parsed) {
        return switch (codeGenType) {
            case HTML -> nativeArtifactValidator.validateHtml(
                    parsed instanceof HtmlCodeResult hr ? hr : new HtmlCodeResult());
            case MULTI_FILE -> nativeArtifactValidator.validateMultiFile(
                    parsed instanceof MultiFileCodeResult mr ? mr : new MultiFileCodeResult());
            default -> ArtifactValidationResult.ok();
        };
    }

    private static MultiFileCodeResult copyMultiFile(MultiFileCodeResult source) {
        MultiFileCodeResult copy = new MultiFileCodeResult();
        copy.setHtmlCode(source.getHtmlCode());
        copy.setCssCode(source.getCssCode());
        copy.setJsCode(source.getJsCode());
        copy.setDescription(source.getDescription());
        return copy;
    }

    private static Map<String, List<ArtifactIssue>> groupIssuesByFile(List<ArtifactIssue> issues) {
        Map<String, List<ArtifactIssue>> grouped = new LinkedHashMap<>();
        for (ArtifactIssue issue : issues) {
            grouped.computeIfAbsent(issue.getFileName(), key -> new ArrayList<>()).add(issue);
        }
        return grouped;
    }

    private static Map<String, Object> buildFailPayload(ArtifactValidationResult validation) {
        Map<String, Object> payload = new HashMap<>();
        ArtifactIssue first = validation.firstIssue();
        if (first != null) {
            payload.put("errorFile", first.getFileName());
            payload.put("errorMessage", first.getMessage());
        }
        List<Map<String, String>> issueList = validation.getIssues().stream()
                .map(issue -> Map.of(
                        "fileName", StrUtil.nullToDefault(issue.getFileName(), ""),
                        "message", StrUtil.nullToDefault(issue.getMessage(), "")))
                .toList();
        payload.put("issues", issueList);
        return payload;
    }

    static String formatValidationFailedText(ArtifactValidationResult validation) {
        StringBuilder sb = new StringBuilder("\n\n❌ 代码校验未通过");
        for (ArtifactIssue issue : validation.getIssues()) {
            sb.append("\n- ").append(issue.getFileName()).append(": ").append(issue.getMessage());
        }
        sb.append("\n");
        return sb.toString();
    }

    private record ProcessResult(boolean ok,
                                 boolean wasCancelled,
                                 boolean attemptedFix,
                                 File savedDir,
                                 ArtifactValidationResult firstValidation,
                                 ArtifactValidationResult finalValidation,
                                 String errorMessage) {
        static ProcessResult saved(File dir, ArtifactValidationResult firstValidation, boolean attemptedFix) {
            return new ProcessResult(true, false, attemptedFix, dir, firstValidation, null, null);
        }

        static ProcessResult failed(ArtifactValidationResult firstValidation,
                                    ArtifactValidationResult finalValidation,
                                    boolean attemptedFix) {
            ArtifactIssue first = finalValidation != null ? finalValidation.firstIssue() : null;
            if (first == null && firstValidation != null) {
                first = firstValidation.firstIssue();
            }
            String message = first != null
                    ? first.getFileName() + " — " + first.getMessage()
                    : "代码校验未通过";
            return new ProcessResult(false, false, attemptedFix, null, firstValidation, finalValidation, message);
        }

        static ProcessResult cancelledResult() {
            return new ProcessResult(false, true, false, null, null, null, null);
        }
    }
}
