package com.zjz.codesandbox.config;

import com.zjz.codesandbox.sandbox.impl.cpp.CppDockerCodeBox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean
    public CppDockerCodeBox cppDockerCodeBox(){
        return new CppDockerCodeBox();
    }
}

