package com.tmz.aicode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.mapper.UserMapper;
import com.tmz.aicode.model.dto.user.UserQueryRequest;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.UserRoleEnum;
import com.tmz.aicode.model.vo.LoginUserVO;
import com.tmz.aicode.model.vo.UserVO;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tmz.aicode.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author tmz
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 密码加密时加入的固定盐值，避免直接保存原始密码的摘要。
     */
    private static final String PASSWORD_SALT = "ai_code_password_salt";

    /**
     * 用户列表允许使用的排序字段。
     */
    private static final Set<String> USER_SORT_FIELDS = Set.of(
            "id", "userAccount", "userName", "userRole", "editTime", "createTime", "updateTime"
    );

    /**
     * 创建一个普通用户账号。
     *
     * 这里集中完成必填校验、长度校验、两次密码确认、账号查重、密码处理和用户落库，
     * 确保任何调用注册能力的入口都遵循同一套规则。
     *
     * @param userAccount 用户用于登录的账号
     * @param userPassword 用户输入的原始密码
     * @param checkPassword 用户再次输入的确认密码
     * @return 注册成功后生成的用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 先拦截空白参数，避免后面的长度比较和数据库查询处理无效数据。
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }

        // 在写入前检查账号是否已经存在，让用户能得到清楚的重复注册提示。
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }

        // 数据库只保存处理后的密码，同时为新账号设置默认昵称和普通用户角色。
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncryptPassword(userPassword));
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 使用固定盐值计算密码摘要。
     *
     * 注册和登录共用这个方法，保证同一密码在两个流程中得到一致的比较结果。
     *
     * @param userPassword 用户输入的原始密码
     * @return 可用于数据库保存和登录比较的密码摘要
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        byte[] passwordBytes = (PASSWORD_SALT + userPassword).getBytes(StandardCharsets.UTF_8);
        return DigestUtils.md5DigestAsHex(passwordBytes);
    }

    /**
     * 把用户实体转换为当前登录用户视图。
     *
     * 目标对象只声明允许返回的字段，复制过程中会自然过滤密码和删除状态。
     *
     * @param user 用户实体
     * @return 脱敏后的登录用户；输入为空时返回 {@code null}
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        // 只复制视图类中存在的同名字段，密码等敏感字段不会进入返回对象。
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 校验账号密码并建立用户会话。
     *
     * 密码按照注册时相同的规则处理后参与查询；匹配成功时把用户写入当前 Session，
     * 让后续请求能够识别用户身份。
     *
     * @param userAccount 用户账号
     * @param userPassword 用户输入的原始密码
     * @param request 当前 HTTP 请求
     * @return 脱敏后的登录用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 登录参数只要存在空白内容，就没有继续查询数据库的必要。
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }

        // 使用和注册时相同的处理方式生成密码摘要，再与数据库中的结果进行匹配。
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userAccount", userAccount)
                .eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 登录成功后把用户放入当前会话，后续请求可以据此识别用户身份。
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return getLoginUserVO(user);
    }

    /**
     * 获取当前会话中的登录用户。
     *
     * Session 负责提供用户 id，随后重新查询数据库，避免返回登录时缓存的旧资料。
     *
     * @param request 当前 HTTP 请求
     * @return 数据库中的最新用户记录
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // Session 中没有有效用户时，说明当前请求尚未建立登录状态。
        Object userObject = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (!(userObject instanceof User sessionUser) || sessionUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // Session 保存的是登录时的数据，再查一次数据库可以拿到用户的最新资料和状态。
        User currentUser = this.getById(sessionUser.getId());
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 清除当前会话中的用户登录状态。
     *
     * 该操作只移除登录用户属性，不会删除用户记录，也不会影响 Session 中的其他数据。
     *
     * @param request 当前 HTTP 请求
     * @return 清除成功时返回 {@code true}
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 没有登录状态时不能执行注销，避免把重复操作误认为成功。
        Object userObject = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObject == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }

        // 只移除当前用户的登录标记，Session 中的其他业务数据仍然可以继续使用。
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 把单个用户实体转换为通用用户视图。
     *
     * @param user 用户实体
     * @return 脱敏后的用户信息；输入为空时返回 {@code null}
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        // UserVO 没有密码等内部字段，因此只会复制适合对外展示的同名属性。
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 批量转换用户实体列表。
     *
     * 空输入会转换为空列表，调用方可以直接遍历结果，不需要额外判断空指针。
     *
     * @param userList 用户实体列表
     * @return 脱敏后的用户视图列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    /**
     * 从数据库中永久删除指定用户。
     *
     * 普通的 removeById 会受到实体逻辑删除配置影响，只把 isDelete 更新为 1；
     * 这里调用 Mapper 中的明确 DELETE 语句，让数据库真正移除该用户记录。
     *
     * @param userId 需要永久删除的用户 id
     * @return 数据库成功删除一条记录时返回 {@code true}
     */
    @Override
    public boolean deleteUserPermanently(long userId) {
        if (userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户 id 不合法");
        }
        return this.mapper.deleteByIdPermanently(userId) > 0;
    }

    /**
     * 根据查询请求构造用户列表的数据库条件。
     *
     * 只有有效筛选值才会加入查询，同时限制可用排序字段，避免默认值误筛选数据或
     * 非法字段进入排序语句。
     *
     * @param userQueryRequest 用户筛选、分页和排序参数
     * @return 可交给 MyBatis-Flex 执行的查询条件
     */
    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();
        // Knife4j 会为数字字段生成 0，只有正数 id 才应作为真实查询条件。
        if (id != null && id > 0) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(userRole)) {
            queryWrapper.eq("userRole", userRole);
        }
        if (StrUtil.isNotBlank(userAccount)) {
            queryWrapper.like("userAccount", userAccount);
        }
        if (StrUtil.isNotBlank(userName)) {
            queryWrapper.like("userName", userName);
        }
        if (StrUtil.isNotBlank(userProfile)) {
            queryWrapper.like("userProfile", userProfile);
        }
        if (StrUtil.isNotBlank(sortField)) {
            if (!USER_SORT_FIELDS.contains(sortField)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序字段不合法");
            }
            if (!"ascend".equals(sortOrder) && !"descend".equals(sortOrder)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "排序方式不合法");
            }
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }

}
