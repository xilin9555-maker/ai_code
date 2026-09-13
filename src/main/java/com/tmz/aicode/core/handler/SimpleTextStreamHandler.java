package com.tmz.aicode.core.handler;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * 处理 HTML 和多文件模式产生的普通文本流。
 *
 * 这两种模式的每个片段本身就是需要展示的文本，因此可以原样返回给前端；处理器只在
 * 旁路收集完整回复，并在流正常结束后把一条完整的 AI 消息写入对话历史。
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 收集普通文本流并保存最终回复。
     *
     * {@link Flux#defer} 会为每次订阅创建独立缓冲区，避免重复订阅或并发请求共用同一个
     * StringBuilder。发生异常时不保存残缺回复，而是记录一条便于用户理解的失败消息。
     *
     * @param originFlux 门面返回的原始文本流
     * @param chatHistoryService 对话历史服务
     * @param appId 当前应用 id
     * @param loginUser 发起生成的登录用户
     * @return 内容和顺序保持不变的文本流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               User loginUser) {
        return Flux.defer(() -> {
            StringBuilder aiResponseBuilder = new StringBuilder();
            return originFlux
                    .doOnNext(aiResponseBuilder::append)
                    .doOnComplete(() -> saveCompletedResponse(
                            chatHistoryService,
                            appId,
                            loginUser.getId(),
                            aiResponseBuilder.toString()
                    ))
                    .doOnError(error -> saveFailureResponse(
                            chatHistoryService,
                            appId,
                            loginUser.getId(),
                            error
                    ));
        });
    }

    /**
     * 流正常结束后保存完整回复；空回复不产生无意义的历史记录。
     */
    private void saveCompletedResponse(ChatHistoryService chatHistoryService,
                                       long appId,
                                       Long userId,
                                       String response) {
        if (StrUtil.isBlank(response)) {
            return;
        }
        boolean saved = chatHistoryService.addChatMessage(
                appId,
                response,
                ChatHistoryMessageTypeEnum.AI.getValue(),
                userId
        );
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "AI 回复保存失败");
    }

    /**
     * 尽力记录生成失败原因，同时避免历史记录写入异常覆盖原始模型异常。
     */
    private void saveFailureResponse(ChatHistoryService chatHistoryService,
                                     long appId,
                                     Long userId,
                                     Throwable error) {
        String detail = StrUtil.blankToDefault(error.getMessage(), "未知错误");
        if (detail.length() > 500) {
            detail = detail.substring(0, 500);
        }
        try {
            chatHistoryService.addChatMessage(
                    appId,
                    "AI 回复失败：" + detail,
                    ChatHistoryMessageTypeEnum.AI.getValue(),
                    userId
            );
        } catch (Exception saveException) {
            log.error("保存 AI 失败消息时出现异常，appId={}", appId, saveException);
        }
    }
}
