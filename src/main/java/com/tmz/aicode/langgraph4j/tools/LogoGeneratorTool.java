package com.tmz.aicode.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesis;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisParam;
import com.alibaba.dashscope.aigc.imagesynthesis.ImageSynthesisResult;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import com.tmz.aicode.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据品牌描述生成 Logo 图片。
 *
 * 工具通过 DashScope 文生图模型生成一张方形图片，将临时图片上传到 COS 后转换成工作流
 * 统一使用的 ImageResource。生成失败时返回空列表，便于调用方继续处理或选择降级方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogoGeneratorTool {

    /** 方形 Logo 的固定输出尺寸。 */
    private static final String IMAGE_SIZE = "512*512";

    /** 单次只生成一张，避免在无法自动筛选时产生额外费用。 */
    private static final int IMAGE_COUNT = 1;

    /** 下载模型临时图片的超时时间，避免网络波动导致过早失败。 */
    private static final int IMAGE_DOWNLOAD_TIMEOUT_MILLIS = 120_000;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.image-model:wan2.2-t2i-flash}")
    private String imageModel;

    private final CosManager cosManager;

    /**
     * 根据名称、行业和风格等信息生成 Logo。
     *
     * @param description Logo 的品牌、行业、配色和视觉风格描述
     * @return 上传成功后的 Logo 图片列表；参数无效、生成失败或上传失败时返回空列表
     */
    @Tool("根据描述生成 Logo 设计图片，用于页面品牌标识")
    public List<ImageResource> generateLogos(
            @P("Logo 设计描述，包括名称、行业、配色和风格，内容尽量具体")
            String description) {
        if (StrUtil.isBlank(description)) {
            log.warn("Logo 设计描述为空，跳过图片生成");
            return List.of();
        }
        if (StrUtil.isBlank(dashScopeApiKey)) {
            log.warn("未配置 DashScope API Key，跳过 Logo 图片生成");
            return List.of();
        }

        String normalizedDescription = description.trim();
        try {
            String logoPrompt = "生成 Logo，Logo 中禁止包含任何文字！Logo 介绍："
                    + normalizedDescription;
            ImageSynthesisParam param = ImageSynthesisParam.builder()
                    .apiKey(dashScopeApiKey)
                    .model(imageModel)
                    .prompt(logoPrompt)
                    .size(IMAGE_SIZE)
                    .n(IMAGE_COUNT)
                    .build();

            ImageSynthesisResult result = callImageSynthesis(param);
            List<ImageResource> logoList = convertResults(result, normalizedDescription);
            for (ImageResource logo : logoList) {
                logo.setUrl(uploadToCos(logo.getUrl()));
            }
            logoList.removeIf(logo -> StrUtil.isBlank(logo.getUrl()));
            log.info("Logo 图片生成完成，返回数量：{}", logoList.size());
            return logoList;
        } catch (Exception exception) {
            // 模型或网络异常不直接中断图片收集流程，由上层根据空列表决定是否降级。
            log.error("Logo 图片生成失败：{}", exception.getMessage(), exception);
            return List.of();
        }
    }

    /** 下载模型生成的临时图片，上传到 COS 后删除本地文件。 */
    private String uploadToCos(String imageUrl) {
        File logoFile = FileUtil.createTempFile("generated_logo_", ".png", true);
        try (HttpResponse response = HttpRequest.get(imageUrl)
                .timeout(IMAGE_DOWNLOAD_TIMEOUT_MILLIS)
                .execute()) {
            if (!response.isOk()) {
                throw new IllegalStateException(
                        "Logo 临时图片下载失败，响应状态码：" + response.getStatus());
            }
            response.writeBody(logoFile);
            String keyName = "/logo/%s/%s".formatted(
                    RandomUtil.randomString(5), logoFile.getName());
            return cosManager.uploadFile(keyName, logoFile);
        } finally {
            FileUtil.del(logoFile);
        }
    }

    /**
     * 执行 DashScope 文生图请求。
     *
     * 单独封装 SDK 调用点，便于测试请求参数和响应转换而不产生真实费用。
     */
    ImageSynthesisResult callImageSynthesis(ImageSynthesisParam param)
            throws ApiException, NoApiKeyException {
        return new ImageSynthesis().call(param);
    }

    /**
     * 将模型响应中的有效 URL 转换成统一图片资源。
     *
     * @param result 模型返回结果
     * @param description 原始 Logo 描述
     * @return 已过滤空地址的 Logo 资源列表
     */
    static List<ImageResource> convertResults(
            ImageSynthesisResult result, String description) {
        List<ImageResource> logoList = new ArrayList<>();
        if (result == null
                || result.getOutput() == null
                || result.getOutput().getResults() == null) {
            return logoList;
        }

        for (Map<String, String> imageResult : result.getOutput().getResults()) {
            if (imageResult == null) {
                continue;
            }
            String imageUrl = imageResult.get("url");
            if (StrUtil.isBlank(imageUrl)) {
                continue;
            }
            logoList.add(ImageResource.builder()
                    .category(ImageCategoryEnum.LOGO)
                    .description(description)
                    .url(imageUrl)
                    .build());
        }
        return logoList;
    }
}
