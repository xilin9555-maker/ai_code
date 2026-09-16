package com.tmz.aicode.utils;

import com.tmz.aicode.model.dto.app.AppQueryRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 缓存 Key 工具的本地单元测试，不需要启动 Spring 或连接 Redis。
 */
class CacheKeyUtilsTest {

    @Test
    void sameQueryContentGeneratesSameKey() {
        AppQueryRequest first = createQuery(1, 10, "作品集");
        AppQueryRequest second = createQuery(1, 10, "作品集");

        assertEquals(
                CacheKeyUtils.generateKey(first),
                CacheKeyUtils.generateKey(second)
        );
    }

    @Test
    void differentQueryContentGeneratesDifferentKey() {
        AppQueryRequest first = createQuery(1, 10, "作品集");
        AppQueryRequest second = createQuery(2, 10, "作品集");

        assertNotEquals(
                CacheKeyUtils.generateKey(first),
                CacheKeyUtils.generateKey(second)
        );
    }

    @Test
    void nullValueAlsoGeneratesStableKey() {
        String first = CacheKeyUtils.generateKey(null);
        String second = CacheKeyUtils.generateKey(null);

        assertEquals(first, second);
        assertEquals(32, first.length());
    }

    private AppQueryRequest createQuery(int pageNum, int pageSize, String appName) {
        AppQueryRequest queryRequest = new AppQueryRequest();
        queryRequest.setPageNum(pageNum);
        queryRequest.setPageSize(pageSize);
        queryRequest.setAppName(appName);
        return queryRequest;
    }
}
