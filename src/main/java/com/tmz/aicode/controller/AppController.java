package com.tmz.aicode.controller;

import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.tmz.aicode.annotation.AuthCheck;
import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.DeleteRequest;
import com.tmz.aicode.common.ResultUtils;
import com.tmz.aicode.config.RedisCacheManagerConfig;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.constant.UserConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.dto.app.AppAddRequest;
import com.tmz.aicode.model.dto.app.AppAdminUpdateRequest;
import com.tmz.aicode.model.dto.app.AppDeployRequest;
import com.tmz.aicode.model.dto.app.AppQueryRequest;
import com.tmz.aicode.model.dto.app.AppUpdateRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.model.vo.AppVO;
import com.tmz.aicode.model.vo.GenerationStreamEvent;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ProjectDownloadService;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 应用基础能力接口。
 *
 * 普通用户可以创建和管理自己的应用，也可以浏览精选应用；管理员接口额外提供全量查询、
 * 任意应用维护和删除能力。所有响应继续使用项目统一的 BaseResponse 结构。
 */
@RestController
@RequestMapping("/app")
public class AppController {

    /**
     * 普通列表接口单页允许返回的最大数量，防止一次请求读取过多数据。
     */
    private static final int USER_PAGE_SIZE_LIMIT = 20;

    /**
     * 数据库中应用名称字段允许的最大长度。
     */
    private static final int APP_NAME_MAX_LENGTH = 256;

    /**
     * 数据库中封面地址字段允许的最大长度。
     */
    private static final int APP_COVER_MAX_LENGTH = 512;

    private final AppService appService;

    private final UserService userService;

    private final ProjectDownloadService projectDownloadService;

    public AppController(AppService appService,
                         UserService userService,
                         ProjectDownloadService projectDownloadService) {
        this.appService = appService;
        this.userService = userService;
        this.projectDownloadService = projectDownloadService;
    }

    /**
     * 创建应用。
     *
     * Controller 只负责确认请求对象和当前登录用户，需求校验、类型选择及数据库写入由
     * AppService 统一完成，避免接口层承载不断增长的业务规则。
     *
     * @param appAddRequest 创建应用时填写的初始化需求
     * @param request 当前请求，用于读取已经登录的用户
     * @return 创建成功后的应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest,
                                     HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.createApp(appAddRequest, loginUser));
    }

    /**
     * 修改当前用户自己的应用名称。
     *
     * @param appUpdateRequest 应用 id 和新名称
     * @param request 当前请求，用于确认应用所有者
     * @return 更新成功时返回 {@code true}
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest,
                                            HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null
                || appUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String appName = validateAndNormalizeAppName(appUpdateRequest.getAppName());
        User loginUser = userService.getLoginUser(request);
        App oldApp = getExistingApp(appUpdateRequest.getId());
        ThrowUtils.throwIf(!Objects.equals(oldApp.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能修改自己创建的应用");

        App app = new App();
        app.setId(oldApp.getId());
        app.setAppName(appName);
        app.setEditTime(LocalDateTime.now());
        boolean updated = appService.updateById(app);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "应用更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除应用。
     *
     * 普通用户只能删除自己的应用，管理员也可以通过这个入口处理任意应用。实体已配置逻辑
     * 删除，因此记录会被标记为已删除，并从后续常规查询结果中排除。
     *
     * @param deleteRequest 要删除的应用 id
     * @param request 当前请求，用于校验操作者身份
     * @return 删除成功时返回 {@code true}
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest,
                                            HttpServletRequest request) {
        Long id = validateDeleteRequest(deleteRequest);
        User loginUser = userService.getLoginUser(request);
        App oldApp = getExistingApp(id);
        boolean isOwner = Objects.equals(oldApp.getUserId(), loginUser.getId());
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isOwner && !isAdmin,
                ErrorCode.NO_AUTH_ERROR, "只能删除自己创建的应用");

        boolean removed = appService.removeById(id);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "应用删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取应用详情。
     *
     * @param id 应用 id
     * @return 应用信息以及创建者的公开资料
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.getAppVO(getExistingApp(id)));
    }

    /**
     * 获取当前用户自己创建的应用详情。
     *
     * 编辑页面使用该接口读取数据。应用 id 即使被人为替换，服务端仍会根据 Session 中的
     * 登录用户校验所有者，避免只依赖前端路由判断造成越权访问。
     *
     * @param id 应用 id
     * @param request 当前请求，用于读取登录用户
     * @return 当前用户拥有的应用详情
     */
    @GetMapping("/my/get/vo")
    public BaseResponse<AppVO> getMyAppVOById(@RequestParam long id,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        App app = getExistingApp(id);
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能查看自己创建的应用");
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用。
     *
     * userId 始终由 Session 中的登录用户确定，不接受客户端指定。这样即使请求体带有别人的
     * userId，也不能借此查看他人的个人应用列表。
     *
     * @param appQueryRequest 页码、应用名称和排序条件
     * @param request 当前请求，用于读取登录用户
     * @return 当前用户的应用分页结果
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(
            @RequestBody AppQueryRequest appQueryRequest,
            HttpServletRequest request) {
        validateUserPageRequest(appQueryRequest);
        User loginUser = userService.getLoginUser(request);

        AppQueryRequest safeQuery = copyPublicQuery(appQueryRequest);
        safeQuery.setUserId(loginUser.getId());
        return ResultUtils.success(queryAppVOPage(safeQuery));
    }

    /**
     * 分页获取精选应用。
     *
     * 精选条件由服务端固定写入，客户端不能通过传入其他优先级扩大查询范围。
     *
     * @param appQueryRequest 页码、应用名称和排序条件
     * @return 精选应用分页结果
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            cacheNames = RedisCacheManagerConfig.GOOD_APP_PAGE_CACHE,
            key = "T(com.tmz.aicode.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest != null && #appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(
            @RequestBody AppQueryRequest appQueryRequest) {
        validateUserPageRequest(appQueryRequest);

        AppQueryRequest safeQuery = copyPublicQuery(appQueryRequest);
        safeQuery.setPriority(AppConstant.GOOD_APP_PRIORITY);
        return ResultUtils.success(queryAppVOPage(safeQuery));
    }

    /**
     * 通过 SSE 持续返回应用代码生成结果。
     *
     * GET 请求便于浏览器直接使用 EventSource 建立连接，text/event-stream 告诉客户端保持
     * 响应通道并逐条处理数据。Controller 只完成入口参数和登录状态校验，应用所有权、
     * 生成类型选择、代码解析与文件保存统一交给应用服务及生成门面处理。
     *
     * @param appId 需要继续生成代码的应用 id
     * @param message 用户本次提交的网站需求
     * @param agent 是否使用 AI 工作流模式
     * @param request 当前 HTTP 请求，用于读取 Session 中的登录用户
     * 普通事件把回复片段放入 JSON 的 d 字段，构建阶段使用具名事件携带结构化进度。
     * 生成流正常结束后发送 done，前端此时才能刷新预览；如果上游异常则只发送错误事件。
     *
     * @return 回复、构建进度、心跳，以及正常结束时的 done 事件
     */
    @GetMapping(value = "/chat/gen/code",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       @RequestParam(defaultValue = "false")
                                                       boolean agent,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message),
                ErrorCode.PARAMS_ERROR, "用户消息不能为空");

        // 明确字符集并禁止中间代理缓冲，确保中文代码片段到达后可以立即被浏览器处理。
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        // 登录用户从服务端 Session 中取得，客户端不能通过请求参数伪造用户身份。
        User loginUser = userService.getLoginUser(request);
        Flux<GenerationStreamEvent> contentFlux = appService.chatToGenCode(
                appId, message, loginUser, agent);

        Flux<ServerSentEvent<String>> businessFlux = contentFlux
                .map(streamEvent -> {
                    String jsonData = JSONUtil.toJsonStr(streamEvent.getData());
                    ServerSentEvent.Builder<String> builder =
                            ServerSentEvent.<String>builder().data(jsonData);
                    // 普通消息不指定 event，继续由 EventSource.onmessage 接收。
                    if (!GenerationStreamEvent.MESSAGE_EVENT.equals(streamEvent.getEvent())) {
                        builder.event(streamEvent.getEvent());
                    }
                    return builder.build();
                })
                .concatWith(Mono.just(
                        // concatWith 只会在代码流正常完成后执行，因此 done 可以作为成功结束标志。
                        ServerSentEvent.<String>builder()
                                .event("done")
                                // 非空数据可以确保浏览器稳定派发这个自定义事件。
                                .data("{\"completed\":true}")
                                .build()
                ));
        businessFlux = businessFlux.onErrorResume(error -> {
            /*
             * SSE 响应开始后不能再交给全局 JSON 异常处理器，否则会因为响应类型不兼容
             * 产生第二个异常。改为具名事件后，前端既能展示失败原因，也不会误收 done。
             */
            String detail = StrUtil.blankToDefault(error.getMessage(), "未知错误");
            if (detail.length() > 500) {
                detail = detail.substring(0, 500);
            }
            String errorData = JSONUtil.toJsonStr(Map.of(
                    "message", "生成失败：" + detail
            ));
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("generation_error")
                    .data(errorData)
                    .build());
        });

        /*
         * 模型生成和 npm 构建都可能较久没有业务片段。定时注释不会触发前端消息事件，
         * 但能让浏览器、网关和反向代理知道连接仍然存活。业务流结束后心跳也立即停止。
         */
        return businessFlux.publish(shared -> {
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                    .map(sequence -> ServerSentEvent.<String>builder()
                            .comment("keep-alive")
                            .build())
                    .takeUntilOther(shared.ignoreElements());
            return Flux.merge(shared, heartbeat);
        });
    }

    /**
     * 部署当前用户已经生成过代码的应用。
     *
     * Controller 只负责读取请求参数和登录身份，目录检查、所有权判断、文件复制及数据库
     * 更新全部由应用服务完成，成功后返回可以直接访问的稳定地址。
     *
     * @param appDeployRequest 需要部署的应用 id
     * @param request 当前 HTTP 请求，用于读取 Session 中的登录用户
     * @return 部署完成后的公开访问地址
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null,
                ErrorCode.PARAMS_ERROR, "部署请求不能为空");
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");

        User loginUser = userService.getLoginUser(request);
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载当前用户创建的应用源代码。
     *
     * 下载的是代码生成工作目录，而不是部署目录。对于 Vue 工程，压缩包会保留可继续修改的
     * 源码和 package.json，并过滤 node_modules、dist 等可以重新生成的内容。当前登录用户
     * 必须是应用创建者，避免通过猜测应用 id 下载其他用户的代码。
     *
     * @param appId   需要下载的应用 id
     * @param request 当前 HTTP 请求，用于读取 Session 中的登录用户
     * @param response ZIP 文件会直接写入这个响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 无效");

        App app = getExistingApp(appId);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");

        // 使用数据库中受控的生成类型构造目录，拒绝异常类型进入服务器文件路径。
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null,
                ErrorCode.SYSTEM_ERROR, "应用的代码生成类型不受支持");
        String sourceDirName = codeGenType.getValue() + "_" + appId;
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        ThrowUtils.throwIf(!sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");

        // 应用 id 只包含数字，作为下载文件名能够避免中文和特殊字符造成响应头兼容问题。
        projectDownloadService.downloadProjectAsZip(
                sourceDir.getAbsolutePath(),
                String.valueOf(appId),
                response
        );
    }

    /**
     * 管理员删除任意应用。
     *
     * @param deleteRequest 要删除的应用 id
     * @return 删除成功时返回 {@code true}
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        Long id = validateDeleteRequest(deleteRequest);
        getExistingApp(id);
        boolean removed = appService.removeById(id);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "应用删除失败");
        return ResultUtils.success(true);
    }

    /**
     * 管理员更新应用展示信息。
     *
     * 名称、封面和优先级都采用按需更新。至少需要提交一个可修改字段，修改优先级为精选值
     * 后，该应用就会出现在精选应用列表中。
     *
     * @param updateRequest 应用 id 以及需要更新的字段
     * @return 更新成功时返回 {@code true}
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(
            @RequestBody AppAdminUpdateRequest updateRequest) {
        if (updateRequest == null || updateRequest.getId() == null
                || updateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        getExistingApp(updateRequest.getId());
        ThrowUtils.throwIf(updateRequest.getAppName() == null
                        && updateRequest.getCover() == null
                        && updateRequest.getPriority() == null,
                ErrorCode.PARAMS_ERROR, "至少填写一个需要更新的字段");

        App app = new App();
        app.setId(updateRequest.getId());
        if (updateRequest.getAppName() != null) {
            app.setAppName(validateAndNormalizeAppName(updateRequest.getAppName()));
        }
        if (updateRequest.getCover() != null) {
            ThrowUtils.throwIf(updateRequest.getCover().length() > APP_COVER_MAX_LENGTH,
                    ErrorCode.PARAMS_ERROR, "封面地址不能超过 512 个字符");
            app.setCover(updateRequest.getCover().trim());
        }
        if (updateRequest.getPriority() != null) {
            ThrowUtils.throwIf(updateRequest.getPriority() < 0,
                    ErrorCode.PARAMS_ERROR, "应用优先级不能小于 0");
            app.setPriority(updateRequest.getPriority());
        }
        app.setEditTime(LocalDateTime.now());

        boolean updated = appService.updateById(app);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "应用更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页查询全部应用。
     *
     * @param appQueryRequest 完整的分页、筛选和排序条件
     * @return 应用分页结果
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(
            @RequestBody AppQueryRequest appQueryRequest) {
        validateBasePageRequest(appQueryRequest);
        return ResultUtils.success(queryAppVOPage(appQueryRequest));
    }

    /**
     * 管理员根据 id 获取应用详情。
     *
     * @param id 应用 id
     * @return 应用信息以及创建者的公开资料
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(appService.getAppVO(getExistingApp(id)));
    }

    /**
     * 查询应用分页数据，并将数据库实体统一转换为视图对象。
     */
    private Page<AppVO> queryAppVOPage(AppQueryRequest queryRequest) {
        long pageNum = queryRequest.getPageNum();
        long pageSize = queryRequest.getPageSize();
        Page<App> appPage = appService.page(
                Page.of(pageNum, pageSize),
                appService.getQueryWrapper(queryRequest)
        );
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return appVOPage;
    }

    /**
     * 只复制普通列表允许使用的名称和排序条件，受保护条件随后由服务端设置。
     */
    private AppQueryRequest copyPublicQuery(AppQueryRequest source) {
        AppQueryRequest target = new AppQueryRequest();
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
        target.setSortField(source.getSortField());
        target.setSortOrder(source.getSortOrder());
        target.setAppName(source.getAppName());
        return target;
    }

    /**
     * 检查普通列表的页码与单页数量。
     */
    private void validateUserPageRequest(AppQueryRequest queryRequest) {
        validateBasePageRequest(queryRequest);
        ThrowUtils.throwIf(queryRequest.getPageSize() > USER_PAGE_SIZE_LIMIT,
                ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
    }

    /**
     * 检查所有分页接口都必须满足的基础条件。
     */
    private void validateBasePageRequest(AppQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(queryRequest.getPageNum() <= 0,
                ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        ThrowUtils.throwIf(queryRequest.getPageSize() <= 0,
                ErrorCode.PARAMS_ERROR, "每页数量必须大于 0");
    }

    /**
     * 查询一条仍然有效的应用记录，找不到时返回统一的业务异常。
     */
    private App getExistingApp(long id) {
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在或已经被删除");
        return app;
    }

    /**
     * 检查删除请求并返回已经验证过的应用 id。
     */
    private Long validateDeleteRequest(DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() == null
                || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return deleteRequest.getId();
    }

    /**
     * 检查应用名称并去掉首尾空白。
     */
    private String validateAndNormalizeAppName(String appName) {
        ThrowUtils.throwIf(StrUtil.isBlank(appName),
                ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        String normalizedName = appName.trim();
        ThrowUtils.throwIf(normalizedName.length() > APP_NAME_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR, "应用名称不能超过 256 个字符");
        return normalizedName;
    }
}
