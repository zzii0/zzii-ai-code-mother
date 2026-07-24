package com.nylg.zziiaicodemother.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Component
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

    public String uploadAvatar(MultipartFile file, Long userId) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "头像文件不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户未登录");

        String contentType = file.getContentType();
        ThrowUtils.throwIf(contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType),
                ErrorCode.PARAMS_ERROR, "仅支持 JPG、PNG、GIF、WebP 格式");

        ThrowUtils.throwIf(file.getSize() > MAX_SIZE, ErrorCode.PARAMS_ERROR, "头像大小不能超过 2MB");

        String extension = FileUtil.extName(file.getOriginalFilename());
        if (extension == null || extension.isBlank()) {
            extension = switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/gif" -> "gif";
                case "image/webp" -> "webp";
                default -> null;
            };
        }
        extension = extension == null ? "" : extension.toLowerCase();
        ThrowUtils.throwIf(!ALLOWED_EXTENSIONS.contains(extension), ErrorCode.PARAMS_ERROR, "文件格式不支持");

        Path uploadDir = resolveAvatarRoot();
        try {
            Files.createDirectories(uploadDir);
            String fileName = userId + "_" + IdUtil.getSnowflakeNextIdStr() + "." + extension;
            Path targetPath = uploadDir.resolve(fileName).normalize();
            ThrowUtils.throwIf(!targetPath.startsWith(uploadDir), ErrorCode.PARAMS_ERROR, "非法文件路径");
            file.transferTo(targetPath.toFile());
            return "/static/avatar/" + fileName;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "头像上传失败");
        }
    }

    /**
     * 解析头像目录：相对路径基于项目工作目录，与代码输出目录策略一致
     */
    public Path resolveAvatarRoot() {
        Path path = Paths.get(avatarDir);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir"), avatarDir);
        }
        return path.toAbsolutePath().normalize();
    }
}
