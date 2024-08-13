package com.zjz.codesandbox.sandbox;


import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;



public interface CodeBox {
    /**
     * 执行代码接口
     */
    ExecuteResponse executeCode(ExecuteRequest executeRequest);

    /**
     * 查看代码沙箱状态
     */
    String getStatus();
}
