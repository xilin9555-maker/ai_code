package com.tmz.aicode.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.model.entity.ChatHistory;
import com.tmz.aicode.service.AppService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 对话历史保存规则的本地单元测试。
 *
 * save 方法被替换为内存模拟实现，不会连接数据库，也不会调用真实模型。
 */
class ChatHistoryServiceImplTest {

    /**
     * 保存 AI 代码时必须保留原有空格和换行，否则重新展示历史时网页代码会被破坏。
     */
    @Test
    void addChatMessageKeepsOriginalFormatting() {
        ChatHistoryServiceImpl service = spy(
                new ChatHistoryServiceImpl(mock(AppService.class))
        );
        doReturn(true).when(service).save(any(ChatHistory.class));
        String message = "  <main>\n    内容\n  </main>";

        boolean saved = service.addChatMessage(2001L, message, "ai", 1001L);

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(service).save(captor.capture());
        assertEquals(true, saved);
        assertEquals(message, captor.getValue().getMessage());
        assertEquals("ai", captor.getValue().getMessageType());
    }

    /**
     * 未知消息类型不能进入数据库，避免历史列表出现无法识别的发送方。
     */
    @Test
    void addChatMessageRejectsUnknownType() {
        ChatHistoryServiceImpl service = new ChatHistoryServiceImpl(mock(AppService.class));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.addChatMessage(2001L, "消息", "system", 1001L)
        );

        assertEquals("不支持的消息类型：system", exception.getMessage());
    }

    /**
     * 数据库返回的是从新到旧的历史记录，恢复到模型记忆时应变成从旧到新，并把数据库实体
     * 转换为 LangChain4j 能识别的 UserMessage 和 AiMessage。
     */
    @Test
    void loadChatHistoryRestoresMessagesInConversationOrder() {
        ChatHistoryServiceImpl service = spy(
                new ChatHistoryServiceImpl(mock(AppService.class))
        );
        ChatHistory recentAiMessage = ChatHistory.builder()
                .message("这是上一轮回答")
                .messageType("ai")
                .build();
        ChatHistory olderUserMessage = ChatHistory.builder()
                .message("这是上一轮问题")
                .messageType("user")
                .build();
        doReturn(List.of(recentAiMessage, olderUserMessage))
                .when(service).list(any(QueryWrapper.class));
        MessageWindowChatMemory chatMemory = mock(MessageWindowChatMemory.class);

        int loadedCount = service.loadChatHistoryToMemory(2001L, chatMemory, 20);

        assertEquals(2, loadedCount);
        verify(chatMemory).clear();
        ArgumentCaptor<ChatMessage> messageCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMemory, times(2)).add(messageCaptor.capture());
        List<ChatMessage> loadedMessages = messageCaptor.getAllValues();
        UserMessage loadedUserMessage =
                assertInstanceOf(UserMessage.class, loadedMessages.get(0));
        AiMessage loadedAiMessage =
                assertInstanceOf(AiMessage.class, loadedMessages.get(1));
        assertEquals("这是上一轮问题", loadedUserMessage.singleText());
        assertEquals("这是上一轮回答", loadedAiMessage.text());
    }

    /**
     * 没有可恢复的历史时不清空消息窗口，避免无意义地访问 Redis。
     */
    @Test
    void loadChatHistoryLeavesMemoryUntouchedWhenDatabaseIsEmpty() {
        ChatHistoryServiceImpl service = spy(
                new ChatHistoryServiceImpl(mock(AppService.class))
        );
        doReturn(List.of()).when(service).list(any(QueryWrapper.class));
        MessageWindowChatMemory chatMemory = mock(MessageWindowChatMemory.class);

        int loadedCount = service.loadChatHistoryToMemory(2001L, chatMemory, 20);

        assertEquals(0, loadedCount);
        verify(chatMemory, never()).clear();
    }
}
