package com.tmz.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户创建的应用记录。
 *
 * 这个类与数据库中的 {@code app} 表一一对应，保存应用最初的生成需求、生成方式、
 * 部署状态以及创建人等信息。网页代码文件不直接存入这张表，而是由代码生成模块保存。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用的唯一标识。
     *
     * 服务端使用雪花算法生成 id，创建记录后可以立即拿到稳定的应用编号，后续生成代码、
     * 部署和查询都可以使用同一个编号关联。
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 应用名称，用于列表卡片和详情页展示。
     */
    @Column("appName")
    private String appName;

    /**
     * 应用封面图片地址，没有设置时由前端展示默认封面。
     */
    @Column("cover")
    private String cover;

    /**
     * 用户首次创建应用时提交的需求描述。
     *
     * 后续生成网站代码时会把这段文字作为核心输入，因此需要完整保留。
     */
    @Column("initPrompt")
    private String initPrompt;

    /**
     * 代码生成类型，对应 {@code CodeGenTypeEnum} 中的稳定值。
     */
    @Column("codeGenType")
    private String codeGenType;

    /**
     * 对外部署时使用的唯一标识。
     */
    @Column("deployKey")
    private String deployKey;

    /**
     * 最近一次部署成功的时间。
     */
    @Column("deployedTime")
    private LocalDateTime deployedTime;

    /**
     * 展示优先级，数值越高越适合排在前面。
     */
    @Column("priority")
    private Integer priority;

    /**
     * 创建该应用的用户 id。
     */
    @Column("userId")
    private Long userId;

    /**
     * 用户最后一次主动编辑应用信息的时间。
     */
    @Column("editTime")
    private LocalDateTime editTime;

    /**
     * 应用创建时间，由数据库首次写入时自动填写。
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 数据记录最后一次更新时间，由数据库在字段变化时自动刷新。
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：{@code 0} 表示正常，{@code 1} 表示已删除。
     *
     * 常规查询会自动排除已删除记录，避免用户继续看到已经移除的应用。
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
