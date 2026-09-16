package com.tmz.aicode.langgraph4j.tools;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用当前 Spring 配置验证真实 Logo 图片生成和 COS 上传服务。
 *
 * 每次执行都会调用文生图模型并产生相应费用，返回的图片地址会输出到测试日志。
 */
@SpringBootTest
class LogoGeneratorToolTest {

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /** 配置有效时应返回一张上传到 COS 的 Logo 图片。 */
    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void generatesLogoFromConfiguredModel() {
        List<ImageResource> logos = logoGeneratorTool
                .generateLogos("科技服务品牌，现代简约风格，蓝紫渐变");

        assertNotNull(logos);
        assertFalse(logos.isEmpty());
        assertEquals(1, logos.size());
        ImageResource firstLogo = logos.getFirst();
        assertEquals(ImageCategoryEnum.LOGO, firstLogo.getCategory());
        assertEquals("科技服务品牌，现代简约风格，蓝紫渐变",
                firstLogo.getDescription());
        assertNotNull(firstLogo.getUrl());
        assertTrue(firstLogo.getUrl().startsWith("http"));
        assertTrue(firstLogo.getUrl().contains("/logo/"));
        System.out.println("Logo COS 地址：" + firstLogo.getUrl());
    }
}
