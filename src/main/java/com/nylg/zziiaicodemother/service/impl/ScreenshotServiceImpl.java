package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.manager.CosManager;
import com.nylg.zziiaicodemother.service.ScreenshotService;
import com.nylg.zziiaicodemother.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;

    @Resource
    private WebScreenshotUtils webScreenshotUtils;

    private static final int MAX_SCREENSHOT_RETRIES = 2;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页 URL 不能为空");
            return null;
        }
        String localScreenshotPath = null;
        try {
            log.info("开始生成截图，网页URL：{}", webUrl);
            for (int attempt = 0; attempt <= MAX_SCREENSHOT_RETRIES; attempt++) {
                if (attempt > 0) {
                    log.warn("截图失败，开始第 {} 次重试，webUrl={}", attempt, webUrl);
                    Thread.sleep(2000L * attempt);
                }
                localScreenshotPath = webScreenshotUtils.saveWebPageScreenshot(webUrl);
                if (StrUtil.isNotBlank(localScreenshotPath)) {
                    break;
                }
            }
            if (StrUtil.isBlank(localScreenshotPath)) {
                log.error("截图生成失败，webUrl={}", webUrl);
                return null;
            }
            log.info("开始上传截图到对象存储");
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            if (StrUtil.isBlank(cosUrl)) {
                log.error("上传截图到对象存储失败，webUrl={}", webUrl);
                return null;
            }
            log.info("截图上传成功，访问URL：{}", cosUrl);
            return cosUrl;
        } catch (Exception e) {
            log.error("生成并上传截图失败，webUrl={}", webUrl, e);
            return null;
        } finally {
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath 本地截图路径
     * @return 对象存储访问URL，失败返回null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if (!screenshotFile.exists()) {
            log.error("截图文件不存在: {}", localScreenshotPath);
            return null;
        }
        // 生成 COS 对象键
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的对象存储键
     * 格式：/screenshots/2025/07/31/filename.jpg
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        if (StrUtil.isBlank(localFilePath)) {
            return;
        }
        try {
            File localFile = new File(localFilePath);
            if (localFile.exists()) {
                File parentDir = localFile.getParentFile();
                FileUtil.del(parentDir);
                log.info("本地截图文件已清理: {}", localFilePath);
            }
        } catch (Exception e) {
            log.warn("清理本地截图文件失败: {}", localFilePath, e);
        }
    }
}

