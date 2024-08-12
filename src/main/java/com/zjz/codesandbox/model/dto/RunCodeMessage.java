package com.zjz.codesandbox.model.dto;

import com.zjz.codesandbox.model.execute.ExecuteInfo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @Description: 运行代码后响应
 */
@Data
@Builder
public class RunCodeMessage {

    /**
     * 运行是否成功
     */
    private Boolean success;

    /**
     * 异常信息
     */
    private String exceptionMessage;

    /**
     * 运行输出结果
     */
    private List<String> outputs;

    /**
     * 测试用例执行结果
     */
    private List<ExecuteInfo> executeInfos;
}
