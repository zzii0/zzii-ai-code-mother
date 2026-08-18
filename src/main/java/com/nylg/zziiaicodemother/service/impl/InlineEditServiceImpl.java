package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.constant.UserConstant;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import com.nylg.zziiaicodemother.model.dto.app.AppInlineEditRequest;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.service.AppService;
import com.nylg.zziiaicodemother.service.AppVersionService;
import com.nylg.zziiaicodemother.service.InlineEditService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原生 HTML / 多文件模式行内编辑：按 CSS 选择器定位元素并更新文本
 */
@Service
@Slf4j
public class InlineEditServiceImpl implements InlineEditService {

    private static final String DEFAULT_FILE = "index.html";
    private static final ConcurrentHashMap<Long, Object> APP_EDIT_LOCKS = new ConcurrentHashMap<>();
    private static final Safelist INLINE_HTML_SAFELIST = Safelist.none()
            .addTags("span", "br", "strong", "em", "b", "i", "u", "small", "a")
            .addAttributes("a", "href", "title", "target", "rel")
            .addAttributes("span", "class");

    @Resource
    private AppService appService;

    @Resource
    private AppVersionService appVersionService;

    @Override
    public Boolean inlineEdit(AppInlineEditRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long appId = request.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");

        Object lock = APP_EDIT_LOCKS.computeIfAbsent(appId, id -> new Object());
        synchronized (lock) {
            return doInlineEdit(request, loginUser);
        }
    }

    private Boolean doInlineEdit(AppInlineEditRequest request, User loginUser) {
        Long appId = request.getAppId();
        String selector = StrUtil.trim(request.getSelector());
        String newContent = request.getNewContent();
        String innerHtml = request.getInnerHtml();
        String fileName = StrUtil.blankToDefault(StrUtil.trim(request.getFile()), DEFAULT_FILE);

        ThrowUtils.throwIf(StrUtil.isBlank(selector), ErrorCode.PARAMS_ERROR, "选择器不能为空");
        ThrowUtils.throwIf(newContent == null && StrUtil.isBlank(innerHtml), ErrorCode.PARAMS_ERROR, "新内容不能为空");
        ThrowUtils.throwIf(!DEFAULT_FILE.equals(fileName), ErrorCode.PARAMS_ERROR, "暂仅支持编辑 index.html");

        App app = getAndCheckPermission(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app);
        ThrowUtils.throwIf(!CodeGenTypeEnum.HTML.equals(codeGenTypeEnum)
                        && !CodeGenTypeEnum.MULTI_FILE.equals(codeGenTypeEnum),
                ErrorCode.PARAMS_ERROR, "当前生成模式不支持行内编辑");

        File htmlFile = getHtmlFile(appId, codeGenTypeEnum);
        ThrowUtils.throwIf(!htmlFile.exists() || !htmlFile.isFile(),
                ErrorCode.NOT_FOUND_ERROR, "目标 HTML 文件不存在");

        try {
            String originalHtml = Files.readString(htmlFile.toPath(), StandardCharsets.UTF_8);
            Document document = Jsoup.parse(originalHtml, "");
            document.outputSettings()
                    .prettyPrint(false)
                    .outline(false)
                    .charset(StandardCharsets.UTF_8);

            Elements matchedElements = document.select(selector);
            ThrowUtils.throwIf(matchedElements.isEmpty(), ErrorCode.PARAMS_ERROR, "未找到目标元素，请刷新预览后重试");
            ThrowUtils.throwIf(matchedElements.size() > 1, ErrorCode.PARAMS_ERROR, "选择器匹配到多个元素");

            Element targetElement = matchedElements.first();
            String currentText = normalizeText(targetElement.text());
            String trimmedNewContent = normalizeText(newContent);

            if (StrUtil.isNotBlank(trimmedNewContent) && currentText.equals(trimmedNewContent)
                    && StrUtil.isBlank(innerHtml)) {
                return true;
            }

            appVersionService.archiveCurrentVersion(appId, codeGenTypeEnum);
            applyContent(targetElement, innerHtml, newContent);

            String html = document.html();
            if (hasDoctype(originalHtml) && !startsWithDoctype(html)) {
                html = "<!DOCTYPE html>\n" + html;
            }
            Files.writeString(htmlFile.toPath(), html, StandardCharsets.UTF_8);

            log.info("行内编辑成功, appId={}, selector={}", appId, selector);
            return true;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("行内编辑失败, appId={}, selector={}", appId, selector, e);
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "行内编辑失败：" + e.getMessage());
            return false;
        }
    }

    private void applyContent(Element targetElement, String innerHtml, String newContent) {
        if (StrUtil.isNotBlank(innerHtml)) {
            String cleaned = Jsoup.clean(innerHtml, "", INLINE_HTML_SAFELIST,
                    new Document.OutputSettings().prettyPrint(false));
            targetElement.html(cleaned);
            return;
        }
        replaceVisibleText(targetElement, StrUtil.nullToEmpty(newContent).trim());
    }

    /**
     * 尽量只替换文本节点，避免把标题里的图标等子元素清掉
     */
    private void replaceVisibleText(Element element, String newContent) {
        List<TextNode> textNodes = new ArrayList<>();
        collectTextNodes(element, textNodes);
        List<TextNode> significant = textNodes.stream()
                .filter(node -> StrUtil.isNotBlank(node.getWholeText()))
                .toList();
        if (significant.isEmpty()) {
            element.text(newContent);
            return;
        }
        if (significant.size() == 1) {
            significant.get(0).text(newContent);
            return;
        }
        TextNode primary = significant.stream()
                .max(Comparator.comparingInt(node -> node.getWholeText().trim().length()))
                .orElse(significant.getLast());
        primary.text(newContent);
    }

    private void collectTextNodes(Element element, List<TextNode> textNodes) {
        for (Node child : element.childNodes()) {
            if (child instanceof TextNode textNode) {
                textNodes.add(textNode);
            } else if (child instanceof Element childElement) {
                collectTextNodes(childElement, textNodes);
            }
        }
    }

    private static String normalizeText(String text) {
        return StrUtil.nullToEmpty(text).replaceAll("\\s+", " ").trim();
    }

    private static boolean hasDoctype(String html) {
        String trimmed = StrUtil.nullToEmpty(html).trim();
        return startsWithDoctype(trimmed);
    }

    private static boolean startsWithDoctype(String html) {
        return html.regionMatches(true, 0, "<!doctype", 0, 9);
    }

    private App getAndCheckPermission(Long appId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isOwner = app.getUserId().equals(loginUser.getId());
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isOwner && !isAdmin, ErrorCode.NO_AUTH_ERROR, "无权限编辑该应用");
        return app;
    }

    private CodeGenTypeEnum resolveCodeGenType(App app) {
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "代码生成类型错误");
        return codeGenTypeEnum;
    }

    private File getHtmlFile(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        String dirName = codeGenTypeEnum.getValue() + "_" + appId;
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dirName + File.separator + DEFAULT_FILE);
    }
}
