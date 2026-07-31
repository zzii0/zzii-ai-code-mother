package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import com.nylg.zziiaicodemother.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 允许的代码输出根目录
     */
    private static final Path ALLOWED_PROJECT_ROOT = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR)
            .toAbsolutePath()
            .normalize();

    /**
     * 需要过滤的文件和目录名称（小写，用于不区分大小写匹配）
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".ds_store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名（小写）
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    /**
     * 检查给定完整路径是否允许包含在压缩包中
     * @param projectRoot 项目根目录路径
     * @param fullPath 待检查的文件/目录的完整路径
     * @return true表示允许，false表示应被过滤
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        // 规范化项目根目录绝对路径
        Path normalizedRoot = projectRoot.toAbsolutePath().normalize();
        // 规范化待检查路径绝对路径
        Path normalizedFull = fullPath.toAbsolutePath().normalize();
        if (!normalizedFull.startsWith(normalizedRoot)) {
            // 如果待检查路径不在项目根目录下，则不允许
            return false;
        }
        // 计算相对于项目根目录的相对路径
        Path relativePath = normalizedRoot.relativize(normalizedFull);
        for (Path part : relativePath) {
            // 遍历相对路径的每一级目录/文件名
            String partNameLower = part.toString().toLowerCase(Locale.ROOT); // 转为小写便于不区分大小写匹配
            if (IGNORED_NAMES.contains(partNameLower)) {
                // 如果名称在忽略名称集合中，则不允许
                return false;
            }
            for (String ext : IGNORED_EXTENSIONS) {
                // 遍历忽略的扩展名
                if (partNameLower.endsWith(ext)) {
                    // 如果当前部分以忽略的扩展名结尾，则不允许
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 校验项目目录必须在允许的代码输出根目录下，防止路径遍历攻击
     * @param projectRoot 项目根目录路径
     */
    private void validateProjectWithinAllowedRoot(Path projectRoot) {
        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize(); // 规范化项目根目录绝对路径
        ThrowUtils.throwIf(!normalizedProjectRoot.startsWith(ALLOWED_PROJECT_ROOT), // 如果不在允许的根目录下，抛出参数异常
                ErrorCode.PARAMS_ERROR, "非法的项目路径");
    }

    /**
     * 清洗下载文件名，只保留字母、数字、下划线和连字符，防止响应头注入与非法字符
     * @param downloadFileName 原始下载文件名
     * @return 清洗后的安全文件名
     */
    private String sanitizeDownloadFileName(String downloadFileName) {
        String sanitized = downloadFileName.replaceAll("[^a-zA-Z0-9_-]", ""); // 移除非允许字符
        ThrowUtils.throwIf(StrUtil.isBlank(sanitized), ErrorCode.PARAMS_ERROR, "下载文件名无效"); // 如果清洗后为空则报错
        return sanitized; // 返回清洗后的文件名
    }

    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 参数非空校验：项目路径不能为空
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        // 参数非空校验：下载文件名不能为空
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "下载文件名不能为空");

        File projectDir = new File(projectPath); // 根据路径创建File对象
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.PARAMS_ERROR, "项目目录不存在"); // 校验目录存在
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "项目路径不是目录"); // 校验是目录

        Path projectRoot = projectDir.toPath(); // 将File转为Path
        validateProjectWithinAllowedRoot(projectRoot); // 校验项目目录在允许的根目录下
        String safeFileName = sanitizeDownloadFileName(downloadFileName); // 清洗文件名

        log.info("开始打包下载项目: {} -> {}.zip", projectRoot.toAbsolutePath().normalize(), safeFileName); // 记录打包开始日志

        response.setStatus(HttpServletResponse.SC_OK); // 设置HTTP状态码为200
        response.setContentType("application/zip"); // 设置响应内容类型为ZIP
        response.setHeader("Cache-Control", "no-store"); // 禁止缓存响应
        response.addHeader("Content-Disposition", // 添加Content-Disposition头，指定下载文件名
                String.format("attachment; filename=\"%s.zip\"", safeFileName));

        Path normalizedProjectRoot = projectRoot.toAbsolutePath().normalize(); // 获取规范化的项目根路径
        FileFilter fileFilter = file -> isPathAllowed(normalizedProjectRoot, file.toPath()); // 创建文件过滤器，基于isPathAllowed判断

        try {
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, fileFilter, projectDir); // 调用工具类将项目目录打包成ZIP写入响应输出流
            log.info("项目打包下载完成: {}", safeFileName); // 记录打包成功日志
        } catch (Exception e) {
            log.error("项目打包下载失败: {}", safeFileName, e); // 记录错误日志
            if (response.isCommitted()) {
                // 如果响应已提交，无法再返回错误信息
                log.warn("响应已提交，无法返回错误信息给客户端");
                return;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目打包下载失败");
        }
    }
}