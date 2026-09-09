package com.tmz.aicode.controller;

import cn.hutool.core.io.FileUtil;
import com.tmz.aicode.constant.AppConstant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 生成网站预览接口的本地文件测试。
 *
 * 测试只创建一个临时 HTML 文件，结束后立即删除，不连接数据库或模型服务。
 */
class StaticResourceControllerTest {

    /**
     * 访问生成目录根路径时应返回 index.html，并声明正确的 UTF-8 HTML 类型。
     */
    @Test
    void serveStaticResourceReturnsIndexPage() throws Exception {
        String directoryName = "multi_file_930001";
        File directory = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, directoryName);
        File indexFile = new File(directory, "index.html");

        try {
            FileUtil.mkdir(directory);
            FileUtil.writeString("<h1>预览页面</h1>", indexFile, StandardCharsets.UTF_8);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRequestURI("/api/static/" + directoryName + "/");
            request.setAttribute(
                    HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                    "/static/" + directoryName + "/"
            );

            ResponseEntity<Resource> response = new StaticResourceController()
                    .serveStaticResource(directoryName, request);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("text/html; charset=UTF-8",
                    response.getHeaders().getFirst("Content-Type"));
            assertEquals("<h1>预览页面</h1>",
                    response.getBody().getContentAsString(StandardCharsets.UTF_8));
        } finally {
            FileUtil.del(directory);
        }
    }
}
