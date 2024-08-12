package com.zjz.codesandbox.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @Description: 代码编译响应消息
 */
@Data
@Builder
public class CompileMessage {
    /**
     * 代码编译响应是否成功
     */
    private Boolean success;

    /**
     * 代码编译响应失败原因
     */
    private String reason;
}
