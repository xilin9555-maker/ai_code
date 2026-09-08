package com.tmz.aicode.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.tmz.aicode.annotation.AuthCheck;
import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.DeleteRequest;
import com.tmz.aicode.common.ResultUtils;
import com.tmz.aicode.constant.UserConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.dto.user.UserAddRequest;
import com.tmz.aicode.model.dto.user.UserLoginRequest;
import com.tmz.aicode.model.dto.user.UserQueryRequest;
import com.tmz.aicode.model.dto.user.UserRegisterRequest;
import com.tmz.aicode.model.dto.user.UserUpdateRequest;
import com.tmz.aicode.model.dto.user.UserUpdateMyRequest;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.UserRoleEnum;
import com.tmz.aicode.model.vo.LoginUserVO;
import com.tmz.aicode.model.vo.UserVO;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户 控制层。
 *
 * @author tmz
 */
@RestController
@RequestMapping("/user")
public class UserController {

    /**
     * 管理员创建用户时使用的初始密码。
     */
    private static final String DEFAULT_PASSWORD = "12345678";

    @Autowired
    private UserService userService;

    /**
     * 注册一个新的普通用户。
     *
     * 控制器只负责读取请求内容并交给服务层处理，所有注册规则都集中在服务层，
     * 这样其他入口需要注册用户时也能复用相同的校验和保存逻辑。
     *
     * @param userRegisterRequest 账号、密码和确认密码
     * @return 新用户的 id
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        long userId = userService.userRegister(
                userRegisterRequest.getUserAccount(),
                userRegisterRequest.getUserPassword(),
                userRegisterRequest.getCheckPassword()
        );
        return ResultUtils.success(userId);
    }

    /**
     * 使用账号和密码登录。
     *
     * @param userLoginRequest 登录账号和密码
     * @param request 当前 HTTP 请求，用于记录用户的登录状态
     * @return 脱敏后的登录用户信息
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUserVO = userService.userLogin(
                userLoginRequest.getUserAccount(),
                userLoginRequest.getUserPassword(),
                request
        );
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前已经登录的用户。
     *
     * @param request 当前 HTTP 请求，用于读取对应的 Session
     * @return 脱敏后的最新用户信息
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User currentUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(currentUser));
    }

    /**
     * 注销当前用户。
     *
     * @param request 当前 HTTP 请求，用于找到对应的 Session
     * @return 是否成功清除登录状态
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(userService.userLogout(request));
    }

    /**
     * 管理员创建用户。
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        user.setUserPassword(userService.getEncryptPassword(DEFAULT_PASSWORD));
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 管理员根据 id 获取完整用户记录。
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取脱敏后的用户信息。
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 管理员根据 id 永久删除用户。
     *
     * 删除操作会直接移除数据库记录，不能通过逻辑删除标记恢复。当前管理员不能
     * 删除自己的账号，避免当前会话指向已经不存在的用户记录。
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest,
                                            HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser.getId().equals(deleteRequest.getId()),
                ErrorCode.OPERATION_ERROR, "不能删除当前登录账号");
        boolean deleted = userService.deleteUserPermanently(deleteRequest.getId());
        ThrowUtils.throwIf(!deleted, ErrorCode.NOT_FOUND_ERROR, "用户不存在或已经被删除");
        return ResultUtils.success(true);
    }

    /**
     * 管理员更新用户资料和角色。
     *
     * 更新前会确认用户真实存在，并检查角色值是否属于系统支持的范围。当前管理员可以
     * 修改自己的昵称、头像和简介，但不能在登录期间取消自己的管理员身份，避免保存后
     * 立即失去管理入口。账号和密码不属于这个接口的编辑范围。
     *
     * @param userUpdateRequest 需要更新的用户 id、公开资料和角色
     * @param request 当前 HTTP 请求，用于确认操作者和目标用户是否为同一账号
     * @return 更新成功时返回 true
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
                                            HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null
                || userUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User targetUser = userService.getById(userUpdateRequest.getId());
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        String userName = userUpdateRequest.getUserName();
        if (userName == null || userName.isBlank() || userName.length() > 256) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户昵称不能为空且不能超过 256 个字符");
        }
        String userAvatar = userUpdateRequest.getUserAvatar();
        ThrowUtils.throwIf(userAvatar != null && userAvatar.length() > 1024,
                ErrorCode.PARAMS_ERROR, "头像地址不能超过 1024 个字符");
        String userProfile = userUpdateRequest.getUserProfile();
        ThrowUtils.throwIf(userProfile != null && userProfile.length() > 512,
                ErrorCode.PARAMS_ERROR, "个人简介不能超过 512 个字符");

        String userRole = userUpdateRequest.getUserRole();
        ThrowUtils.throwIf(UserRoleEnum.getEnumByValue(userRole) == null,
                ErrorCode.PARAMS_ERROR, "用户角色不合法");

        User loginUser = userService.getLoginUser(request);
        boolean editingSelf = loginUser.getId().equals(userUpdateRequest.getId());
        boolean removingOwnAdminRole = editingSelf && !UserConstant.ADMIN_ROLE.equals(userRole);
        ThrowUtils.throwIf(removingOwnAdminRole,
                ErrorCode.OPERATION_ERROR, "不能取消当前登录账号的管理员权限");

        User user = new User();
        BeanUtil.copyProperties(userUpdateRequest, user);
        user.setUserName(userName.trim());
        user.setEditTime(LocalDateTime.now());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "用户信息更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 当前登录用户修改自己的公开资料。
     *
     * 目标用户 id 只从 Session 中获取，不接受客户端指定，从入口上阻止普通用户越权
     * 修改他人资料。这个接口也不会接收角色、账号和密码，因此个人设置页面只能更新
     * 昵称、头像和简介。
     *
     * @param userUpdateMyRequest 新的昵称、头像地址和个人简介
     * @param request 当前 HTTP 请求，用于读取 Session 中的登录用户
     * @return 更新成功时返回 true
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(userUpdateMyRequest == null, ErrorCode.PARAMS_ERROR);
        String userName = userUpdateMyRequest.getUserName();
        if (userName == null || userName.isBlank() || userName.length() > 256) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户昵称不能为空且不能超过 256 个字符");
        }
        String userAvatar = userUpdateMyRequest.getUserAvatar();
        ThrowUtils.throwIf(userAvatar != null && userAvatar.length() > 1024,
                ErrorCode.PARAMS_ERROR, "头像地址不能超过 1024 个字符");
        String userProfile = userUpdateMyRequest.getUserProfile();
        ThrowUtils.throwIf(userProfile != null && userProfile.length() > 512,
                ErrorCode.PARAMS_ERROR, "个人简介不能超过 512 个字符");

        User loginUser = userService.getLoginUser(request);
        User user = new User();
        user.setId(loginUser.getId());
        user.setUserName(userName.trim());
        user.setUserAvatar(userAvatar);
        user.setUserProfile(userProfile);
        user.setEditTime(LocalDateTime.now());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "个人资料更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页查询脱敏后的用户列表。
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0, ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        ThrowUtils.throwIf(pageSize <= 0, ErrorCode.PARAMS_ERROR, "每页数量必须大于 0");
        Page<User> userPage = userService.page(
                Page.of(pageNum, pageSize),
                userService.getQueryWrapper(userQueryRequest)
        );

        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        userVOPage.setRecords(userService.getUserVOList(userPage.getRecords()));
        return ResultUtils.success(userVOPage);
    }

}
