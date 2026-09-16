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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 根据关键词搜索页面内容图片。
 *
 * 工具负责调用 Pexels 搜索接口，并把外部响应转换为工作流统一使用的 ImageResource。
 * 搜索失败时返回空列表，使调用方可以继续处理或选择其他图片来源。
 */
@Slf4j
@Component
public class ImageSearchTool {

    /** Pexels 内容图片搜索接口。 */
    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /** 单次搜索返回的最大图片数量。 */
    private static final int SEARCH_COUNT = 3;

    /** 防止外部图片服务长时间无响应而阻塞整个工作流。 */
    private static final int REQUEST_TIMEOUT_MILLIS = 10_000;

    /** 从配置读取访问密钥，未配置时保持为空并跳过外部请求。 */
    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    /**
     * 搜索与页面内容相关的图片。
     *
     * @param query 搜索关键词，建议使用简短且具体的英文词组
     * @return 已转换的内容图片列表；参数无效或请求失败时返回空列表
     */
    @Tool("搜索与页面主题相关的内容图片，用于页面内容展示")
    public List<ImageResource> searchContentImages(
            @P("图片搜索关键词，使用简短且具体的英文词组") String query) {
        if (StrUtil.isBlank(query)) {
            log.warn("图片搜索关键词为空，跳过内容图片搜索");
            return List.of();
        }
        if (StrUtil.isBlank(pexelsApiKey)) {
            log.warn("未配置 Pexels API 密钥，跳过内容图片搜索");
            return List.of();
        }

        // 使用 try-with-resources 确保连接和响应流能够及时释放。
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query.trim())
                .form("per_page", SEARCH_COUNT)
                .form("page", 1)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .execute()) {
            if (!response.isOk()) {
                log.warn("Pexels 图片搜索失败，响应状态码：{}", response.getStatus());
                return List.of();
            }
            List<ImageResource> imageList = parseSearchResponse(response.body(), query.trim());
            log.info("内容图片搜索完成，关键词：{}，返回数量：{}", query, imageList.size());
            return imageList;
        } catch (Exception exception) {
            // 外部服务异常不应直接中断工作流，由上层根据空结果决定是否降级处理。
            log.error("Pexels 图片搜索调用失败：{}", exception.getMessage(), exception);
            return List.of();
        }
    }

    /**
     * 将 Pexels 搜索响应转换成内部图片资源。
     *
     * 解析逻辑单独保留，既便于测试，也能集中处理响应字段缺失的情况。没有 medium 地址的
     * 数据不能被页面直接使用，因此会被忽略；描述为空时使用搜索关键词作为后备描述。
     *
     * @param responseBody Pexels 返回的 JSON 文本
     * @param fallbackDescription 图片描述缺失时使用的关键词
     * @return 可以直接写入工作流状态的图片资源列表
     */
    static List<ImageResource> parseSearchResponse(
            String responseBody, String fallbackDescription) {
        List<ImageResource> imageList = new ArrayList<>();
        JSONObject result = JSONUtil.parseObj(responseBody);
        JSONArray photos = result.getJSONArray("photos");
        if (photos == null || photos.isEmpty()) {
            return imageList;
        }

        for (int index = 0; index < photos.size(); index++) {
            JSONObject photo = photos.getJSONObject(index);
            if (photo == null) {
                continue;
            }
            JSONObject source = photo.getJSONObject("src");
            if (source == null) {
                continue;
            }
            String imageUrl = source.getStr("medium");
            if (StrUtil.isBlank(imageUrl)) {
                continue;
            }
            String description = photo.getStr("alt");
            if (StrUtil.isBlank(description)) {
                description = fallbackDescription;
            }
            imageList.add(ImageResource.builder()
                    .category(ImageCategoryEnum.CONTENT)
                    .description(description)
                    .url(imageUrl)
                    .build());
        }
        return imageList;
    }
}
