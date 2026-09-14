package com.tmz.aicode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;

/**
 * 网页截图工具。
 *
 * 该工具会在后台启动 Chrome，等待网页完成首屏渲染，再将浏览器可见区域保存为压缩后的 JPG 文件。
 * 调用方只需要提供可访问的网页地址，不需要直接操作浏览器驱动。
 */
@Slf4j
public final class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;

    private static final int DEFAULT_HEIGHT = 900;

    private static final float COMPRESSION_QUALITY = 0.3F;

    private static final String IMAGE_SUFFIX = ".png";

    private static final String COMPRESSION_SUFFIX = "_compressed.jpg";

    /**
     * ChromeDriver 的一个实例对应一个浏览器会话，复用它可以避免每次截图都重新启动浏览器。
     */
    private static final WebDriver WEB_DRIVER = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);

    static {
        // 工具类不由 Spring 创建，因此通过 JVM 关闭钩子确保开发服务停止时浏览器进程也会退出。
        Runtime.getRuntime().addShutdownHook(new Thread(WebScreenshotUtils::closeWebDriver,
                "web-screenshot-driver-shutdown"));
    }

    private WebScreenshotUtils() {
        // 工具类只提供静态能力，不需要创建对象。
    }

    /**
     * 根据网页地址生成一张本地截图。
     *
     * 截图先以 PNG 保存，随后压缩成 JPG。原始 PNG 会在压缩成功后删除，最终只保留体积更小的文件。
     *
     * @param webUrl 浏览器可以访问的完整网页地址
     * @return 压缩后的截图绝对路径；网页访问或截图失败时返回 null
     */
    public static synchronized String saveWebPageScreenshot(String webUrl) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页地址不能为空");
            return null;
        }
        try {
            String rootPath = System.getProperty("user.dir")
                    + File.separator + "tmp"
                    + File.separator + "screenshots"
                    + File.separator + UUID.fastUUID().toString(true).substring(0, 8);
            FileUtil.mkdir(rootPath);

            String imageSavePath = rootPath + File.separator
                    + RandomUtil.randomNumbers(5) + IMAGE_SUFFIX;
            WEB_DRIVER.get(webUrl);
            waitForPageLoad(WEB_DRIVER);

            byte[] screenshotBytes = ((TakesScreenshot) WEB_DRIVER).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始网页截图已保存：{}", imageSavePath);

            String compressedImagePath = rootPath + File.separator
                    + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩后的网页截图已保存：{}", compressedImagePath);

            FileUtil.del(imageSavePath);
            return new File(compressedImagePath).getAbsolutePath();
        } catch (Exception e) {
            log.error("网页截图失败，地址：{}", webUrl, e);
            return null;
        }
    }

    /**
     * 初始化后台 Chrome 浏览器。
     *
     * WebDriverManager 会检查本机浏览器版本并准备匹配的驱动。无头模式不会弹出浏览器窗口，
     * 固定浏览器窗口尺寸则让不同调用生成的封面比例基本保持一致。
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

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
     * 等待浏览器完成页面加载。
     *
     * document.readyState 只能说明页面文档已经完成加载，额外等待两秒可以给异步脚本、字体和图片
     * 留出渲染时间。即使等待超时也继续截图，避免某个长期轮询的页面一直阻塞当前任务。
     */
    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(currentDriver -> "complete".equals(
                    ((JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
            Thread.sleep(2000);
            log.info("截图页面加载完成");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待截图页面渲染时线程被中断，将使用当前页面内容继续截图");
        } catch (Exception e) {
            log.warn("等待截图页面加载时出现异常，将使用当前页面内容继续截图", e);
        }
    }

    /**
     * 将 Selenium 返回的 PNG 字节写入临时文件。
     */
    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存网页截图失败，路径：{}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存网页截图失败");
        }
    }

    /**
     * 将原始截图压缩为 JPG，减少后续上传和页面加载时的文件体积。
     */
    private static void compressImage(String originalImagePath, String compressedImagePath) {
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩网页截图失败：{} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩网页截图失败");
        }
    }

    /**
     * 关闭复用的浏览器会话，释放 ChromeDriver 和 Chrome 子进程。
     */
    private static void closeWebDriver() {
        try {
            WEB_DRIVER.quit();
        } catch (Exception e) {
            log.warn("关闭网页截图浏览器时出现异常", e);
        }
    }
}
