package com.zjz.codesandbox.model.process;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessMessage {

    /**
     * 错误码
     */
    private Integer exitCode;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 成功信息
     */
    private String successMsg;

    /**
     * 执行时间
     */
    private Long executeTime;

    /**
     * 内存使用情况
     */
    private Long memoryUsage;

}
