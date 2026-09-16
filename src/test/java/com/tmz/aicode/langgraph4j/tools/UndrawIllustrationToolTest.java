package com.tmz.aicode.langgraph4j.tools;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证插画搜索的真实页面集成、结构化结果转换和无效输入处理。
 */
class UndrawIllustrationToolTest {

    private final UndrawIllustrationTool undrawIllustrationTool =
            new UndrawIllustrationTool();

    /**
     * 公开搜索页面应返回可直接使用的插画资源。
     *
     * 该站点的网络连通性存在波动，因此真实调用需要通过系统属性显式开启，避免普通构建随机失败。
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "undraw.integration-test", matches = "true")
    void searchesIllustrationsFromPublicPage() {
        List<ImageResource> illustrations =
                undrawIllustrationTool.searchIllustrations("happy");

        assertNotNull(illustrations);
        assertFalse(illustrations.isEmpty());
        assertTrue(illustrations.size() <= 12);

        ImageResource firstIllustration = illustrations.getFirst();
        assertEquals(ImageCategoryEnum.ILLUSTRATION,
                firstIllustration.getCategory());
        assertNotNull(firstIllustration.getDescription());
        assertFalse(firstIllustration.getDescription().isBlank());
        assertNotNull(firstIllustration.getUrl());
        assertTrue(firstIllustration.getUrl().startsWith("http"));
    }

    /** 页面中的 Next.js 数据脚本应被正确定位并解析。 */
    @Test
    void extractsIllustrationsFromSearchPage() {
        String pageContent = """
                <html>
                  <body>
                    <script id="__NEXT_DATA__" type="application/json">
                      {
                        "props": {
                          "pageProps": {
                            "initialResults": [
                              {
                                "title": "Creative work",
                                "media": "https://cdn.example.com/creative-work.svg"
                              },
                              {
                                "title": "",
                                "media": "https://cdn.example.com/fallback-title.svg"
                              },
                              {
                                "title": "Missing media"
                              }
                            ]
                          }
                        }
                      }
                    </script>
                  </body>
                </html>
                """;

        List<ImageResource> illustrations =
                UndrawIllustrationTool.parseSearchPage(pageContent);

        assertEquals(2, illustrations.size());
        assertEquals(ImageCategoryEnum.ILLUSTRATION,
                illustrations.getFirst().getCategory());
        assertEquals("Creative work", illustrations.getFirst().getDescription());
        assertEquals("https://cdn.example.com/creative-work.svg",
                illustrations.getFirst().getUrl());
        assertEquals("插画", illustrations.get(1).getDescription());
    }

    /** 空页面、缺少数据脚本和空关键词都应安全返回空列表。 */
    @Test
    void returnsEmptyListForInvalidInput() {
        assertTrue(UndrawIllustrationTool.parseSearchPage("").isEmpty());
        assertTrue(UndrawIllustrationTool
                .parseSearchPage("<html><body></body></html>").isEmpty());
        assertTrue(undrawIllustrationTool.searchIllustrations("  ").isEmpty());
    }
}
