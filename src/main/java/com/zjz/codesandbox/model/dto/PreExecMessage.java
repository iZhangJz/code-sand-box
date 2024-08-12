package com.zjz.codesandbox.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @Description: 前置准备消息
 */
@Data
@Builder
public class PreExecMessage {
    /**
     * 前置准备是否成功
     */
    private Boolean success;

    /**
     * 前置准备失败原因
     */
    private String reason;
}
