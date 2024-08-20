package com.zjz.codesandbox.controller;

import com.zjz.codesandbox.model.enums.CodeBoxExecuteEnum;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.sandbox.impl.cpp.CppDockerCodeBox;
import com.zjz.codesandbox.sandbox.impl.java.JavaDockerCodeBox;
import com.zjz.codesandbox.sandbox.impl.java.JavaNativeCodeBox;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.function.Function;

/**
 * ApiController
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";


    @Resource
    private JavaNativeCodeBox javaNativeCodeBox;

    @Resource
    private JavaDockerCodeBox javaDockerCodeBox;

    @Resource
    private CppDockerCodeBox cppDockerCodeBox;


    /**
     * 权限校验
     * @param httpServletRequest httpServletRequest
     * @return boolean
     */
    private boolean auth(HttpServletRequest httpServletRequest) {
        String auth = httpServletRequest.getHeader(AUTH_REQUEST_HEADER);
        return AUTH_REQUEST_SECRET.equals(auth);
    }

    /**
     * 统一执行逻辑的方法
     * @param executeRequest 执行请求体
     * @param request 请求对象
     * @param response 响应对象（可选）
     * @param executor 执行逻辑的函数接口
     * @return 执行响应体
     */
    private ExecuteResponse executeCode(ExecuteRequest executeRequest,
                                        HttpServletRequest request,
                                        HttpServletResponse response,
                                        Function<ExecuteRequest, ExecuteResponse> executor) {
        if (!auth(request)) {
            if (response != null) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("鉴权失败")
                    .build();
        }
        if (executeRequest == null) {
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("请求体为空")
                    .build();
        }
        return executor.apply(executeRequest);
    }

    /**
     *  Java 原生代码沙箱接口
     */
    @PostMapping("/native/java")
    public ExecuteResponse execJavaCodeWithNative(
            @RequestBody ExecuteRequest executeRequest, HttpServletRequest request, HttpServletResponse response) {
        return executeCode(executeRequest, request, response, javaNativeCodeBox::executeCode);
    }

    /**
     *  Java Docker 代码沙箱接口
     */
    @PostMapping("/docker/java")
    public ExecuteResponse execJavaCodeWithDocker(
            @RequestBody ExecuteRequest executeRequest, HttpServletRequest request) {
        return executeCode(executeRequest, request, null, javaDockerCodeBox::executeCode);
    }

    /**
     *  Cpp Docker 代码沙箱接口
     */
    @PostMapping("/docker/cpp")
    public ExecuteResponse execCppCodeWithDocker(
            @RequestBody ExecuteRequest executeRequest, HttpServletRequest request) {
        return executeCode(executeRequest, request, null, cppDockerCodeBox::executeCode);
    }

}
