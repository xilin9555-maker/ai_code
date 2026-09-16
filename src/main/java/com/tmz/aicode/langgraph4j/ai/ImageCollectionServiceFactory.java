package com.tmz.aicode.langgraph4j.ai;

import com.tmz.aicode.langgraph4j.tools.ImageSearchTool;
import com.tmz.aicode.langgraph4j.tools.LogoGeneratorTool;
import com.tmz.aicode.langgraph4j.tools.MermaidDiagramTool;
import com.tmz.aicode.langgraph4j.tools.UndrawIllustrationTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图片收集 AI 服务创建工厂。
 *
 * 该配置类把项目中现有的对话模型和四类图片工具组合成 ImageCollectionService。
 * 模型只负责根据用户需求决定调用哪些工具，各工具负责执行搜索、绘制或生成操作，
 * 服务代理负责收集工具返回的图片资源。
 */
@Slf4j
@Configuration
public class ImageCollectionServiceFactory {

    /** 复用项目已经配置的非流式对话模型，用于分析需求和调度工具。 */
    @Resource
    private ChatModel chatModel;

    /** 搜索与网站主题相关的内容图片。 */
    @Resource
    private ImageSearchTool imageSearchTool;

    /** 搜索适合页面装饰和概念表达的插画。 */
    @Resource
    private UndrawIllustrationTool undrawIllustrationTool;

    /** 把模型生成的 Mermaid 代码转换为可访问的架构图。 */
    @Resource
    private MermaidDiagramTool mermaidDiagramTool;

    /** 根据品牌描述生成 Logo，并返回上传后的图片地址。 */
    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    /**
     * 创建图片收集 AI 服务。
     *
     * tools 的注册顺序与系统提示词中的工具说明保持一致，便于维护时逐项核对。
     * AiServices 会为接口生成代理，并在模型返回工具调用请求时自动分发到对应 Java 方法。
     *
     * @return 已绑定对话模型和全部图片工具的服务代理
     */
    @Bean
    public ImageCollectionService createImageCollectionService() {
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(chatModel)
                .tools(
                        imageSearchTool,
                        undrawIllustrationTool,
                        mermaidDiagramTool,
                        logoGeneratorTool
                )
                .build();
    }
}
