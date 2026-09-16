package com.tmz.aicode.langgraph4j;

import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 并发图片收集工作流手动集成测试。
 *
 * 两个场景都会执行规划、真实图片工具、代码生成、质量检查和项目构建完整流程。默认跳过，
 * 只有显式设置 runRealConcurrentWorkflowTest=true 时才执行，避免自动测试产生外部调用。
 */
@SpringBootTest
@EnabledIfSystemProperty(
        named = "runRealConcurrentWorkflowTest",
        matches = "true",
        disabledReason = "仅允许手动启用真实并发工作流测试"
)
class ConcurrentWorkflowAppTest {

    @Test
    void testConcurrentWorkflow() {
        WorkflowContext result = new ConcurrentWorkflowApp().executeWorkflow(
                "创建一个技术博客网站，需要展示编程知识和系统架构");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("构建结果目录: " + result.getBuildResultDir());
        System.out.println("收集的图片数量: "
                + (result.getImageList() != null ? result.getImageList().size() : 0));
    }

    @Test
    void testEcommerceWorkflow() {
        WorkflowContext result = new ConcurrentWorkflowApp().executeWorkflow(
                "创建一个电子商务网站，需要商品展示、购物车和支付功能");
        Assertions.assertNotNull(result);
        System.out.println("生成类型: " + result.getGenerationType());
        System.out.println("生成的代码目录: " + result.getGeneratedCodeDir());
        System.out.println("收集的图片数量: "
                + (result.getImageList() != null ? result.getImageList().size() : 0));
    }
}
