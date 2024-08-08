package com.zjz.codesandbox.sandbox;


import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;

import java.io.IOException;

public interface CodeBox {
    /**
     * 执行代码接口
     */
    ExecuteResponse executeCode(ExecuteRequest executeRequest) throws IOException;

    /**
     * 查看代码沙箱状态
     */
    String getStatus();
}
