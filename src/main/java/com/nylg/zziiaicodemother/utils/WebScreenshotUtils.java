package com.nylg.zziiaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 网页截图服务（单线程串行 + WebDriver 懒加载）
 */
@Service
@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;
    /** 单个截图任务的最大超时时间（含 ChromeDriver 初始化） */
    private static final Duration SCREENSHOT_TASK_TIMEOUT = Duration.ofMinutes(3);
    /** WebDriver 初始化单独超时，避免与页面加载混淆 */
    private static final Duration DRIVER_INIT_TIMEOUT = Duration.ofSeconds(90);
    /** npmmirror 镜像，避免访问 Google 下载 chromedriver */
    private static final String CHROME_DRIVER_MIRROR_URL =
            "https://registry.npmmirror.com/-/binary/chromedriver/";

    /**
     * 截图任务单线程执行器，保证 WebDriver 不会被并发使用
     */
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "screenshot-worker");
        thread.setDaemon(true);
        return thread;
    });

    /** 仅在 screenshot-worker 线程中访问 */
    private WebDriver webDriver;

    /**
     * 应用启动后预热 ChromeDriver，避免首次部署截图时长时间等待
     */
    @PostConstruct
    public void warmUpWebDriver() {
        screenshotExecutor.submit(() -> {
            try {
                getOrCreateWebDriver();
                log.info("Chrome WebDriver 预热完成");
            } catch (Exception e) {
                log.warn("Chrome WebDriver 预热失败，首次截图时将重试: {}", e.getMessage());
            }
        });
    }

    /**
     * 生成网页截图（提交到单线程队列执行）
     *
     * @param webUrl 网页 URL
     * @return 压缩后的截图文件路径，失败返回 null
     */
    public String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("webUrl 不能为空");
            return null;
        }
        try {
            return screenshotExecutor
                    .submit(() -> doSaveWebPageScreenshot(webUrl))
                    .get(SCREENSHOT_TASK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("网页截图任务超时: {}", webUrl, e);
            resetWebDriverAsync();
            return null;
        } catch (Exception e) {
            log.error("网页截图任务执行失败: {}", webUrl, e);
            resetWebDriverAsync();
            return null;
        }
    }

    /**
     * 实际截图逻辑，运行在 screenshot-worker 线程
     */
    private String doSaveWebPageScreenshot(String webUrl) {
        try {
            WebDriver driver = getOrCreateWebDriver();
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);

            final String imageSuffix = ".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + imageSuffix;

            String screenshotUrl = normalizeScreenshotUrl(webUrl);
            log.info("开始加载页面: {}", screenshotUrl);
            driver.get(screenshotUrl);
            waitForPageLoad(driver);

            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始图片保存成功: {}", imageSavePath);

            String compressImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + "_compressed.jpg";
            compressImage(imageSavePath, compressImagePath);
            log.info("压缩图片保存成功: {}", compressImagePath);

            FileUtil.del(imageSavePath);
            return compressImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            resetWebDriver();
            return null;
        }
    }

    /**
     * 将 localhost 统一替换为 127.0.0.1，避免 headless Chrome 解析 localhost 异常
     */
    private String normalizeScreenshotUrl(String webUrl) {
        return webUrl.replace("http://localhost", "http://127.0.0.1")
                .replace("https://localhost", "https://127.0.0.1");
    }

    /**
     * 懒加载 WebDriver（首次截图时在 screenshot-worker 线程中初始化）
     */
    private WebDriver getOrCreateWebDriver() {
        if (webDriver == null) {
            log.info("开始初始化 Chrome 浏览器...");
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            log.info("Chrome 浏览器初始化完成");
        }
        return webDriver;
    }

    private WebDriver initChromeDriver(int width, int height) {
        try {
            setupChromeDriver();
            ChromeOptions options = buildChromeOptions(width, height);
            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败: " + e.getMessage());
        }
    }

    /**
     * 配置 chromedriver：优先使用本地缓存，其次通过国内镜像下载
     */
    private void setupChromeDriver() {
        String configuredPath = System.getProperty("webdriver.chrome.driver");
        if (StrUtil.isNotBlank(configuredPath) && new File(configuredPath).exists()) {
            log.info("使用已配置的 chromedriver: {}", configuredPath);
            return;
        }

        String cachedDriver = findCachedChromeDriver();
        if (cachedDriver != null) {
            System.setProperty("webdriver.chrome.driver", cachedDriver);
            log.info("使用本地缓存 chromedriver: {}", cachedDriver);
            return;
        }

        configureWebDriverManagerMirror();
        String chromeVersion = detectLocalChromeVersion();
        log.info("检测到本地 Chrome 版本: {}", StrUtil.blankToDefault(chromeVersion, "未知"));

        WebDriverManager wdm = WebDriverManager.chromedriver()
                .useMirror()
                .timeout((int) DRIVER_INIT_TIMEOUT.getSeconds());

        if (StrUtil.isNotBlank(chromeVersion)) {
            wdm.browserVersion(chromeVersion);
        }

        wdm.setup();
        log.info("chromedriver 已通过镜像配置完成");
    }

    private void configureWebDriverManagerMirror() {
        System.setProperty("wdm.chromeDriverMirrorUrl", CHROME_DRIVER_MIRROR_URL);
        // 减少对外网版本检测服务的依赖
        System.setProperty("wdm.avoidBrowserDetection", "true");
        System.setProperty("wdm.avoidResolutionCache", "false");
    }

    private ChromeOptions buildChromeOptions(int width, int height) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-sync");
        options.addArguments("--no-first-run");
        options.addArguments("--no-default-browser-check");
        options.addArguments(String.format("--window-size=%d,%d", width, height));
        options.addArguments("--disable-extensions");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        String chromeBinary = findChromeBinary();
        if (chromeBinary != null) {
            options.setBinary(chromeBinary);
            log.info("使用 Chrome 可执行文件: {}", chromeBinary);
        }
        return options;
    }

    /**
     * 查找本地已缓存的 chromedriver（WebDriverManager / Selenium 缓存目录）
     */
    private String findCachedChromeDriver() {
        Path[] cacheRoots = {
                Paths.get(System.getProperty("user.home"), ".cache", "selenium", "chromedriver"),
                Paths.get(System.getProperty("user.home"), ".cache", "selenium"),
                Paths.get(System.getProperty("user.home"), ".m2", "repository", "webdriver", "chromedriver")
        };
        for (Path cacheRoot : cacheRoots) {
            String driverPath = findChromeDriverInDirectory(cacheRoot);
            if (driverPath != null) {
                return driverPath;
            }
        }
        return null;
    }

    private String findChromeDriverInDirectory(Path root) {
        if (!Files.exists(root)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(root, 6)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .filter(path -> {
                        String name = path.toLowerCase();
                        return name.endsWith("chromedriver.exe") || name.endsWith("/chromedriver") || name.endsWith("\\chromedriver");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.debug("扫描 chromedriver 缓存目录失败: {}", root, e);
            return null;
        }
    }

    /**
     * 检测本地 Chrome 版本，用于匹配 chromedriver
     */
    private String detectLocalChromeVersion() {
        String chromeBinary = findChromeBinary();
        if (chromeBinary == null) {
            return null;
        }
        try {
            Process process = new ProcessBuilder(chromeBinary, "--version")
                    .redirectErrorStream(true)
                    .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && line.contains("Chrome")) {
                    // Google Chrome 131.0.6778.86
                    return line.replaceAll(".*Chrome\\s+", "").trim().split("\\s+")[0];
                }
            }
        } catch (Exception e) {
            log.warn("检测 Chrome 版本失败", e);
        }
        return null;
    }

    private String findChromeBinary() {
        String[] candidates = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium-browser"
        };
        for (String candidate : candidates) {
            if (new File(candidate).exists()) {
                return candidate;
            }
        }
        return null;
    }

    private void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private void compressImage(String imagePath, String compressImagePath) {
        try {
            ImgUtil.compress(FileUtil.file(imagePath), FileUtil.file(compressImagePath), 0.3f);
        } catch (IORuntimeException e) {
            log.error("压缩图片失败: {} -> {}", imagePath, compressImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            wait.until(currentDriver ->
                    "complete".equals(((JavascriptExecutor) currentDriver)
                            .executeScript("return document.readyState"))
            );
            Thread.sleep(1500);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.warn("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    private void resetWebDriverAsync() {
        screenshotExecutor.submit(this::resetWebDriver);
    }

    private void resetWebDriver() {
        if (webDriver != null) {
            try {
                webDriver.quit();
                log.info("已重置 Chrome 浏览器实例");
            } catch (Exception e) {
                log.warn("关闭 Chrome 浏览器失败", e);
            } finally {
                webDriver = null;
            }
        }
    }

    @PreDestroy
    public void destroy() {
        screenshotExecutor.shutdown();
        try {
            if (!screenshotExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                screenshotExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            screenshotExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        resetWebDriver();
    }
}
