package com.tmz.aicode.langgraph4j;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证自定义业务状态可以在本地工作流中完整传递。
 *
 * 测试节点只修改 currentStep，不会访问模型、图片服务、数据库或文件系统。
 */
class SimpleStatefulWorkflowAppTest {

    /** 全部节点执行后应保留已有字段，并把当前步骤更新为最后一个节点。 */
    @Test
    void preservesContextAcrossAllWorkflowNodes() throws GraphStateException {
        ImageResource logo = ImageResource.builder()
                .category(ImageCategoryEnum.LOGO)
                .description("个人博客的圆形头像标志")
                .url("https://example.com/logo.png")
                .build();
        WorkflowContext initialContext = WorkflowContext.builder()
                .currentStep("初始化")
                .originalPrompt("创建一个个人博客网站")
                .imageListStr("LOGO：个人博客的圆形头像标志")
                .imageList(List.of(logo))
                .enhancedPrompt("创建一个包含头像与文章列表的个人博客网站")
                .generationType(CodeGenTypeEnum.VUE_PROJECT)
                .generatedCodeDir("build/source")
                .buildResultDir("build/dist")
                .build();
        CompiledGraph<MessagesState<String>> workflow =
                SimpleStatefulWorkflowApp.createWorkflow();

        MessagesState<String> finalState = workflow
                .invoke(WorkflowContext.saveContext(initialContext))
                .orElseThrow();
        WorkflowContext finalContext = WorkflowContext.getContext(finalState);

        assertEquals(SimpleStatefulWorkflowApp.PROJECT_BUILDER,
                finalContext.getCurrentStep());
        assertEquals("创建一个个人博客网站", finalContext.getOriginalPrompt());
        assertEquals(List.of(logo), finalContext.getImageList());
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, finalContext.getGenerationType());
        assertEquals("build/source", finalContext.getGeneratedCodeDir());
        assertEquals("build/dist", finalContext.getBuildResultDir());
    }

    /** 固定键读写应返回同一个上下文对象，并校验空上下文。 */
    @Test
    void readsAndWritesContextWithStableStateKey() {
        WorkflowContext context = WorkflowContext.builder()
                .originalPrompt("生成产品落地页")
                .build();
        MessagesState<String> state = new MessagesState<>(
                WorkflowContext.saveContext(context));

        assertSame(context, WorkflowContext.getContext(state));
        assertThrows(NullPointerException.class,
                () -> WorkflowContext.saveContext(null));
    }

    /** 图片分类应能按稳定值查询，未知值保持为空。 */
    @Test
    void resolvesImageCategoryByStableValue() {
        assertEquals(ImageCategoryEnum.CONTENT,
                ImageCategoryEnum.getEnumByValue("CONTENT"));
        assertEquals(ImageCategoryEnum.ARCHITECTURE,
                ImageCategoryEnum.getEnumByValue("ARCHITECTURE"));
        assertNull(ImageCategoryEnum.getEnumByValue("UNKNOWN"));
        assertNull(ImageCategoryEnum.getEnumByValue(null));
    }
}
