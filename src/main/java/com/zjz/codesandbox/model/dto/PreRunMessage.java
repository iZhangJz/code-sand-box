package com.zjz.codesandbox.model.dto;

import lombok.Builder;
import lombok.Data;

/**
 * @Description: 前置运行消息
 */
@Data
@Builder
public class PreRunMessage {
    /**
     * 前置运行准备是否成功
     */
    private Boolean success;

    /**
     * 前置准备失败原因
     */
    private String reason;

}
