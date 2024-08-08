package com.zjz.codesandbox.model.enums;


import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum CodeBoxExecuteEnum {

    SUCCESS("执行成功", "success"),
    COMPILE_FAILED("编译失败", "compile_failed"),
    FAILED("执行失败", "failed");

    private final String text;

    private final String value;

    CodeBoxExecuteEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 获取值列表
     *
     */
    public static List<String> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     */
    public static CodeBoxExecuteEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (CodeBoxExecuteEnum anEnum : CodeBoxExecuteEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }

    public String getText() {
        return text;
    }
}
