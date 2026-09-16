package com.tmz.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.tmz.aicode.ai.AiCodeGenTypeRoutingService;
import com.tmz.aicode.ai.AiCodeGenTypeRoutingServiceFactory;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.AiCodeGeneratorFacade;
import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.core.handler.StreamHandlerExecutor;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.langgraph4j.WorkflowApp;
import com.tmz.aicode.mapper.AppMapper;
import com.tmz.aicode.model.dto.app.AppAddRequest;
import com.tmz.aicode.model.dto.app.AppQueryRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.model.vo.AppVO;
import com.tmz.aicode.model.vo.UserVO;
import com.tmz.aicode.mq.ScreenshotTaskProducer;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ChatHistoryService;
import com.tmz.aicode.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用业务服务实现。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    /**
     * 部署标识使用大小写字母和数字，既适合放入 URL，也能在较短长度下提供足够组合。
     */
    private static final String DEPLOY_KEY_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    /**
     * 极少数情况下随机标识可能重复，最多重新生成十次后再明确报告失败。
     */
    private static final int DEPLOY_KEY_MAX_RETRIES = 10;

    /**
     * 允许客户端使用的排序字段。
     *
     * 排序字段最终会参与 SQL 拼接，因此不能直接信任客户端传入的任意字符串。
     */
    private static final Set<String> APP_SORT_FIELDS = Set.of(
            "id", "appName", "codeGenType", "deployKey", "deployedTime", "priority",
            "userId", "editTime", "createTime", "updateTime"
    );

    private final UserService userService;

    private final AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    private final ChatHistoryService chatHistoryService;

    private final StreamHandlerExecutor streamHandlerExecutor;

    private final VueProjectBuilder vueProjectBuilder;

    private final ScreenshotTaskProducer screenshotTaskProducer;

    public AppServiceImpl(UserService userService,
                          AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory,
                          AiCodeGeneratorFacade aiCodeGeneratorFacade,
                          ChatHistoryService chatHistoryService,
                          StreamHandlerExecutor streamHandlerExecutor,
                          VueProjectBuilder vueProjectBuilder,
                          ScreenshotTaskProducer screenshotTaskProducer) {
        this.userService = userService;
        this.aiCodeGenTypeRoutingServiceFactory = aiCodeGenTypeRoutingServiceFactory;
        this.aiCodeGeneratorFacade = aiCodeGeneratorFacade;
        this.chatHistoryService = chatHistoryService;
        this.streamHandlerExecutor = streamHandlerExecutor;
        this.vueProjectBuilder = vueProjectBuilder;
        this.screenshotTaskProducer = screenshotTaskProducer;
    }

    /**
     * 创建应用，并在写入数据库前让 AI 根据需求复杂度选择生成方案。
     *
     * 用户只负责描述想要的网站。服务端统一绑定创建者、生成临时名称、设置默认优先级，
     * 再把完整需求交给路由服务。选择结果会随应用一起保存，后续生成、预览、部署和下载
     * 都以数据库中的类型为准，不需要在每一轮对话中重复判断。
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null,
                ErrorCode.PARAMS_ERROR, "创建应用参数不能为空");
        String initPrompt = StrUtil.trim(appAddRequest.getInitPrompt());
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt),
                ErrorCode.PARAMS_ERROR, "初始化需求不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        // 每次创建应用都构造新的路由服务，确保并发请求使用彼此独立的模型实例。
        AiCodeGenTypeRoutingService routingService =
                aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        ThrowUtils.throwIf(selectedCodeGenType == null,
                ErrorCode.SYSTEM_ERROR, "未能确定代码生成类型");

        App app = new App();
        app.setInitPrompt(initPrompt);
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        app.setCodeGenType(selectedCodeGenType.getValue());
        app.setPriority(AppConstant.DEFAULT_APP_PRIORITY);

        boolean saved = this.save(app);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "应用创建失败");
        log.info("应用创建成功，id：{}，代码生成类型：{}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    /**
     * 将应用当前生成的代码复制到部署目录，并记录稳定的外部访问标识。
     *
     * 第一次部署会生成 deployKey，之后重复部署继续使用同一个值，因此应用更新后重新发布
     * 不会改变访问地址。文件复制成功后才更新数据库，避免数据库显示已部署但目录中没有文件。
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在或已经被删除");
        ThrowUtils.throwIf(!java.util.Objects.equals(app.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能部署自己创建的应用");

        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null,
                ErrorCode.SYSTEM_ERROR, "应用的代码生成类型不受支持");

        String sourceDirName = codeGenType.getValue() + "_" + appId;
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        ThrowUtils.throwIf(!sourceDir.isDirectory(),
                ErrorCode.OPERATION_ERROR, "应用代码不存在，请先生成代码");

        if (codeGenType == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 源码不能直接交给浏览器运行，部署前必须同步生成静态构建产物。
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDir.getAbsolutePath());
            ThrowUtils.throwIf(!buildSuccess,
                    ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");

            File distDir = new File(sourceDir, "dist");
            ThrowUtils.throwIf(!distDir.isDirectory(),
                    ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 只发布 dist，避免把源码、node_modules 和 package.json 暴露到静态网站目录。
            sourceDir = distDir;
            log.info("Vue 项目构建成功，准备部署静态文件目录：{}", distDir.getAbsolutePath());
        }

        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = generateUniqueDeployKey();
        }

        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        try {
            // 覆盖同名文件使重新部署立即更新内容，同时保持 deployKey 和访问地址不变。
            FileUtil.copyContent(sourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署文件复制失败");
        }

        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updated = this.updateById(updateApp);
        ThrowUtils.throwIf(!updated,
                ErrorCode.OPERATION_ERROR, "应用部署信息更新失败");

        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 截图任务进入 RabbitMQ 后由消费者处理，部署请求不需要等待浏览器和 COS 上传。
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 把应用封面生成任务提交到 RabbitMQ。
     *
     * 部署线程只发送包含应用 id 和访问地址的小消息。真正的浏览器截图、COS 上传和封面
     * 更新由单并发消费者按队列顺序完成，因此共享 WebDriver 不会同时切换多个页面。
     * RabbitMQ 暂时不可用时记录完整异常，但不撤销已经成功复制的部署文件。
     *
     * @param appId  已完成部署的应用 id
     * @param appUrl 可以由截图浏览器访问的应用地址
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        try {
            screenshotTaskProducer.sendScreenshotTask(appId, appUrl);
        } catch (Exception e) {
            // 应用静态文件已经部署成功，截图消息发送失败只影响封面，不能把部署结果回滚掉。
            log.error("应用截图任务发送失败，应用 id：{}，应用地址：{}", appId, appUrl, e);
        }
    }

    /**
     * 生成数据库中尚未使用的六位部署标识。
     *
     * 数据库的唯一索引是最后一道约束，这里的主动查询可以让常见的随机碰撞在写库前解决。
     */
    private String generateUniqueDeployKey() {
        for (int attempt = 0; attempt < DEPLOY_KEY_MAX_RETRIES; attempt++) {
            String candidate = RandomUtil.randomString(DEPLOY_KEY_CHARACTERS, 6);
            long existingCount = this.count(
                    QueryWrapper.create().eq("deployKey", candidate)
            );
            if (existingCount == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成部署标识失败，请稍后重试");
    }

    /**
     * 校验应用与当前用户后，把流式生成任务交给代码生成门面。
     *
     * 应用服务掌握数据库记录和所有权规则，适合决定“谁可以为哪个应用生成”；门面只处理
     * “怎样调用模型、解析结果并保存文件”。这种分工避免底层生成组件依赖用户 Session。
     */
    @Override
    public Flux<String> chatToGenCode(Long appId,
                                      String message,
                                      User loginUser,
                                      boolean agent) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 必须大于 0");
        }
        String normalizedMessage = StrUtil.trim(message);
        if (StrUtil.isBlank(normalizedMessage)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        }
        if (loginUser == null || loginUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 应用数据同时提供所有者和生成类型，必须以数据库中的最新记录为准。
        App app = this.getById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在或已经被删除");
        }
        if (!java.util.Objects.equals(app.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "只能为自己创建的应用生成代码");
        }

        // 数据库存储稳定枚举值，转换失败说明记录异常，不能继续请求模型和写文件。
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用的代码生成类型不受支持");
        }
        // 模型调用前先保存用户输入。即使随后连接模型失败，本轮对话的起点仍然可以追溯。
        boolean userMessageSaved = chatHistoryService.addChatMessage(
                appId,
                normalizedMessage,
                ChatHistoryMessageTypeEnum.USER.getValue(),
                loginUser.getId()
        );
        ThrowUtils.throwIf(!userMessageSaved,
                ErrorCode.OPERATION_ERROR, "用户消息保存失败");

        /*
         * defer 把模型调用推迟到订阅阶段。这样模型方法同步抛出的连接异常也会进入响应流，
         * 既能被下面的错误处理记录，又能继续由 SSE 链路通知前端。
         */
        return Flux.defer(() -> {
            Flux<String> originFlux;
            if (agent) {
                // 工作流仍把真实 appId 和既定生成类型交给同一代码生成门面，因而与普通模式
                // 共用项目目录及 appId + codeGenType 对应的 LangChain4j 会话记忆。
                originFlux = WorkflowApp.executeWorkflowWithFlux(
                        normalizedMessage, appId, codeGenType);
            } else {
                originFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                        normalizedMessage,
                        codeGenType,
                        appId
                );
            }
            /*
             * 普通模式直接收集文本，Vue 模式先解析门面产生的 JSON 事件。两类处理器最终
             * 都返回前端可展示的文本，并负责把整理后的完整 AI 回复保存到对话历史。
             */
            return streamHandlerExecutor.doExecute(
                    originFlux,
                    chatHistoryService,
                    appId,
                    loginUser,
                    codeGenType,
                    // AI 工作流包含同步构建节点，不能在流处理结束后再次构建同一工程。
                    !agent
            );
        });
    }

    /**
     * 删除应用时一并清理它的对话历史。
     *
     * 两次删除放在同一个事务内：任何一步抛出异常都会整体回滚，避免留下没有所属应用的
     * 历史消息，也避免应用仍存在但历史已经被提前清空。
     *
     * @param id 需要删除的应用 id
     * @return 应用删除成功时返回 true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId;
        try {
            appId = Long.parseLong(id.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 格式错误");
        }
        ThrowUtils.throwIf(appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");

        // 没有历史记录时 deleteByAppId 会返回 false，这不影响继续删除应用本身。
        chatHistoryService.deleteByAppId(appId);
        boolean removed = super.removeById(id);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "应用删除失败");
        return true;
    }

    /**
     * 按照非空参数构造查询条件。
     *
     * 字符串展示内容使用模糊匹配，枚举、部署标识、优先级和关联 id 使用精确匹配。
     * 排序字段会先经过白名单检查，防止非法字段影响生成的 SQL。
     */
    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();
        // 接口文档工具常会给数字字段填入 0，只有正数才代表有效 id。
        if (id != null && id > 0) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(appName)) {
            queryWrapper.like("appName", appName.trim());
        }
        if (StrUtil.isNotBlank(cover)) {
            queryWrapper.like("cover", cover.trim());
        }
        if (StrUtil.isNotBlank(initPrompt)) {
            queryWrapper.like("initPrompt", initPrompt.trim());
        }
        if (StrUtil.isNotBlank(codeGenType)) {
            if (CodeGenTypeEnum.getEnumByValue(codeGenType) == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不合法");
            }
            queryWrapper.eq("codeGenType", codeGenType);
        }
        if (StrUtil.isNotBlank(deployKey)) {
            queryWrapper.eq("deployKey", deployKey.trim());
        }
        if (priority != null) {
            queryWrapper.eq("priority", priority);
        }
        if (userId != null && userId > 0) {
            queryWrapper.eq("userId", userId);
        }
        if (StrUtil.isNotBlank(sortField)) {
            if (!APP_SORT_FIELDS.contains(sortField)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序字段不合法");
            }
            if (!"ascend".equals(sortOrder) && !"descend".equals(sortOrder)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序方式不合法");
            }
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }

    /**
     * 转换一个应用，并补充创建者的公开资料。
     *
     * 单条详情只需要查询一个用户，这种写法直观且不会造成额外的列表查询开销。
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = toBasicAppVO(app);
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }

    /**
     * 批量组装应用和创建者信息。
     *
     * 先收集全部 userId，再一次查询对应用户并构造映射。无论当前页有多少应用，用户查询
     * 都只执行一次，避免逐条查询造成 N+1 性能问题。
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(
                                User::getId,
                                userService::getUserVO,
                                (first, ignored) -> first
                        ));

        return appList.stream().map(app -> {
            AppVO appVO = toBasicAppVO(app);
            appVO.setUser(userVOMap.get(app.getUserId()));
            return appVO;
        }).collect(Collectors.toList());
    }

    /**
     * 复制应用自身的可展示字段，不在这里访问数据库。
     */
    private AppVO toBasicAppVO(App app) {
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        return appVO;
    }
}
