package com.nylg.zziiaicodemother.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
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

import java.io.File;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 网页截图服务（单线程串行 + WebDriver 懒加载）
 */
@Service
@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 900;
    // 单个截图任务的最大超时时间（2分钟）
    private static final Duration SCREENSHOT_TASK_TIMEOUT = Duration.ofMinutes(2);

    /**
     * 截图任务单线程执行器，保证 WebDriver 不会被并发使用
     * 使用 newSingleThreadExecutor 创建仅包含一个线程的线程池
     */
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor(r -> {
        // 自定义线程工厂，设置线程名称和守护模式
        Thread thread = new Thread(r, "screenshot-worker");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 仅在 screenshot-worker 线程中访问
     */
    private WebDriver webDriver;

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
            // 向单线程执行器提交任务，并获取 Future 对象，然后阻塞等待结果，超时时间为 SCREENSHOT_TASK_TIMEOUT
            return screenshotExecutor
                    .submit(() -> doSaveWebPageScreenshot(webUrl))
                    .get(SCREENSHOT_TASK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("网页截图任务超时: {}", webUrl, e);
            return null;
        } catch (Exception e) {
            log.error("网页截图任务执行失败: {}", webUrl, e);
            return null;
        }
    }

    /**
     * 实际截图逻辑，运行在 screenshot-worker 线程
     * 包含：获取 WebDriver、创建临时目录、加载页面、截图、压缩、删除原图等步骤
     *
     * @param webUrl 网页 URL
     * @return 压缩后的图片路径，失败返回 null
     */
    private String doSaveWebPageScreenshot(String webUrl) {
        try {
            // 获取或创建 WebDriver 实例（懒加载）
            WebDriver driver = getOrCreateWebDriver();
            // 构建临时根目录：user.dir/tmp/screenshots/随机8位UUID
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);

            // 原始截图格式为 PNG
            final String imageSuffix = ".png";
            // 生成原始截图保存路径：根目录/5位随机数字.png
            String imageSavePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + imageSuffix;

            // 导航到目标 URL
            driver.get(webUrl);
            // 等待页面加载完成
            waitForPageLoad(driver);
            // 保存原始截图
            byte[] screenshotBytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始图片保存成功: {}", imageSavePath);
            // 压缩图片
            String compressImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + "_compressed.jpg";
            compressImage(imageSavePath, compressImagePath);
            log.info("压缩图片保存成功: {}", compressImagePath);
            // 删除原始图片
            FileUtil.del(imageSavePath);
            return compressImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }

    /**
     * 懒加载 WebDriver（首次截图时在 screenshot-worker 线程中初始化）
     */
    private WebDriver getOrCreateWebDriver() {
        if (webDriver == null) {
            log.info("首次截图，开始初始化 Chrome 浏览器...");
            webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
            log.info("Chrome 浏览器初始化完成");
        }
        return webDriver;
    }

    private WebDriver initChromeDriver(int width, int height) {
        try {
            System.setProperty("wdm.chromeDriverMirrorUrl",
                    "https://registry.npmmirror.com/binary.html?path=chromedriver");
            WebDriverManager.chromedriver().useMirror().timeout(60).setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");

            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 将字节数组保存为图片文件
     *
     * @param imageBytes 图片字节数据
     * @param imagePath  目标文件路径
     */
    private void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    /**
     * 压缩图片
     *
     * @param imagePath          原始图片路径
     * @param compressImagePath  压缩图片路径
     */
    private void compressImage(String imagePath, String compressImagePath) {
        try {
            ImgUtil.compress(FileUtil.file(imagePath), FileUtil.file(compressImagePath), 0.3f);
        } catch (IORuntimeException e) {
            log.error("压缩图片失败: {} -> {}", imagePath, compressImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    /**
     * 等待页面加载完成
     *
     */
    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(currentDriver ->
                    "complete".equals(((JavascriptExecutor) currentDriver)
                            .executeScript("return document.readyState"))
            );
            Thread.sleep(2000);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.warn("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    /**
     * Spring 容器销毁时调用的清理方法
     * 关闭线程池并释放 WebDriver 资源
     */
    @PreDestroy
    public void destroy() {
        // 关闭线程池，不再接受新任务
        screenshotExecutor.shutdown();
        try {
            // 等待线程池中任务执行完毕，最多等待 30 秒
            if (!screenshotExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                screenshotExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            screenshotExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (webDriver != null) {
            try {
                webDriver.quit();
                log.info("Chrome 浏览器已关闭");
            } catch (Exception e) {
                log.warn("关闭 Chrome 浏览器失败", e);
            }
        }
    }
}
