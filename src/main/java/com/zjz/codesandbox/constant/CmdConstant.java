package com.zjz.codesandbox.constant;

/**
 * 控制台命令常量
 */
public interface CmdConstant {

    /**
     * Java 编译命令
     */
    String JAVA_COMPILE_CMD = "javac -encoding utf-8 %s";

    /**
     * Java 运行命令
     */
    String JAVA_RUN_CMD = "java -Xmx256M -cp %s Main";

    /**
     * 编译
     */
    String COMPILE_OPERATION_NAME = "编译";

    /**
     * 运行
     */
    String RUN_OPERATION_NAME = "运行";

    /**
     *  Windows 查看进程状态 前缀
     */
    String WINDOWS_TASKLIST_CMD_PREFIX = "tasklist /FI \"PID eq ";

    /**
     * Windows 查看进程状态 后缀
     */
    String WINDOWS_TASKLIST_CMD_SUFFIX = "\" /FO CSV";

    /**
     * Linux 查看进程状态 前缀
     */
    String LINUX_TASKLIST_CMD_PREFIX = "ps -p ";

    /**
     * Linux 查看进程状态 后缀
     */
    String LINUX_TASKLIST_CMD_SUFFIX = " -o rss=";
}
