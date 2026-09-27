package com.tmz.aicode.innerservice;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static com.tmz.aicode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务向其他服务暴露的内部接口。
 *
 * 这里只定义跨服务真正需要的最小能力，避免其他模块直接依赖用户服务的业务实现。
 */
public interface InnerUserService {

    /**
     * 根据一组用户编号批量查询用户。
     *
     * @param ids 用户编号集合
     * @return 匹配的用户列表
     */
    List<User> listByIds(Collection<? extends Serializable> ids);

    /**
     * 根据用户编号查询用户。
     *
     * @param id 用户编号
     * @return 用户实体
     */
    User getById(Serializable id);

    /**
     * 将用户实体转换为可对外展示的视图对象。
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    UserVO getUserVO(User user);

    /**
     * 从当前 HTTP 会话中读取登录用户。
     *
     * 该方法只读取当前进程中的请求对象，因此声明为静态方法，不参与后续 RPC 调用。
     *
     * @param request 当前 HTTP 请求
     * @return 已登录用户
     * @throws BusinessException 当前会话不存在有效用户时抛出
     */
    static User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
}
