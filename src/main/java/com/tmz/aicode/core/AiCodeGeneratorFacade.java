package com.tmz.aicode.core;

import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.AiCodeGeneratorService;
import com.tmz.aicode.ai.AiCodeGeneratorServiceFactory;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.ToolExecutedMessage;
import com.tmz.aicode.ai.model.message.ToolRequestMessage;
import com.tmz.aicode.core.parser.CodeParserExecutor;
import com.tmz.aicode.core.saver.CodeFileSaverExecutor;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Locale;

/**
 * 统一编排网页代码的生成和保存流程。
 *
 * 调用方只需要提供需求描述、生成方式和应用 id，不必分别了解 AI 服务、结构化结果类型
 * 和文件保存器。这层门面把多个步骤收拢成一个稳定入口，并确保代码写入所属应用的目录。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    private final AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 通过构造器接收 AI 服务工厂。门面会把 appId 交给工厂，让每个应用使用独立的会话记忆。
     *
     * @param aiCodeGeneratorServiceFactory 根据应用 id 创建代码生成服务的工厂
     */
    public AiCodeGeneratorFacade(AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory) {
        this.aiCodeGeneratorServiceFactory = aiCodeGeneratorServiceFactory;
    }
    /**
     * 根据生成方式完成“调用 AI”和“写入文件”两个步骤。
     *
     * @param userMessage 用户对网站功能和设计的描述
     * @param codeGenType 需要生成的代码组织方式
     * @param appId 代码所属的应用 id，用于确定保存目录
     * @return 当前应用对应的代码目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType, Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型不能为空");
        }
        validateAppId(appId);
        AiCodeGeneratorService aiCodeGeneratorService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);
        return switch (codeGenType) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "Vue 工程只支持流式生成"
            );
        };
    }

    /**
     * 根据生成方式返回代码片段流，并在流正常结束后保存完整网站。
     *
     * 返回 Flux 后不会立刻请求模型，只有调用方订阅数据流时才开始生成。控制器后续可以把
     * Flux 直接作为 SSE 响应返回，让前端实时接收片段；门面同时在后台收集相同片段，
     * 完成后解析并保存，不需要再向模型发起第二次请求。
     *
     * @param userMessage 用户对网站功能和设计的描述
     * @param codeGenType 需要生成的代码组织方式
     * @param appId 代码所属的应用 id，用于确定保存目录
     * @return 按模型生成顺序发出的代码文本片段
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage,
                                                   CodeGenTypeEnum codeGenType,
                                                   Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型不能为空");
        }
        validateAppId(appId);
        AiCodeGeneratorService aiCodeGeneratorService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenType);
        return switch (codeGenType) {
            case HTML -> processCodeStream(
                    aiCodeGeneratorService.generateHtmlCodeStream(userMessage),
                    CodeGenTypeEnum.HTML,
                    appId
            );
            case MULTI_FILE -> processCodeStream(
                    aiCodeGeneratorService.generateMultiFileCodeStream(userMessage),
                    CodeGenTypeEnum.MULTI_FILE,
                    appId
            );
            case VUE_PROJECT -> Flux.defer(() -> processTokenStream(
                            aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage)
                    ))
                    // Vue 文件已经由 writeFile 工具逐个保存，这里只负责传递生成过程。
                    .doOnComplete(() -> log.info("Vue 工程文件生成完成，应用 id：{}", appId))
                    .doOnError(error -> log.error("Vue 工程生成失败，应用 id：{}", appId, error));
        };
    }

    /**
     * 把 LangChain4j 的 TokenStream 适配为项目统一使用的 Flux。
     *
     * TokenStream 会通过不同回调报告普通文本、工具请求和工具执行结果。这里将每种事件
     * 包装成带 type 字段的 JSON，使下游能够在同一条响应流中可靠地区分消息，而不需要
     * 根据文本内容猜测当前片段的含义。
     *
     * @param tokenStream Vue 工程生成服务返回的事件流
     * @return 按事件发生顺序输出统一 JSON 消息的 Reactor 流
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            try {
                tokenStream
                        .onPartialResponse(partialResponse -> {
                            if (partialResponse != null && !sink.isCancelled()) {
                                sink.next(JSONUtil.toJsonStr(new AiResponseMessage(partialResponse)));
                            }
                        })
                        .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                            if (toolExecutionRequest != null && !sink.isCancelled()) {
                                sink.next(JSONUtil.toJsonStr(
                                        new ToolRequestMessage(toolExecutionRequest)
                                ));
                            }
                        })
                        .onToolExecuted(toolExecution -> {
                            if (toolExecution != null && !sink.isCancelled()) {
                                sink.next(JSONUtil.toJsonStr(
                                        new ToolExecutedMessage(toolExecution)
                                ));
                            }
                        })
                        .onCompleteResponse(response -> {
                            if (!sink.isCancelled()) {
                                sink.complete();
                            }
                        })
                        .onError(error -> {
                            log.error("TokenStream 处理失败", error);
                            if (!sink.isCancelled()) {
                                sink.error(error);
                            }
                        })
                        .start();
            } catch (Throwable error) {
                // start 也可能同步抛出配置异常，需要把它交给 Flux 的错误链统一处理。
                if (!sink.isCancelled()) {
                    sink.error(error);
                }
            }
        });
    }

    /**
     * 处理所有生成模式共用的流式收集、解析和保存流程。
     *
     * Flux.defer 确保每次订阅都有独立的 StringBuilder，避免并发请求或重复订阅共享缓冲区。
     * doOnNext 只旁路收集片段，不会改变继续传给前端的内容；流正常结束后，两个执行器根据
     * codeGenType 选择解析策略和保存模板，appId 决定最终目录。任何错误都会继续沿 Flux
     * 传递给调用方，避免前端收到成功结束信号但文件实际上没有保存。
     *
     * @param codeStream AI 服务返回的原始代码片段流
     * @param codeGenType 当前代码生成类型
     * @param appId 代码所属的应用 id
     * @return 内容和顺序保持不变的代码片段流
     */
    private Flux<String> processCodeStream(Flux<String> codeStream,
                                            CodeGenTypeEnum codeGenType,
                                            Long appId) {
        return Flux.defer(() -> {
            StringBuilder codeBuilder = new StringBuilder();
            return codeStream
                    .doOnNext(codeBuilder::append)
                    .doOnComplete(() -> {
                        String completeCode = codeBuilder.toString();
                        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                        if (!containsCompleteHtmlDocument(parsedResult)) {
                            /*
                             * 用户可能只是询问已有页面或进行普通对话。这种回复需要正常传给前端，
                             * 但不能把自然语言覆盖到 index.html，也不应因为缺少代码而中断 SSE。
                             */
                            log.info("本轮回复未包含完整网页代码，保留现有文件，应用 id：{}", appId);
                            return;
                        }
                        File savedDirectory = CodeFileSaverExecutor.executeSaver(
                                parsedResult, codeGenType, appId
                        );
                        log.info("网页代码保存成功，应用 id：{}，类型：{}，路径：{}",
                                appId, codeGenType.getValue(), savedDirectory.getAbsolutePath());
                    })
                    .doOnError(error -> log.error("网页代码流式处理失败，应用 id：{}，类型：{}",
                            appId, codeGenType.getValue(), error));
        });
    }

    /**
     * 判断解析结果是否包含可以作为网页入口保存的完整 HTML 文档。
     *
     * 单文件和多文件模式最终都必须提供 index.html。DOCTYPE、html 和 body 三个标记可以
     * 排除普通闲聊文字以及不完整的代码片段，避免它们覆盖上一次已经生成成功的网站。
     *
     * @param parsedResult 按当前生成类型解析后的结果
     * @return 包含完整 HTML 文档时返回 true
     */
    private boolean containsCompleteHtmlDocument(Object parsedResult) {
        String htmlCode = switch (parsedResult) {
            case HtmlCodeResult htmlResult -> htmlResult.getHtmlCode();
            case MultiFileCodeResult multiFileResult -> multiFileResult.getHtmlCode();
            default -> null;
        };
        if (htmlCode == null || htmlCode.isBlank()) {
            return false;
        }
        String normalizedHtml = htmlCode.toLowerCase(Locale.ROOT);
        return normalizedHtml.contains("<!doctype html")
                && normalizedHtml.contains("<html")
                && normalizedHtml.contains("<body");
    }

    /**
     * 在请求模型前检查应用 id，避免代码生成完成后才发现无法确定保存目录。
     */
    private void validateAppId(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 必须大于 0");
        }
    }
}
