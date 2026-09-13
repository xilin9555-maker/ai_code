package com.tmz.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.AiCodeGeneratorFacade;
import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.core.handler.StreamHandlerExecutor;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.mapper.AppMapper;
import com.tmz.aicode.model.dto.app.AppQueryRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.model.vo.AppVO;
import com.tmz.aicode.model.vo.UserVO;
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

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    private final ChatHistoryService chatHistoryService;

    private final StreamHandlerExecutor streamHandlerExecutor;

    private final VueProjectBuilder vueProjectBuilder;

    public AppServiceImpl(UserService userService,
                          AiCodeGeneratorFacade aiCodeGeneratorFacade,
                          ChatHistoryService chatHistoryService,
                          StreamHandlerExecutor streamHandlerExecutor,
                          VueProjectBuilder vueProjectBuilder) {
        this.userService = userService;
        this.aiCodeGeneratorFacade = aiCodeGeneratorFacade;
        this.chatHistoryService = chatHistoryService;
        this.streamHandlerExecutor = streamHandlerExecutor;
        this.vueProjectBuilder = vueProjectBuilder;
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

        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
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
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
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
            Flux<String> originFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                    normalizedMessage,
                    codeGenType,
                    appId
            );
            /*
             * 普通模式直接收集文本，Vue 模式先解析门面产生的 JSON 事件。两类处理器最终
             * 都返回前端可展示的文本，并负责把整理后的完整 AI 回复保存到对话历史。
             */
            return streamHandlerExecutor.doExecute(
                    originFlux,
                    chatHistoryService,
                    appId,
                    loginUser,
                    codeGenType
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
