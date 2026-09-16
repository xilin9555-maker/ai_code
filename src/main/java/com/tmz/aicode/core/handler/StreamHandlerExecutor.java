package com.tmz.aicode.core.handler;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.model.vo.GenerationStreamEvent;
import com.tmz.aicode.service.ChatHistoryService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 根据代码生成类型选择对应的响应流处理器。
 *
 * HTML 和多文件模式输出普通文本，Vue 工程模式输出统一 JSON 消息。处理完成后两者都
 * 转换为结构化响应事件，使控制器只需要处理一种稳定的数据类型。
 */
@Component
public class StreamHandlerExecutor {

    private final JsonMessageStreamHandler jsonMessageStreamHandler;

    public StreamHandlerExecutor(JsonMessageStreamHandler jsonMessageStreamHandler) {
        this.jsonMessageStreamHandler = jsonMessageStreamHandler;
    }

    /**
     * 选择流处理器并完成前端输出与历史记录整理。
     *
     * @param originFlux 门面返回的原始响应流
     * @param chatHistoryService 对话历史服务
     * @param appId 当前应用 id
     * @param loginUser 发起生成的登录用户
     * @param codeGenType 当前应用的代码生成类型
     * @return 可以直接交给 SSE 控制器的结构化事件流
     */
    public Flux<GenerationStreamEvent> doExecute(Flux<String> originFlux,
                                                 ChatHistoryService chatHistoryService,
                                                 long appId,
                                                 User loginUser,
                                                 CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型不能为空");
        }
        return switch (codeGenType) {
            case VUE_PROJECT -> jsonMessageStreamHandler.handle(
                    originFlux,
                    chatHistoryService,
                    appId,
                    loginUser
            );
            case HTML, MULTI_FILE -> new SimpleTextStreamHandler()
                    .handle(originFlux, chatHistoryService, appId, loginUser)
                    .map(GenerationStreamEvent::message);
        };
    }
}
