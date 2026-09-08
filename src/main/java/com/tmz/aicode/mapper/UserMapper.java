package com.tmz.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.tmz.aicode.model.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 用户 映射层。
 *
 * @author tmz
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户 id 从数据库中永久删除记录。
     *
     * 这里使用明确的 DELETE 语句绕过实体上的逻辑删除配置，数据删除后无法通过
     * 修改 isDelete 字段恢复，因此只能由经过权限校验的管理业务调用。
     *
     * @param userId 需要永久删除的用户 id
     * @return 数据库实际删除的记录数
     */
    @Delete("DELETE FROM `user` WHERE id = #{userId}")
    int deleteByIdPermanently(@Param("userId") Long userId);

}
