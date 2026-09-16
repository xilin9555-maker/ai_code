package com.tmz.aicode.langgraph4j.tools;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证内容图片搜索接口集成、响应解析和请求前校验。
 *
 * 接口集成测试使用当前 Spring 配置访问真实服务；其余测试使用固定数据，帮助快速定位失败发生在
 * 外部调用还是内部转换阶段。
 */
@SpringBootTest
class ImageSearchToolTest {

    @Resource
    private ImageSearchTool imageSearchTool;

    /** 配置正确时应能搜索到可供页面直接使用的内容图片。 */
    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void searchesContentImagesFromConfiguredService() {
        List<ImageResource> images =
                imageSearchTool.searchContentImages("technology workspace");

        assertNotNull(images);
        assertFalse(images.isEmpty());
        assertTrue(images.size() <= 12);

        ImageResource firstImage = images.getFirst();
        assertEquals(ImageCategoryEnum.CONTENT, firstImage.getCategory());
        assertNotNull(firstImage.getDescription());
        assertFalse(firstImage.getDescription().isBlank());
        assertNotNull(firstImage.getUrl());
        assertTrue(firstImage.getUrl().startsWith("http"));
    }

    /** 有效图片应转换为统一资源模型，缺少描述时使用关键词作为后备值。 */
    @Test
    void parsesValidImagesAndSkipsEntriesWithoutUrl() {
        String responseBody = """
                {
                  "photos": [
                    {
                      "alt": "Developer working at a desk",
                      "src": {"medium": "https://images.example.com/workspace.jpg"}
                    },
                    {
                      "alt": "",
                      "src": {"medium": "https://images.example.com/keyboard.jpg"}
                    },
                    {
                      "alt": "Image without a usable address",
                      "src": {"small": "https://images.example.com/small.jpg"}
                    }
                  ]
                }
                """;

        List<ImageResource> images =
                ImageSearchTool.parseSearchResponse(responseBody, "technology workspace");

        assertEquals(2, images.size());
        assertEquals(ImageCategoryEnum.CONTENT, images.getFirst().getCategory());
        assertEquals("Developer working at a desk", images.getFirst().getDescription());
        assertEquals("https://images.example.com/workspace.jpg",
                images.getFirst().getUrl());
        assertEquals("technology workspace", images.get(1).getDescription());
    }

    /** 响应没有图片数组时应返回空列表。 */
    @Test
    void returnsEmptyListWhenResponseHasNoPhotos() {
        List<ImageResource> images =
                ImageSearchTool.parseSearchResponse("{}", "portfolio");

        assertTrue(images.isEmpty());
    }

    /** 未配置访问密钥时应在发送网络请求前返回空列表。 */
    @Test
    void skipsRequestWhenApiKeyIsMissing() {
        ImageSearchTool toolWithoutApiKey = new ImageSearchTool();
        ReflectionTestUtils.setField(toolWithoutApiKey, "pexelsApiKey", " ");

        List<ImageResource> images =
                toolWithoutApiKey.searchContentImages("technology workspace");

        assertTrue(images.isEmpty());
    }

    /** 空关键词不应触发外部请求。 */
    @Test
    void skipsRequestWhenQueryIsBlank() {
        ImageSearchTool toolWithApiKey = new ImageSearchTool();
        ReflectionTestUtils.setField(toolWithApiKey, "pexelsApiKey", "configured-key");

        List<ImageResource> images = toolWithApiKey.searchContentImages("  ");

        assertTrue(images.isEmpty());
    }
}
