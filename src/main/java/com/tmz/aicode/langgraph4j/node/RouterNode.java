package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.ai.AiCodeGenTypeRoutingService;
import com.tmz.aicode.ai.AiCodeGenTypeRoutingServiceFactory;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 根据用户需求选择代码生成模式的路由节点。
 *
 * 节点复用现有路由服务进行判断；模型调用失败时回退到原生单文件模式，确保后续代码生成
 * 始终能够获得有效的生成类型。
 */
@Slf4j
public final class RouterNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "智能路由";

    private RouterNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建智能路由节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            CodeGenTypeEnum generationType = context.getGenerationType();
            if (generationType != null) {
                // 接入已有应用时沿用数据库中的生成类型，保证预览、部署和源码目录始终一致。
                log.info("沿用应用既定生成类型：{}（{}）",
                        generationType.getValue(), generationType.getText());
            } else {
                try {
                    // 独立运行工作流时仍由路由服务根据原始需求选择生成类型。
                    AiCodeGenTypeRoutingServiceFactory routingServiceFactory =
                            SpringContextUtil.getBean(AiCodeGenTypeRoutingServiceFactory.class);
                    AiCodeGenTypeRoutingService routingService =
                            routingServiceFactory.createAiCodeGenTypeRoutingService();
                    generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                    log.info("智能路由完成，选择类型：{}（{}）",
                            generationType.getValue(), generationType.getText());
                } catch (Exception exception) {
                    // 路由失败时使用最基础的 HTML 模式，避免工作流因无法分类而直接终止。
                    log.error("智能路由失败，使用默认 HTML 类型：{}", exception.getMessage());
                    generationType = CodeGenTypeEnum.HTML;
                }
            }

            context.setCurrentStep(STEP_NAME);
            context.setGenerationType(generationType);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("智能路由节点缺少工作流上下文");
        }
        return context;
    }
}
