package com.nylg.zziiaicodemother.service;

/**
 * 通用截图服务
 */
public interface ScreenshotService {

    /**
     * 截图并上传
     *
     * @param webUrl 网页地址
     */
    String generateAndUploadScreenshot(String webUrl);
}
