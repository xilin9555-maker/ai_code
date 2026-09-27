package com.tmz.aicode.innerservice.impl;

import com.tmz.aicode.innerservice.InnerUserService;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.vo.UserVO;
import com.tmz.aicode.service.UserService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 用户内部服务的 Dubbo 提供方实现。
 *
 * 该适配层只负责把远程请求转交给现有用户业务服务，不重复编写查询与对象转换逻辑。
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserService userService;

    /**
     * 批量查询用户，减少应用列表组装用户信息时的远程调用次数。
     *
     * @param ids 用户编号集合
     * @return 匹配的用户列表
     */
    @Override
    public List<User> listByIds(Collection<? extends Serializable> ids) {
        return userService.listByIds(ids);
    }

    /**
     * 根据编号查询单个用户。
     *
     * @param id 用户编号
     * @return 用户实体，不存在时返回 null
     */
    @Override
    public User getById(Serializable id) {
        return userService.getById(id);
    }

    /**
     * 复用用户服务的脱敏规则生成用户视图。
     *
     * @param user 用户实体
     * @return 可对外使用的用户视图
     */
    @Override
    public UserVO getUserVO(User user) {
        return userService.getUserVO(user);
    }
}
