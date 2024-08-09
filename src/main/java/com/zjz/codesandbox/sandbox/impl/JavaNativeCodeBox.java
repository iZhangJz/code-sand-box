package com.zjz.codesandbox.sandbox.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.enums.CodeBoxExecuteEnum;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.model.process.ProcessMessage;
import com.zjz.codesandbox.sandbox.CodeBox;
import com.zjz.codesandbox.utils.ProcessUtils;
import com.zjz.codesandbox.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class JavaNativeCodeBox implements CodeBox {

    public static void main(String[] args) throws IOException {
        JavaNativeCodeBox javaNativeCodeBox = new JavaNativeCodeBox();
        List<String> list = new ArrayList<>();
        list.add("1 2");
        String code = ResourceUtil.readStr("testcode/MemoryErrorCode/Main.java",StandardCharsets.UTF_8);
        ExecuteRequest java = ExecuteRequest.builder()
                .code(code)
                .inputs(list)
                .language("java")
                .build();
        javaNativeCodeBox.executeCode(java);
    }

    /**
     * 删除文件
     * @param file 文件
     */
    private void deleteFile(File file){
        if (file.getParentFile() != null){
            String parentName = file.getParentFile().getName();
            boolean del = FileUtil.del(file.getParentFile());
            if (del){
                log.info("删除文件 {} 成功",parentName);
            }else {
                log.error("删除文件 {} 失败",parentName);
            }
        }
    }

    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        List<String> inputs = executeRequest.getInputs();
        String code = executeRequest.getCode();
        String language = executeRequest.getLanguage();

        // 校验用户代码是否保护敏感或危险代码
        if (VerifyUtils.verifyCodeSecurity(code)) {
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("用户代码存在敏感或危险代码")
                    .build();
        }

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
        String responseMessage = "";
        String responseStatus = "";
        String compileCommand = String.format(CmdConstant.JAVA_COMPILE_CMD, file.getAbsolutePath());

        try {
            Process exec = Runtime.getRuntime().exec(compileCommand);
            ProcessMessage compileMessage = ProcessUtils.runProcessAndMessage(exec, CmdConstant.COMPILE_OPERATION_NAME);

            if (compileMessage.getExitCode() == 0) {
                // 编译成功
                System.out.println(compileMessage.getSuccessMsg());
            } else {
                // 编译失败 封装返回消息
                deleteFile(file);
                responseStatus = CodeBoxExecuteEnum.COMPILE_FAILED.getValue();
                responseMessage = compileMessage.getErrorMsg();
                return ExecuteResponse.builder()
                        .status(responseStatus)
                        .message(responseMessage)
                        .build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Process Error", e);
        }

        // 5.执行 Java 程序
        if (ObjectUtil.isNull(inputs) || inputs.isEmpty()) {
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .message("测试用例为空")
                    .build();
        }
        List<String> outputs = new ArrayList<>();
        List<ExecuteInfo> executeInfos = new ArrayList<>();
        for (String input : inputs) {
            String runCommand = String.format(CmdConstant.JAVA_RUN_CMD,userCodePath,input);
            try {
                Process exec = Runtime.getRuntime().exec(runCommand);


                ProcessMessage runMessage = ProcessUtils.runProcessAndMessage(exec, CmdConstant.RUN_OPERATION_NAME,input);
                ExecuteInfo executeInfo = new ExecuteInfo();
                executeInfo.setTime(runMessage.getExecuteTime());
                executeInfo.setMemory(runMessage.getMemoryUsage());
                System.out.println("执行用时：" + runMessage.getExecuteTime() + "ms");
                System.out.println("执行内存：" + runMessage.getMemoryUsage() + "kB");
                if (runMessage.getExitCode() == 0) {
                    executeInfo.setMessage("success");
                    outputs.add(runMessage.getSuccessMsg());
                    System.out.println("执行结果：" + runMessage.getSuccessMsg());
                } else {
                    executeInfo.setMessage("failed");
                    outputs.add(runMessage.getErrorMsg());
                    System.out.println("执行结果：" + runMessage.getErrorMsg());
                }
            } catch (IOException e){
                throw new RuntimeException("Process Error");
            }
        }
        // 6.删除文件
        deleteFile(file);

        return ExecuteResponse.builder()
                .status(CodeBoxExecuteEnum.SUCCESS.getValue())
                .outputs(outputs)
                .executeInfos(executeInfos)
                .message(CodeBoxExecuteEnum.SUCCESS.getText())
                .build();
    }

    @Override
    public String getStatus() {
        return null;
    }
}
