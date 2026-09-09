package com.tmz.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.tmz.aicode.model.dto.app.AppQueryRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用业务服务。
 *
 * 除了继承通用的数据操作能力，这个接口还统一负责查询条件构造和视图对象转换，
 * 让控制器不需要了解数据库查询与用户信息组装的细节。
 */
public interface AppService extends IService<App> {

    /**
     * 把已经生成的应用代码发布到稳定的部署目录。
     *
     * @param appId 需要部署的应用 id
     * @param loginUser 当前登录用户，用于确认应用所有权
     * @return 可以交给浏览器访问的部署地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 根据用户消息为指定应用流式生成代码并保存。
     *
     * @param appId 需要生成代码的应用 id
     * @param message 用户本次提交的网站需求
     * @param loginUser 当前登录用户，用于校验应用所有权
     * @return 按生成顺序持续发出的代码文本片段
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 根据请求参数构造应用查询条件。
     *
     * @param appQueryRequest 分页、筛选和排序参数
     * @return 可以交给 MyBatis-Flex 执行的查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 将单个应用实体转换为包含创建者信息的视图对象。
     *
     * @param app 数据库中的应用实体
     * @return 对外展示的应用信息；传入 {@code null} 时返回 {@code null}
     */
    AppVO getAppVO(App app);

    /**
     * 批量转换应用视图，并一次性查询列表中涉及的用户。
     *
     * @param appList 应用实体列表
     * @return 包含创建者信息的应用列表，输入为空时返回空列表
     */
    List<AppVO> getAppVOList(List<App> appList);
}
