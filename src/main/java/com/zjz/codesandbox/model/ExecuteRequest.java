package com.zjz.codesandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 执行请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteRequest {

    /**
     * 测试用例数组
     */
    private List<String> inputs;

    /**
     * 代码
     */
    private String code;

    /**
     * 语言
     */
    private String language;

}
