package com.tmz.aicode;

import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.ResultUtils;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AiCodeApplicationTests.ExceptionTestConfig.class)
class AiCodeApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void healthReturnsUnifiedResponse() throws Exception {
        mockMvc.perform(get("/api/health/").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("ok"))
                .andExpect(jsonPath("$.message").value("ok"));
    }

    @Test
    void businessExceptionPreservesCodeAndMessage() throws Exception {
        mockMvc.perform(get("/api/test/business-error").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.message").value("应用名称不能为空"));
    }

    @Test
    void unexpectedExceptionHidesInternalDetails() throws Exception {
        mockMvc.perform(get("/api/test/runtime-error").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"code":50000,"data":null,"message":"系统错误"}
                        """));
    }

    @Test
    void corsPreflightAllowsFrontendWithCredentials() throws Exception {
        mockMvc.perform(options("/api/health/").contextPath("/api")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type"));
    }

    @Test
    void openApiDescribesUnifiedResponseWithExceptionAdviceEnabled() throws Exception {
        mockMvc.perform(get("/api/v3/api-docs/default").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/health/'].get.responses['200']").exists())
                .andExpect(jsonPath("$.components.schemas.BaseResponseString.properties.code.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.BaseResponseString.properties.data.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.BaseResponseString.properties.message.type").value("string"));
    }

    @Test
    void knife4jPageRemainsAccessible() throws Exception {
        mockMvc.perform(get("/api/doc.html").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("knife4j")));
    }

    /**
     * 异常测试接口仅在测试上下文注册，不会发布到实际应用。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ExceptionTestConfig {

        @Bean
        ExceptionTestController exceptionTestController() {
            return new ExceptionTestController();
        }
    }

    @RestController
    static class ExceptionTestController {

        @GetMapping("/test/business-error")
        public BaseResponse<String> businessError() {
            ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "应用名称不能为空");
            return ResultUtils.success("ok");
        }

        @GetMapping("/test/runtime-error")
        public BaseResponse<String> runtimeError() {
            throw new IllegalStateException("internal database connection details");
        }
    }
}
