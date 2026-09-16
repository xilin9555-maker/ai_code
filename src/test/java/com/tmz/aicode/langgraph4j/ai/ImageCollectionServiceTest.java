package com.tmz.aicode.langgraph4j.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 图片收集 AI 服务手动集成测试。
 *
 * 该测试保留真实服务调用，仅在显式设置 runRealImageCollectionTest=true 时执行。
 * 默认运行测试套件时会跳过，避免自动调用真实模型和图片生成服务。
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "runRealImageCollectionTest",
        matches = "true",
        disabledReason = "仅允许手动启用真实图片收集测试"
)
class ImageCollectionServiceTest {

    @Resource
    private ImageCollectionService imageCollectionService;

    @Test
    void testTechWebsiteImageCollection() {
        // 测试技术网站的图片收集结果。
        String result = imageCollectionService.collectImages("创建一个技术博客网站，需要展示编程知识和系统架构");
        Assertions.assertNotNull(result);
        System.out.println("技术网站收集到的图片: " + result);
    }

    @Test
    void testEcommerceWebsiteImageCollection() {
        // 测试电商网站的图片收集结果。
        String result = imageCollectionService.collectImages("创建一个电商购物网站，需要展示商品和品牌形象");
        Assertions.assertNotNull(result);
        System.out.println("电商网站收集到的图片: " + result);
    }
}
