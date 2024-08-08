package com.zjz.codesandbox.model.execute;

import lombok.Data;

/**
 * 测试用例执行信息
 */
@Data
public class ExecuteInfo {
    /**
     * 执行信息
     */
    private String message;

    /**
     * 执行花费时间 ms
     */
    private Long time;

    /**
     * 执行使用内存大小 kb
     */
    private Long memory;
}
