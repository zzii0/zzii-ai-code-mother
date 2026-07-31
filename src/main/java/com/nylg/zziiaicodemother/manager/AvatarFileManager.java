package com.nylg.zziiaicodemother.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 用户头像上传管理器（上传至 COS 对象存储）
 */
@Component
@Slf4j
public class AvatarFileManager {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg",
            "jpeg",
            "png",
            "gif",
            "webp"
    );

    private static final long MAX_SIZE = 2 * 1024 * 1024L;

    @Value("${file.upload.avatar-dir:tmp/avatar}")
    private String avatarDir;

    @Resource
    private CosManager cosManager;

    /**
     * 校验并上传头像到 COS，返回可访问的完整 URL
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "头像文件不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户未登录");

        String contentType = file.getContentType();
        ThrowUtils.throwIf(contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType),
                ErrorCode.PARAMS_ERROR, "仅支持 JPG、PNG、GIF、WebP 格式");
        ThrowUtils.throwIf(file.getSize() > MAX_SIZE, ErrorCode.PARAMS_ERROR, "头像大小不能超过 2MB");

        String extension = resolveExtension(file.getOriginalFilename(), contentType);
        ThrowUtils.throwIf(!ALLOWED_EXTENSIONS.contains(extension), ErrorCode.PARAMS_ERROR, "文件格式不支持");

        String fileName = userId + "_" + IdUtil.getSnowflakeNextIdStr() + "." + extension;
        String cosKey = generateAvatarKey(fileName);
        File tempFile = null;
        try {
            tempFile = saveToTempFile(file, fileName);
            String cosUrl = cosManager.uploadFile(cosKey, tempFile);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.OPERATION_ERROR, "头像上传到对象存储失败");
            log.info("用户头像上传 COS 成功，userId={}，url={}", userId, cosUrl);
            return cosUrl;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("用户头像上传失败，userId={}", userId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "头像上传失败");
        } finally {
            cleanupTempFile(tempFile);
        }
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String extension = FileUtil.extName(originalFilename);
        if (extension == null || extension.isBlank()) {
            extension = switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                default -> null;
            };
        }
        return extension == null ? "" : extension.toLowerCase();
    }

    /**
     * 生成头像 COS 对象键：avatars/2026/07/31/filename.jpg
     */
    private String generateAvatarKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("avatars/%s/%s", datePath, fileName);
    }

    private File saveToTempFile(MultipartFile file, String fileName) throws IOException {
        Path uploadDir = resolveAvatarRoot();
        Files.createDirectories(uploadDir);
        Path targetPath = uploadDir.resolve(fileName).normalize();
        ThrowUtils.throwIf(!targetPath.startsWith(uploadDir), ErrorCode.PARAMS_ERROR, "非法文件路径");
        file.transferTo(targetPath.toFile());
        return targetPath.toFile();
    }

    private void cleanupTempFile(File tempFile) {
        if (tempFile == null || !tempFile.exists()) {
            return;
        }
        try {
            FileUtil.del(tempFile);
        } catch (Exception e) {
            log.warn("清理头像临时文件失败: {}", tempFile.getAbsolutePath(), e);
        }
    }

    /**
     * 解析头像本地目录（兼容历史本地头像静态资源映射）
     */
    public Path resolveAvatarRoot() {
        Path path = Paths.get(avatarDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), avatarDir);
        }
        return path.toAbsolutePath().normalize();
    }
}
