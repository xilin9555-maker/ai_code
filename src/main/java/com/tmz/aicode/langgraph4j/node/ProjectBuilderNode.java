package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.dto.build.BuildProgress;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Objects;
import java.util.function.Consumer;

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
        return create(ignored -> {
        });
    }

    /**
     * 创建能够向调用方报告阶段进度的项目构建节点。
     *
     * @param progressConsumer 构建器产生的进度接收器
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create(
            Consumer<BuildProgress> progressConsumer) {
        Objects.requireNonNull(progressConsumer, "构建进度接收器不能为空");
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            String generatedCodeDir = context.getGeneratedCodeDir();
            // 条件边已经保证进入该节点的是 Vue 工程，此处同步等待实际构建完成。
            VueProjectBuilder vueProjectBuilder =
                    SpringContextUtil.getBean(VueProjectBuilder.class);
            boolean buildSuccess = vueProjectBuilder.buildProject(
                    generatedCodeDir, progressConsumer);
            if (!buildSuccess) {
                /*
                 * 构建失败必须中断工作流，不能继续发送“完成”信号。否则前端会立即刷新
                 * 预览，却只能看到旧的 dist 内容，无法判断本次源码其实没有成功发布。
                 */
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查生成代码和依赖");
            }
            String buildResultDir = generatedCodeDir + File.separator + "dist";
            log.info("Vue 项目构建成功，dist 目录：{}", buildResultDir);

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
