package com.zjz.codesandbox.constant;

/**
 * Docker 命令常量
 */
public interface DockerConstant {

    /**
     * Docker Java 环境镜像
     */
    String DOCKER_JAVA_IMAGE = "openjdk:8-alpine";

    /**
     * Docker gcc 编译运行环境镜像
     */
    String DOCKER_GCC_IMAGE = "gcc:latest";

    /**
     * g++ 编译后的文件名
     */
    String DOCKER_GCC_COMPILE_NAME = "main.exe";

    /**
     * 本地文件映射地址
     */
    String DOCKER_CODE_PATH  = "/app";

    /**
     * 测试用例输入临时文件
     */
    String DOCKER_TEST_CASE_INPUT = "/input.txt";

    /**
     * 运行环境的内存大小 256MB
     */
    Long DOCKER_JAVA_MEMORY = 256 * 1000 * 1000L;


}
