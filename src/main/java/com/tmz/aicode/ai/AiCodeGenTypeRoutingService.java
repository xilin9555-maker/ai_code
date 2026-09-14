package com.tmz.aicode.ai;

import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;

/**
 * 根据用户的自然语言需求选择代码生成类型。
 *
 * LangChain4j 会在运行时为该接口创建代理。方法的返回值直接使用枚举，框架会把模型
 * 输出转换为受程序约束的类型，业务层不需要自行解析不稳定的自然语言回答。
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 判断当前需求适合单文件、多文件还是 Vue 工程。
     *
     * @param userPrompt 用户创建应用时填写的完整需求
     * @return 与需求复杂度匹配的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/codegen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
