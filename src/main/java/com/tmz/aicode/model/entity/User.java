package com.tmz.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户在系统中的完整数据记录。
 *
 * 这个类直接对应数据库中的 {@code user} 表。账号凭证、公开资料、角色和审计时间
 * 都集中放在这里，方便数据访问层用同一种结构完成新增、查询与更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户的唯一标识。
     *
     * 由应用生成雪花 ID。即使数据分散到多台服务或多个数据库节点，
     * 也能在不依赖数据库自增序列的情况下生成基本有序且不重复的编号。
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 登录账号。
     *
     * 账号用于识别登录用户，在数据库中具有唯一索引，同一个账号不能重复注册。
     */
    @Column("userAccount")
    private String userAccount;

    /**
     * 加密后的登录密码。
     *
     * 这里只保存经过加密处理的结果，不应保存或向外返回用户输入的明文密码。
     */
    @Column("userPassword")
    private String userPassword;

    /**
     * 用户昵称，用于页面展示。
     *
     * 昵称可以重复，也允许用户在后续完善资料时修改。
     */
    @Column("userName")
    private String userName;

    /**
     * 用户头像地址。
     *
     * 保存可访问的图片地址；没有设置头像时可以为空，由前端展示默认头像。
     */
    @Column("userAvatar")
    private String userAvatar;

    /**
     * 用户的个人简介。
     *
     * 这是一段可选的公开资料，用来帮助其他人快速了解该用户。
     */
    @Column("userProfile")
    private String userProfile;

    /**
     * 用户角色。
     *
     * 当前支持普通用户 {@code user} 和管理员 {@code admin}，业务代码应优先使用
     * {@code UserRoleEnum} 中定义的值，减少手写字符串带来的拼写错误。
     */
    @Column("userRole")
    private String userRole;

    /**
     * 用户资料最后一次主动编辑的时间。
     *
     * 它表达的是用户修改昵称、头像或简介等资料的时间，需要由业务代码主动维护。
     */
    @Column("editTime")
    private LocalDateTime editTime;

    /**
     * 账号创建时间，由数据库在首次写入记录时自动填写。
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 数据记录最后一次更新时间。
     *
     * 任意字段发生更新时，数据库会自动刷新这个时间，便于追踪整条记录的变化。
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：{@code 0} 表示正常，{@code 1} 表示已删除。
     *
     * MyBatis-Flex 会在常规查询中自动排除已删除的数据，让记录保留在数据库中，
     * 同时不会继续出现在正常业务结果里。
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;

}
