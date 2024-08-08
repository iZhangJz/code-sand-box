package com.zjz.codesandbox.model.enums;



import org.springframework.util.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum CodeBoxStatusEnum {

    RUNNING("运行中", "running"),
    ERROR("错误", "error"),
    CRASH("宕机", "crash");

    private final String text;

    private final String value;

    CodeBoxStatusEnum(String text, String value) {
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
    public static CodeBoxStatusEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (CodeBoxStatusEnum anEnum : CodeBoxStatusEnum.values()) {
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
