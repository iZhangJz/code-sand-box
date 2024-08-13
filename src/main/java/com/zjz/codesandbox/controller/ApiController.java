package com.zjz.codesandbox.controller;

import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.sandbox.impl.JavaDockerCodeBox;
import com.zjz.codesandbox.sandbox.impl.JavaNativeCodeBox;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/java")
public class ApiController {

    @Resource
    private JavaNativeCodeBox javaNativeCodeBox;

    @Resource
    private JavaDockerCodeBox javaDockerCodeBox;

    /**
     * Java原生执行代码接口
     * @param executeRequest 执行请求体
     * @return 执行响应体
     */
    @PostMapping("native")
    public ExecuteResponse executeCode(@RequestBody ExecuteRequest executeRequest) {
        if (executeRequest == null) {
            return null;
        }
        return javaNativeCodeBox.executeCode(executeRequest);
    }

    /**
     * Java Docker执行代码接口
     * @param executeRequest 执行请求体
     * @return 执行响应体
     */
    @PostMapping("docker")
    public ExecuteResponse executeCodeWithDocker(@RequestBody ExecuteRequest executeRequest) {
        if (executeRequest == null) {
            return null;
        }
        return javaDockerCodeBox.executeCode(executeRequest);
    }
}
