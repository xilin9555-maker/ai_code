package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 项目构建工作节点。
 *
 * 只有 Vue 工程会通过条件边进入该节点。节点负责安装依赖并执行生产构建，构建成功后
 * 将 dist 目录写入共享上下文；流程分支判断统一由工作流的条件边负责。
 */
@Slf4j
public final class ProjectBuilderNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "项目构建";

    private ProjectBuilderNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建项目构建节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            String generatedCodeDir = context.getGeneratedCodeDir();
            String buildResultDir;
            try {
                // 条件边已经保证进入该节点的是 Vue 工程，此处只处理实际构建任务。
                VueProjectBuilder vueProjectBuilder =
                        SpringContextUtil.getBean(VueProjectBuilder.class);
                boolean buildSuccess = vueProjectBuilder.buildProject(generatedCodeDir);
                if (!buildSuccess) {
                    throw new BusinessException(
                            ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败");
                }
                buildResultDir = generatedCodeDir + File.separator + "dist";
                log.info("Vue 项目构建成功，dist 目录：{}", buildResultDir);
            } catch (Exception exception) {
                // 构建失败时保留源码目录，便于检查文件并在修复后重新构建。
                log.error("Vue 项目构建异常：{}", exception.getMessage(), exception);
                buildResultDir = generatedCodeDir;
            }

            context.setCurrentStep(STEP_NAME);
            context.setBuildResultDir(buildResultDir);
            log.info("项目构建节点完成，最终目录：{}", buildResultDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("项目构建节点缺少工作流上下文");
        }
        return context;
    }
}
