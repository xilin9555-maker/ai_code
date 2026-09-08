package com.tmz.aicode.core;

import com.tmz.aicode.ai.AiCodeGeneratorService;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
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
 * 调用方只需要提供需求描述和生成方式，不必分别了解 AI 服务、结构化结果类型和文件保存器。
 * 这层门面把多个步骤收拢成一个稳定入口，也便于后续继续加入校验、日志和发布流程。
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
     * @return 包含本次全部代码文件的独立目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型不能为空");
        }
        return switch (codeGenType) {
            case HTML -> generateAndSaveHtmlCode(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCode(userMessage);
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
     * @return 按模型生成顺序发出的代码文本片段
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型不能为空");
        }
        return switch (codeGenType) {
            case HTML -> generateAndSaveHtmlCodeStream(userMessage);
            case MULTI_FILE -> generateAndSaveMultiFileCodeStream(userMessage);
        };
    }

    /**
     * 生成单文件网页并保存为 index.html。
     *
     * @param userMessage 用户的网站需求
     * @return 单文件网页所在目录
     */
    private File generateAndSaveHtmlCode(String userMessage) {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(result);
    }

    /**
     * 生成多文件网页并分别保存 HTML、CSS 和 JavaScript。
     *
     * @param userMessage 用户的网站需求
     * @return 多文件网页所在目录
     */
    private File generateAndSaveMultiFileCode(String userMessage) {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(result);
    }

    /**
     * 流式生成单文件网页，在所有片段到达后解析 HTML 代码并保存。
     *
     * Flux.defer 为每次订阅创建独立的 StringBuilder，避免同一个 Flux 被多次订阅时
     * 共用缓冲区导致代码混合。只有流正常完成才保存文件，取消订阅或生成失败时不会写入
     * 一个残缺页面。
     *
     * @param userMessage 用户的网站需求
     * @return 单文件代码片段流
     */
    private Flux<String> generateAndSaveHtmlCodeStream(String userMessage) {
        return Flux.defer(() -> {
            StringBuilder codeBuilder = new StringBuilder();
            return aiCodeGeneratorService.generateHtmlCodeStream(userMessage)
                    .doOnNext(codeBuilder::append)
                    .doOnComplete(() -> {
                        HtmlCodeResult result = CodeParser.parseHtmlCode(codeBuilder.toString());
                        File savedDirectory = CodeFileSaver.saveHtmlCodeResult(result);
                        log.info("单文件网页保存成功，路径：{}", savedDirectory.getAbsolutePath());
                    })
                    .doOnError(error -> log.error("单文件网页流式生成失败", error));
        });
    }

    /**
     * 流式生成多文件网页，在所有片段到达后解析并分别保存三种代码。
     *
     * 下游仍然能够实时收到原始片段，收集动作只是旁路记录，不会改变片段内容和顺序。
     * 如果解析或保存失败，异常会沿 Flux 传递给调用方，便于 SSE 接口向前端报告失败。
     *
     * @param userMessage 用户的网站需求
     * @return 多文件代码片段流
     */
    private Flux<String> generateAndSaveMultiFileCodeStream(String userMessage) {
        return Flux.defer(() -> {
            StringBuilder codeBuilder = new StringBuilder();
            return aiCodeGeneratorService.generateMultiFileCodeStream(userMessage)
                    .doOnNext(codeBuilder::append)
                    .doOnComplete(() -> {
                        MultiFileCodeResult result = CodeParser.parseMultiFileCode(codeBuilder.toString());
                        File savedDirectory = CodeFileSaver.saveMultiFileCodeResult(result);
                        log.info("多文件网页保存成功，路径：{}", savedDirectory.getAbsolutePath());
                    })
                    .doOnError(error -> log.error("多文件网页流式生成失败", error));
        });
    }
}
