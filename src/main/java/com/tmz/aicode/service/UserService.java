package com.tmz.aicode.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.tmz.aicode.model.dto.user.UserQueryRequest;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.vo.LoginUserVO;
import com.tmz.aicode.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 用户 服务层。
 *
 * @author tmz
 */
public interface UserService extends IService<User> {

    /**
     * 注册一个普通用户。
     *
     * 服务层会统一检查账号和密码是否合法、账号是否已存在，并在保存前加密密码。
     *
     * @param userAccount 用户用于登录的账号
     * @param userPassword 用户输入的密码
     * @param checkPassword 用户再次输入的确认密码
     * @return 注册成功后生成的用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 将用户输入的密码转换为数据库中保存的加密结果。
     *
     * 注册和登录必须调用同一个方法，才能保证相同的原始密码得到一致的结果。
     *
     * @param userPassword 用户输入的原始密码
     * @return 加密后的密码字符串
     */
    String getEncryptPassword(String userPassword);

    /**
     * 将数据库用户对象转换成可以安全返回给登录用户的视图。
     *
     * @param user 数据库中查询到的用户对象
     * @return 脱敏后的登录用户信息；传入 {@code null} 时返回 {@code null}
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 校验账号和密码并建立登录状态。
     *
     * @param userAccount 用户账号
     * @param userPassword 用户输入的原始密码
     * @param request 当前 HTTP 请求，用于取得并更新对应的 Session
     * @return 脱敏后的登录用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前请求对应的登录用户。
     *
     * 先从 Session 中读取登录状态，再根据用户 id 查询数据库中的最新记录。
     *
     * @param request 当前 HTTP 请求
     * @return 数据库中的最新用户记录
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 注销当前用户并清除 Session 中的登录状态。
     *
     * @param request 当前 HTTP 请求
     * @return 登录状态成功移除时返回 {@code true}
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 将用户实体转换成可对外返回的用户视图。
     *
     * @param user 用户实体
     * @return 脱敏后的用户信息；传入 {@code null} 时返回 {@code null}
     */
    UserVO getUserVO(User user);

    /**
     * 批量转换用户视图。
     *
     * @param userList 用户实体列表
     * @return 脱敏后的用户列表，输入为空时返回空列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 根据用户管理页面的筛选和排序参数构造查询条件。
     *
     * @param userQueryRequest 用户查询请求
     * @return MyBatis-Flex 查询条件
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

}
