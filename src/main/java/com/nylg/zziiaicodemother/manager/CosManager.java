package com.nylg.zziiaicodemother.manager;

import com.nylg.zziiaicodemother.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
/**
 * COS对象存储管理器
 *
 */
@Component
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传文件
     * @param key 文件名
     * @param file  文件
     */
    public PutObjectResult putObjectResult(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        try {
            PutObjectResult putObjectResult = putObjectResult(key, file);
            if (putObjectResult == null) {
                log.error("上传文件到 COS 失败，{}", file.getName());
                return null;
            }
            String url = buildAccessUrl(key);
            log.info("文件上传到 COS 成功，访问URL：{}", url);
            return url;
        } catch (Exception e) {
            log.error("上传文件到 COS 失败，key={}，file={}，原因：{}", key, file.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * 拼接可访问的完整 URL（必须带协议，否则前端会当成相对路径）
     */
    private String buildAccessUrl(String key) {
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        String host = cosClientConfig.getHost() == null ? "" : cosClientConfig.getHost().trim();
        host = host.replaceAll("/+$", "");
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            host = "https://" + host;
        }
        return host + "/" + normalizedKey;
    }
}
