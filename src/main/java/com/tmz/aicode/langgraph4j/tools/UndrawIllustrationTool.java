package com.tmz.aicode.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 unDraw 搜索可用于页面美化和说明的插画。
 *
 * unDraw 的搜索结果保存在页面内的 Next.js 数据脚本中。工具请求稳定的公开搜索路径，提取
 * pageProps.initialResults，再将标题和媒体地址转换为工作流统一使用的图片资源。
 */
@Slf4j
@Component
public class UndrawIllustrationTool {

    /** 公开搜索页面地址，路径参数为经过编码的搜索关键词。 */
    private static final String UNDRAW_SEARCH_URL = "https://undraw.co/search/%s";

    /** 单次最多向工作流返回的插画数量。 */
    private static final int SEARCH_COUNT = 12;

    /** 防止外部站点长时间无响应而阻塞工作流。 */
    private static final int REQUEST_TIMEOUT_MILLIS = 15_000;

    /** 匹配搜索页面中保存结构化结果的 Next.js 数据脚本。 */
    private static final Pattern NEXT_DATA_PATTERN = Pattern.compile(
            "<script[^>]*id=[\\\"']__NEXT_DATA__[\\\"'][^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * 根据关键词搜索插画。
     *
     * @param query 搜索关键词，建议使用简短且具体的英文单词
     * @return 最多 12 个插画资源；参数无效、页面不可用或解析失败时返回空列表
     */
    @Tool("搜索插画图片，用于页面美化、装饰和概念说明")
    public List<ImageResource> searchIllustrations(
            @P("插画搜索关键词，使用简短且具体的英文单词") String query) {
        if (StrUtil.isBlank(query)) {
            log.warn("插画搜索关键词为空，跳过搜索");
            return List.of();
        }

        String normalizedQuery = query.trim();
        String encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String searchUrl = UNDRAW_SEARCH_URL.formatted(encodedQuery);

        // 使用 try-with-resources 确保连接和响应流能够及时释放。
        try (HttpResponse response = HttpRequest.get(searchUrl)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .execute()) {
            if (!response.isOk()) {
                log.warn("unDraw 插画搜索失败，响应状态码：{}", response.getStatus());
                return List.of();
            }
            List<ImageResource> imageList = parseSearchPage(response.body());
            log.info("插画搜索完成，关键词：{}，返回数量：{}", normalizedQuery, imageList.size());
            return imageList;
        } catch (Exception exception) {
            // 外部页面异常不直接中断工作流，由上层根据空结果决定是否继续或降级。
            log.error("unDraw 插画搜索失败：{}", exception.getMessage(), exception);
            return List.of();
        }
    }

    /**
     * 从搜索页面中提取 Next.js 数据并转换插画列表。
     *
     * @param pageContent 搜索页面 HTML
     * @return 转换后的插画资源列表，找不到数据脚本时返回空列表
     */
    static List<ImageResource> parseSearchPage(String pageContent) {
        if (StrUtil.isBlank(pageContent)) {
            return List.of();
        }
        Matcher matcher = NEXT_DATA_PATTERN.matcher(pageContent);
        if (!matcher.find()) {
            return List.of();
        }
        return parseSearchResponse(matcher.group(1));
    }

    /**
     * 将结构化搜索结果转换为内部图片资源。
     *
     * @param responseBody Next.js 数据脚本中的 JSON 文本
     * @return 最多 12 个带有效媒体地址的插画资源
     */
    static List<ImageResource> parseSearchResponse(String responseBody) {
        List<ImageResource> imageList = new ArrayList<>();
        JSONObject result = JSONUtil.parseObj(responseBody);
        JSONObject props = result.getJSONObject("props");
        JSONObject pageProps = props == null
                ? result.getJSONObject("pageProps")
                : props.getJSONObject("pageProps");
        if (pageProps == null) {
            return imageList;
        }
        JSONArray initialResults = pageProps.getJSONArray("initialResults");
        if (initialResults == null || initialResults.isEmpty()) {
            return imageList;
        }

        int actualCount = Math.min(SEARCH_COUNT, initialResults.size());
        for (int index = 0; index < actualCount; index++) {
            JSONObject illustration = initialResults.getJSONObject(index);
            if (illustration == null) {
                continue;
            }
            String mediaUrl = illustration.getStr("media");
            if (StrUtil.isBlank(mediaUrl)) {
                continue;
            }
            String title = illustration.getStr("title");
            if (StrUtil.isBlank(title)) {
                title = "插画";
            }
            imageList.add(ImageResource.builder()
                    .category(ImageCategoryEnum.ILLUSTRATION)
                    .description(title)
                    .url(mediaUrl)
                    .build());
        }
        return imageList;
    }
}
