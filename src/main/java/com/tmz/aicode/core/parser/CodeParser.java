package com.tmz.aicode.core.parser;

/**
 * 代码解析策略的统一接口。
 *
 * 不同生成模式返回不同的结果类型，泛型 T 让每个策略保留自己的准确返回类型，
 * 同时向执行器提供一致的 parseCode 调用方式。
 *
 * @param <T> 当前生成模式对应的结构化代码结果类型
 */
public interface CodeParser<T> {

    /**
     * 将模型返回的完整文本转换成对应的结构化代码对象。
     *
     * @param codeContent 已完成拼接的模型响应
     * @return 当前解析策略产生的结构化结果
     */
    T parseCode(String codeContent);
}
