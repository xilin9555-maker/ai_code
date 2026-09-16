package com.tmz.aicode.langgraph4j;

import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import org.bsc.langgraph4j.GraphStateException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 网站生成工作流手动集成测试。
 *
 * 测试会执行图片收集、提示词增强、智能路由、代码生成和项目构建完整流程。
 * 只有显式设置 runRealWorkflowTest=true 时才会执行，避免自动测试调用真实模型。
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "runRealWorkflowTest",
        matches = "true",
        disabledReason = "仅允许手动启用真实工作流测试"
)
class WorkflowAppTest {

    @Test
    void testTechBlogWorkflow() {
        WorkflowContext result = executeWorkflow(
                "创建一个技术博客网站，需要展示编程知识和系统架构");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
    }

    @Test
    void testCorporateWorkflow() {
        WorkflowContext result = executeWorkflow("创建企业官网，展示公司形象和业务介绍");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
    }

    @Test
    void testVueProjectWorkflow() {
        WorkflowContext result = executeWorkflow(
                "创建一个 Vue 前端项目，包含用户管理和数据展示功能");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
    }

    @Test
    void testSimpleHtmlWorkflow() {
        WorkflowContext result = executeWorkflow("创建一个简单的个人主页");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
    }

    /**
     * 使用当前 WorkflowApp 提供的静态方法执行完整工作流并读取最终上下文。
     */
    private WorkflowContext executeWorkflow(String originalPrompt) {
        try {
            return WorkflowApp.executeWorkflow(originalPrompt);
        } catch (GraphStateException exception) {
            throw new IllegalStateException("工作流创建失败", exception);
        }
    }
}
