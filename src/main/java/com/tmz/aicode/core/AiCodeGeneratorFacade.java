package com.tmz.aicode.core;

import com.tmz.aicode.ai.AiCodeGeneratorService;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.core.parser.CodeParserExecutor;
import com.tmz.aicode.core.saver.CodeFileSaverExecutor;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 统一编排网页代码的生成和保存流程。
 *
 * 调用方只需要提供需求描述、生成方式和应用 id，不必分别了解 AI 服务、结构化结果类型
 * 和文件保存器。这层门面把多个步骤收拢成一个稳定入口，并确保代码写入所属应用的目录。
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    private final AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 通过构造器接收 AI 服务，正式运行时由 Spring 注入，测试时也可以传入本地 Demo 服务。
     *
     * @param aiCodeGeneratorService 负责生成结构化网页代码的服务
     */
    public AiCodeGeneratorFacade(AiCodeGeneratorService aiCodeGeneratorService) {
        this.aiCodeGeneratorService = aiCodeGeneratorService;
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
        return switch (codeGenType) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
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
        };
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
     * 在请求模型前检查应用 id，避免代码生成完成后才发现无法确定保存目录。
     */
    private void validateAppId(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 必须大于 0");
        }
    }
}
