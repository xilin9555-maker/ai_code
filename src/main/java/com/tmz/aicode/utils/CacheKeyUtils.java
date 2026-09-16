package com.tmz.aicode.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;

/**
 * 缓存 Key 生成工具。
 *
 * 查询参数先序列化为 JSON，再计算固定长度的 MD5。内容相同的请求会得到同一个 Key，
 * 参数不同的请求则会进入各自的缓存项，同时避免直接把较长的 JSON 放入 Redis Key。
 */
public final class CacheKeyUtils {

    private CacheKeyUtils() {
        // 工具类只提供静态方法，不需要创建实例。
    }

    /**
     * 根据对象内容生成稳定的缓存 Key。
     *
     * @param object 参与缓存区分的查询对象
     * @return 查询内容对应的 MD5 字符串
     */
    public static String generateKey(Object object) {
        if (object == null) {
            return DigestUtil.md5Hex("null");
        }
        String json = JSONUtil.toJsonStr(object);
        return DigestUtil.md5Hex(json);
    }
}
