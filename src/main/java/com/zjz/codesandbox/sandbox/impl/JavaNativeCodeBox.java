package com.zjz.codesandbox.sandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.ExecuteRequest;
import com.zjz.codesandbox.model.ExecuteResponse;
import com.zjz.codesandbox.sandbox.CodeBox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JavaNativeCodeBox implements CodeBox {

    public static void main(String[] args) {
        JavaNativeCodeBox javaNativeCodeBox = new JavaNativeCodeBox();
        ExecuteRequest java = ExecuteRequest.builder()
                .code("public class Main {\n" +
                        "    public static void main(String[] args) {\n" +
                        "        System.ou.println(\"Hello World\");\n" +
                        "    }\n" +
                        "}")
                .inputs(new ArrayList<>())
                .language("java")
                .build();
        javaNativeCodeBox.executeCode(java);
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        List<String> inputs = executeRequest.getInputs();
        String code = executeRequest.getCode();
        String language = executeRequest.getLanguage();

        // 1.获取项目路径
        String projectPath = System.getProperty("user.dir");
        String globalCodePath
                = projectPath + File.separator + FileConstant.GLOBAL_CODE_DIR_NAME + File.separator  + language;
        if (!FileUtil.exist(globalCodePath)) {
            // 创建目录
            FileUtil.mkdir(globalCodePath);
        }
        // 2.为用户代码生成一个唯一的目录
        Snowflake snowflake = IdUtil.getSnowflake();
        String userCodePath = globalCodePath + File.separator + snowflake.nextId();
        String codeFilePath = userCodePath +File.separator + FileConstant.JAVA_FILE_NAME;
        // 3.将用户代码写入文件
        File file = FileUtil.writeString(code, codeFilePath, StandardCharsets.UTF_8);

        // 4.编译 Java 代码
        String compileCommand = String.format(CmdConstant.JAVA_COMPILE_CMD,file.getAbsolutePath());
        try {
            Process exec = Runtime.getRuntime().exec(compileCommand);
            int exit = exec.waitFor(); // 等待编译完成
            if (exit == 0) {
                log.info("编译成功");
            }else {
                // 获取控制台输出的错误信息
                InputStreamReader inputStreamReader
                        = new InputStreamReader(exec.getErrorStream(), FileConstant.ENCODING_GBK);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                // 逐行读取
                StringBuilder stringBuilder = new StringBuilder();
                String compileOutputLine;
                while((compileOutputLine = bufferedReader.readLine() )!= null) {
                    stringBuilder.append(compileOutputLine).append(System.lineSeparator());
                }
                log.error("编译失败： {} 退出码：{}",stringBuilder,exit);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    @Override
    public String getStatus() {
        return null;
    }
}
