package com.zjz.codesandbox.controller;

import com.zjz.codesandbox.model.enums.CodeBoxExecuteEnum;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.sandbox.impl.JavaNativeCodeBox;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ApiController
 */
@RestController
@RequestMapping("/api/java")
public class ApiController {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";


    @Resource
    private JavaNativeCodeBox javaNativeCodeBox;

//    @Resource
//    private JavaDockerCodeBox javaDockerCodeBox;


    /**
     * 权限校验
     * @param httpServletRequest httpServletRequest
     * @return boolean
     */
    private boolean auth(HttpServletRequest httpServletRequest) {
        String auth = httpServletRequest.getHeader(AUTH_REQUEST_HEADER);
        if (auth == null) {
            return true;
        }
        return !AUTH_REQUEST_SECRET.equals(auth);
    }

    /**
     * Java原生执行代码接口
     * @param executeRequest 执行请求体
     * @return 执行响应体
     */
    @PostMapping("native")
    public ExecuteResponse executeCode(
            @RequestBody ExecuteRequest executeRequest, HttpServletRequest request,HttpServletResponse response) {
        if (auth(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
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
        return javaNativeCodeBox.executeCode(executeRequest);
    }

    /**
     * Java Docker执行代码接口
     * @param executeRequest 执行请求体
     * @return 执行响应体
     */
//    @PostMapping("docker")
//    public ExecuteResponse executeCodeWithDocker(
//             @RequestBody ExecuteRequest executeRequest, HttpServletRequest request) {
//        if (auth(request)) {
//            return ExecuteResponse.builder()
//                    .status(CodeBoxExecuteEnum.FAILED.getValue())
//                    .message("鉴权失败")
//                    .build();
//        }
//        if (executeRequest == null) {
//            return ExecuteResponse.builder()
//                    .status(CodeBoxExecuteEnum.FAILED.getValue())
//                    .message("请求体为空")
//                    .build();
//        }
//        return javaDockerCodeBox.executeCode(executeRequest);
//    }
}
