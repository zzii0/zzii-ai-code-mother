package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.constant.UserConstant;
import com.nylg.zziiaicodemother.core.builder.VueProjectBuilder;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.exception.ThrowUtils;
import com.nylg.zziiaicodemother.model.dto.app.AppVersionCompareRequest;
import com.nylg.zziiaicodemother.model.dto.app.AppVersionRollbackRequest;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.model.vo.AppVersionCompareVO;
import com.nylg.zziiaicodemother.model.vo.AppVersionVO;
import com.nylg.zziiaicodemother.service.AppService;
import com.nylg.zziiaicodemother.service.AppVersionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用版本管理服务实现：本地目录归档 + 对比 + 回退
 */
@Service
@Slf4j
public class AppVersionServiceImpl implements AppVersionService {

    private static final DateTimeFormatter VERSION_KEY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String VERSION_DISPLAY_FORMATTER = "yyyy-MM-dd HH:mm:ss";

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

    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    private static final Set<String> COMPARABLE_EXTENSIONS = Set.of(
            ".html", ".htm", ".css", ".js", ".ts", ".tsx", ".jsx",
            ".vue", ".json", ".md", ".txt", ".xml", ".yml", ".yaml",
            ".scss", ".less", ".svg", ".env.example"
    );

    @Resource
    @Lazy
    private AppService appService;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Override
    public String archiveCurrentVersion(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");

        File currentDir = getCurrentCodeDir(appId, codeGenTypeEnum);
        if (!hasMeaningfulContent(currentDir)) {
            return null;
        }

        String versionKey = LocalDateTime.now().format(VERSION_KEY_FORMATTER);
        File versionDir = getVersionDir(appId, codeGenTypeEnum, versionKey);
        FileUtil.mkdir(versionDir);
        copyFilteredContent(currentDir, versionDir);
        cleanupOldVersions(appId, codeGenTypeEnum);
        log.info("已归档应用版本, appId={}, versionKey={}, path={}", appId, versionKey, versionDir.getAbsolutePath());
        return versionKey;
    }

    @Override
    public List<AppVersionVO> listAppVersions(Long appId, User loginUser) {
        App app = getAndCheckPermission(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app);

        List<AppVersionVO> versions = new ArrayList<>();

        File currentDir = getCurrentCodeDir(appId, codeGenTypeEnum);
        if (hasMeaningfulContent(currentDir)) {
            AppVersionVO current = new AppVersionVO();
            current.setVersionKey(AppConstant.CURRENT_VERSION_KEY);
            current.setVersionName("当前版本");
            current.setCurrent(true);
            current.setCreateTime(LocalDateTime.now());
            versions.add(current);
        }

        File historyRoot = getAppVersionRoot(appId, codeGenTypeEnum);
        if (historyRoot.exists() && historyRoot.isDirectory()) {
            File[] versionDirs = historyRoot.listFiles(File::isDirectory);
            if (versionDirs != null) {
                Arrays.stream(versionDirs)
                        .sorted(Comparator.comparing(File::getName).reversed())
                        .forEach(dir -> {
                            AppVersionVO vo = new AppVersionVO();
                            vo.setVersionKey(dir.getName());
                            vo.setVersionName("历史版本 " + formatVersionKey(dir.getName()));
                            vo.setCurrent(false);
                            vo.setCreateTime(parseVersionKey(dir.getName()));
                            versions.add(vo);
                        });
            }
        }
        return versions;
    }

    @Override
    public AppVersionCompareVO compareAppVersion(AppVersionCompareRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long appId = request.getAppId();
        App app = getAndCheckPermission(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app);

        File oldVersionDir = resolveVersionDir(appId, codeGenTypeEnum, request.getOldVersionKey());
        File newVersionDir = resolveVersionDir(appId, codeGenTypeEnum, request.getNewVersionKey());
        ThrowUtils.throwIf(!oldVersionDir.exists() || !oldVersionDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "旧版本不存在");
        ThrowUtils.throwIf(!newVersionDir.exists() || !newVersionDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "新版本不存在");

        List<String> fileList = collectComparableFiles(oldVersionDir, newVersionDir);
        ThrowUtils.throwIf(fileList.isEmpty(), ErrorCode.NOT_FOUND_ERROR, "暂无可对比的代码文件");

        String filePath = StrUtil.blankToDefault(request.getFilePath(), fileList.get(0));
        ThrowUtils.throwIf(!fileList.contains(filePath), ErrorCode.PARAMS_ERROR, "文件路径无效");

        String oldContent = readVersionFile(oldVersionDir, filePath);
        String newContent = readVersionFile(newVersionDir, filePath);
        int[] diffCounts = countLineDiff(oldContent, newContent);

        AppVersionCompareVO compareVO = new AppVersionCompareVO();
        compareVO.setFileList(fileList);
        compareVO.setFilePath(filePath);
        compareVO.setOldContent(oldContent);
        compareVO.setNewContent(newContent);
        compareVO.setRemovals(diffCounts[0]);
        compareVO.setAdditions(diffCounts[1]);
        return compareVO;
    }

    @Override
    public Boolean rollbackAppVersion(AppVersionRollbackRequest request, User loginUser) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Long appId = request.getAppId();
        String versionKey = request.getVersionKey();
        ThrowUtils.throwIf(StrUtil.isBlank(versionKey), ErrorCode.PARAMS_ERROR, "版本标识不能为空");
        ThrowUtils.throwIf(AppConstant.CURRENT_VERSION_KEY.equals(versionKey),
                ErrorCode.PARAMS_ERROR, "不能回退到当前版本");

        App app = getAndCheckPermission(appId, loginUser);
        CodeGenTypeEnum codeGenTypeEnum = resolveCodeGenType(app);

        File targetVersionDir = getVersionDir(appId, codeGenTypeEnum, versionKey);
        ThrowUtils.throwIf(!hasMeaningfulContent(targetVersionDir),
                ErrorCode.NOT_FOUND_ERROR, "目标历史版本不存在");

        // 回退前先归档当前代码，避免丢失
        archiveCurrentVersion(appId, codeGenTypeEnum);

        File currentDir = getCurrentCodeDir(appId, codeGenTypeEnum);
        FileUtil.mkdir(currentDir);
        FileUtil.clean(currentDir);
        copyFilteredContent(targetVersionDir, currentDir);
        // Vue 项目预览依赖 dist，回退后异步重新构建
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            vueProjectBuilder.buildProjectAsync(currentDir.getAbsolutePath());
        }
        log.info("应用版本回退成功, appId={}, versionKey={}", appId, versionKey);
        return true;
    }

    private App getAndCheckPermission(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isOwner = app.getUserId().equals(loginUser.getId());
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isOwner && !isAdmin, ErrorCode.NO_AUTH_ERROR, "无权限访问该应用版本");
        return app;
    }

    private CodeGenTypeEnum resolveCodeGenType(App app) {
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.SYSTEM_ERROR, "代码生成类型错误");
        return codeGenTypeEnum;
    }

    private File getCurrentCodeDir(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        String dirName = codeGenTypeEnum.getValue() + "_" + appId;
        return new File(AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + dirName);
    }

    private File getAppVersionRoot(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        String dirName = codeGenTypeEnum.getValue() + "_" + appId;
        return new File(AppConstant.CODE_VERSION_ROOT_DIR + File.separator + dirName);
    }

    private File getVersionDir(Long appId, CodeGenTypeEnum codeGenTypeEnum, String versionKey) {
        return new File(getAppVersionRoot(appId, codeGenTypeEnum), versionKey);
    }

    private File resolveVersionDir(Long appId, CodeGenTypeEnum codeGenTypeEnum, String versionKey) {
        ThrowUtils.throwIf(StrUtil.isBlank(versionKey), ErrorCode.PARAMS_ERROR, "版本标识不能为空");
        if (AppConstant.CURRENT_VERSION_KEY.equals(versionKey)) {
            return getCurrentCodeDir(appId, codeGenTypeEnum);
        }
        // 防止路径穿越
        ThrowUtils.throwIf(versionKey.contains("..") || versionKey.contains("/") || versionKey.contains("\\"),
                ErrorCode.PARAMS_ERROR, "版本标识无效");
        return getVersionDir(appId, codeGenTypeEnum, versionKey);
    }

    private boolean hasMeaningfulContent(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return false;
        }
        List<File> files = listFilteredFiles(dir);
        return !files.isEmpty();
    }

    private void cleanupOldVersions(Long appId, CodeGenTypeEnum codeGenTypeEnum) {
        File historyRoot = getAppVersionRoot(appId, codeGenTypeEnum);
        if (!historyRoot.exists() || !historyRoot.isDirectory()) {
            return;
        }
        File[] versionDirs = historyRoot.listFiles(File::isDirectory);
        if (versionDirs == null || versionDirs.length <= AppConstant.MAX_APP_VERSION_COUNT) {
            return;
        }
        Arrays.stream(versionDirs)
                .sorted(Comparator.comparing(File::getName).reversed())
                .skip(AppConstant.MAX_APP_VERSION_COUNT)
                .forEach(dir -> {
                    boolean deleted = FileUtil.del(dir);
                    log.info("清理过期历史版本, path={}, deleted={}", dir.getAbsolutePath(), deleted);
                });
    }

    private void copyFilteredContent(File sourceDir, File targetDir) {
        List<File> files = listFilteredFiles(sourceDir);
        Path sourceRoot = sourceDir.toPath().toAbsolutePath().normalize();
        for (File sourceFile : files) {
            Path relative = sourceRoot.relativize(sourceFile.toPath().toAbsolutePath().normalize());
            File targetFile = targetDir.toPath().resolve(relative).toFile();
            FileUtil.mkParentDirs(targetFile);
            FileUtil.copy(sourceFile, targetFile, true);
        }
    }

    private List<File> listFilteredFiles(File rootDir) {
        if (rootDir == null || !rootDir.exists()) {
            return Collections.emptyList();
        }
        Path rootPath = rootDir.toPath().toAbsolutePath().normalize();
        List<File> result = new ArrayList<>();
        collectFiles(rootPath, rootDir, result);
        return result;
    }

    private void collectFiles(Path rootPath, File current, List<File> result) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!isPathAllowed(rootPath, child.toPath())) {
                continue;
            }
            if (child.isDirectory()) {
                collectFiles(rootPath, child, result);
            } else if (child.isFile()) {
                result.add(child);
            }
        }
    }

    private boolean isPathAllowed(Path rootPath, Path fullPath) {
        Path normalizedFull = fullPath.toAbsolutePath().normalize();
        if (!normalizedFull.startsWith(rootPath)) {
            return false;
        }
        Path relativePath = rootPath.relativize(normalizedFull);
        for (Path part : relativePath) {
            String nameLower = part.toString().toLowerCase(Locale.ROOT);
            if (IGNORED_NAMES.contains(nameLower)) {
                return false;
            }
            for (String ext : IGNORED_EXTENSIONS) {
                if (nameLower.endsWith(ext)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> collectComparableFiles(File oldDir, File newDir) {
        Set<String> fileSet = new HashSet<>();
        fileSet.addAll(listRelativeComparableFiles(oldDir));
        fileSet.addAll(listRelativeComparableFiles(newDir));
        return fileSet.stream().sorted().collect(Collectors.toList());
    }

    private List<String> listRelativeComparableFiles(File rootDir) {
        Path rootPath = rootDir.toPath().toAbsolutePath().normalize();
        return listFilteredFiles(rootDir).stream()
                .filter(file -> isComparableFile(file.getName()))
                .map(file -> rootPath.relativize(file.toPath().toAbsolutePath().normalize())
                        .toString()
                        .replace('\\', '/'))
                .collect(Collectors.toList());
    }

    private boolean isComparableFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        // 无扩展名的常见配置文件
        if ("dockerfile".equals(lower) || "makefile".equals(lower) || "readme".equals(lower)) {
            return true;
        }
        for (String ext : COMPARABLE_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return true;
            }
        }
        // 兜底：文本类小文件
        return !lower.contains(".") || lower.endsWith(".gitignore") || lower.endsWith(".npmrc");
    }

    private String readVersionFile(File versionDir, String relativePath) {
        Path root = versionDir.toPath().toAbsolutePath().normalize();
        Path filePath = root.resolve(relativePath.replace('/', File.separatorChar)).normalize();
        ThrowUtils.throwIf(!filePath.startsWith(root), ErrorCode.PARAMS_ERROR, "文件路径非法");
        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return "";
        }
        return FileUtil.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 基于最长公共子序列统计删除行 / 新增行
     *
     * @return int[0]=removals, int[1]=additions
     */
    private int[] countLineDiff(String oldContent, String newContent) {
        List<String> oldLines = splitLines(oldContent);
        List<String> newLines = splitLines(newContent);
        int oldLen = oldLines.size();
        int newLen = newLines.size();
        int[][] dp = new int[oldLen + 1][newLen + 1];
        for (int i = oldLen - 1; i >= 0; i--) {
            for (int j = newLen - 1; j >= 0; j--) {
                if (oldLines.get(i).equals(newLines.get(j))) {
                    dp[i][j] = dp[i + 1][j + 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }
        int i = 0;
        int j = 0;
        int removals = 0;
        int additions = 0;
        while (i < oldLen && j < newLen) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                i++;
                j++;
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                removals++;
                i++;
            } else {
                additions++;
                j++;
            }
        }
        removals += oldLen - i;
        additions += newLen - j;
        return new int[]{removals, additions};
    }

    private List<String> splitLines(String content) {
        if (StrUtil.isEmpty(content)) {
            return new ArrayList<>();
        }
        // 保留末尾空行语义：按 \n 切分
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");
        return new ArrayList<>(Arrays.asList(normalized.split("\n", -1)));
    }

    private LocalDateTime parseVersionKey(String versionKey) {
        try {
            return LocalDateTime.parse(versionKey, VERSION_KEY_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String formatVersionKey(String versionKey) {
        LocalDateTime time = parseVersionKey(versionKey);
        if (time == null) {
            return versionKey;
        }
        return time.format(DateTimeFormatter.ofPattern(VERSION_DISPLAY_FORMATTER));
    }
}
