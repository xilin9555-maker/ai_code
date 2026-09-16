package com.tmz.aicode.langgraph4j.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集 AI 服务接口。
 *
 * LangChain4j 会根据该接口创建运行时代理。代理读取系统提示词后，根据用户的网站需求
 * 选择并调用已经注册的图片工具，最终只返回便于后续节点使用的图片资源文本。
 * 这里使用 String 而不是 List 类型，是为了避免工具调用过程与结构化输出解析相互干扰。
 */
public interface ImageCollectionService {

    /**
     * 根据用户提示词收集网站所需的图片资源。
     *
     * 方法本身不包含具体实现，运行时代理只负责模型调用、工具选择、工具执行和结果收集。
     * 系统提示词从资源目录加载，使工具选择规则可以独立维护，不需要修改 Java 代码。
     *
     * @param userPrompt 用户对网站主题、内容、风格和图片需求的完整描述
     * @return 按类别整理的图片资源文本，内容包含图片名称、描述和可访问地址
     */
    @SystemMessage(fromResource = "prompt/image-collection-system-prompt.txt")
    String collectImages(@UserMessage String userPrompt);
}
