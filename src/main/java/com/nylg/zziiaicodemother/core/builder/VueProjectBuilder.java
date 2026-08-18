package com.nylg.zziiaicodemother.core.builder;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class VueProjectBuilder {

    private static final Set<String> BUILD_IGNORED_DIRS = Set.of(
            "node_modules", "dist", ".git", ".idea", ".vscode", "target"
    );

    /**
     * 同一项目路径的构建锁，避免预览异步构建与部署同步构建并发冲突
     */
    private final ConcurrentHashMap<String, ReentrantLock> buildLocks = new ConcurrentHashMap<>();

    /**
     * 异步构建项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 等待当前项目路径上正在进行的构建完成
     */
    public void awaitBuildComplete(String projectPath) {
        String lockKey = new File(projectPath).getAbsolutePath();
        ReentrantLock lock = buildLocks.get(lockKey);
        if (lock == null) {
            return;
        }
        lock.lock();
        lock.unlock();
    }

    /**
     * 部署前确保 dist 与源码一致：先等待预览构建结束，再按需重建
     */
    public boolean ensureBuiltForDeploy(String projectPath) {
        awaitBuildComplete(projectPath);
        File projectDir = new File(projectPath);
        File distDir = new File(projectDir, "dist");
        File indexHtml = new File(distDir, "index.html");
        if (!indexHtml.exists() || isSourceNewerThanDist(projectDir, distDir)) {
            log.info("部署前需要重新构建 Vue 项目: {}", projectPath);
            return buildProjectForDeploy(projectPath);
        }
        log.info("部署复用预览 dist，跳过重复构建: {}", distDir.getAbsolutePath());
        return true;
    }

    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        return executeWithLock(projectPath, false).isSuccess();
    }

    /**
     * 构建 Vue 项目并返回详细结果（含错误定位）
     */
    public BuildResult buildProjectWithResult(String projectPath) {
        return executeWithLock(projectPath, false);
    }

    /**
     * 部署场景构建：清理 dist 与 Vite 缓存后强制重建
     */
    public boolean buildProjectForDeploy(String projectPath) {
        return executeWithLock(projectPath, true).isSuccess();
    }

    private BuildResult executeWithLock(String projectPath, boolean cleanDist) {
        String lockKey = new File(projectPath).getAbsolutePath();
        ReentrantLock lock = buildLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        try {
            return doBuildProject(projectPath, cleanDist);
        } finally {
            lock.unlock();
        }
    }

    private BuildResult doBuildProject(String projectPath, boolean cleanDist) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return BuildResult.fail(-1, "", null, null, "项目目录不存在: " + projectPath);
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return BuildResult.fail(-1, "", "package.json", null, "package.json 文件不存在");
        }
        if (cleanDist) {
            cleanBuildArtifacts(projectDir);
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        File nodeModules = new File(projectDir, "node_modules");
        if (!nodeModules.exists() || !nodeModules.isDirectory()) {
            BuildResult installResult = executeNpmInstall(projectDir);
            if (!installResult.isSuccess()) {
                log.error("npm install 执行失败");
                return installResult;
            }
        } else {
            log.info("检测到 node_modules，跳过 npm install");
        }
        BuildResult buildResult = executeNpmBuild(projectDir);
        if (!buildResult.isSuccess()) {
            log.error("npm run build 执行失败: file={}, line={}, message={}",
                    buildResult.getErrorFile(), buildResult.getErrorLine(), buildResult.getErrorMessage());
            return buildResult;
        }
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("dist 目录未生成: {}", distDir.getAbsolutePath());
            return BuildResult.fail(-1, buildResult.getOutput(), null, null,
                    "dist 目录未生成: " + distDir.getAbsolutePath());
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return BuildResult.ok(buildResult.getOutput());
    }

    private void cleanBuildArtifacts(File projectDir) {
        File distDir = new File(projectDir, "dist");
        if (distDir.exists()) {
            FileUtil.del(distDir);
            log.info("已清理 dist 目录: {}", distDir.getAbsolutePath());
        }
        File viteCache = new File(projectDir, "node_modules/.vite");
        if (viteCache.exists()) {
            FileUtil.del(viteCache);
            log.info("已清理 Vite 缓存: {}", viteCache.getAbsolutePath());
        }
    }

    private boolean isSourceNewerThanDist(File projectDir, File distDir) {
        long sourceTime = getLatestModifiedTime(projectDir, true);
        long distTime = getLatestModifiedTime(distDir, false);
        log.info("源码最新修改时间={}, dist 最新修改时间={}", sourceTime, distTime);
        return sourceTime > distTime;
    }

    private long getLatestModifiedTime(File root, boolean excludeBuildDirs) {
        if (root == null || !root.exists()) {
            return 0L;
        }
        long latest = root.lastModified();
        if (!root.isDirectory()) {
            return latest;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return latest;
        }
        for (File child : children) {
            if (excludeBuildDirs && child.isDirectory() && BUILD_IGNORED_DIRS.contains(child.getName())) {
                continue;
            }
            latest = Math.max(latest, getLatestModifiedTime(child, excludeBuildDirs));
        }
        return latest;
    }

    /**
     * 执行命令并捕获合并后的输出
     */
    private BuildResult executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"));
            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return BuildResult.fail(-1, output.toString(), null, null,
                        "命令执行超时（" + timeoutSeconds + "秒）: " + command);
            }
            int exitCode = process.exitValue();
            String outputText = output.toString();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return BuildResult.ok(outputText);
            }
            log.error("命令执行失败，退出码: {}", exitCode);
            if (StrUtil.isNotBlank(outputText)) {
                log.error("命令输出:\n{}", truncateForLog(outputText));
            }
            return ViteBuildErrorParser.parseFailure(exitCode, outputText, workingDir);
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return BuildResult.fail(-1, "", null, null, "执行命令失败: " + e.getMessage());
        }
    }

    private static String truncateForLog(String text) {
        if (text == null) {
            return "";
        }
        int max = 4000;
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n...(truncated)";
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private BuildResult executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300);
    }

    private BuildResult executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180);
    }
}
