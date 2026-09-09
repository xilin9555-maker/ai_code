package com.tmz.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.tmz.aicode.model.entity.App;

/**
 * 应用数据访问层。
 *
 * BaseMapper 已经提供常用的新增、按 id 查询、更新、删除和分页查询能力。需要复杂 SQL 时，
 * 可以继续在这里声明方法，并在对应的 XML 文件中编写语句。
 */
public interface AppMapper extends BaseMapper<App> {
}
