package com.zjz.codesandbox.sandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.constant.FileConstant;
import com.zjz.codesandbox.model.dto.CompileMessage;
import com.zjz.codesandbox.model.dto.PreExecMessage;
import com.zjz.codesandbox.model.dto.PreRunMessage;
import com.zjz.codesandbox.model.dto.RunCodeMessage;
import com.zjz.codesandbox.model.enums.CodeBoxExecuteEnum;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.model.process.ProcessMessage;
import com.zjz.codesandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 执行代码模板抽象类
 */
@Slf4j
public abstract class ExecuteCodeTemplate {

    /**
     * 执行代码
     */
    public final ExecuteResponse exec(ExecuteRequest executeRequest){
        List<String>inputs = executeRequest.getInputs();
        String code = executeRequest.getCode();
        String language = executeRequest.getLanguage();
        PreExecMessage preExecMessage = preExec(executeRequest);
        if (!preExecMessage.getSuccess()){
            return ExecuteResponse.builder()
                    .message(preExecMessage.getReason())
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .build();
        }
        File file = saveCodeFile(code, language);

        CompileMessage compileMessage = compileCode(file);
        if (!compileMessage.getSuccess()){
            // 编译失败 直接返回
            return ExecuteResponse.builder()
                    .status(CodeBoxExecuteEnum.COMPILE_FAILED.getValue())
                    .message(compileMessage.getReason())
                    .build();
        }

        String userCodePath = file.getParentFile().getAbsolutePath();
        PreRunMessage preRunMessage = preRunCode(userCodePath);
        if (!preRunMessage.getSuccess()){
            // 前置运行准备失败
            return ExecuteResponse.builder()
                    .message(preRunMessage.getReason())
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .build();
        }

        RunCodeMessage runCodeMessage = runCode(inputs, userCodePath,preRunMessage.getData());
        if (!runCodeMessage.getSuccess()){
            // 执行失败
            return ExecuteResponse.builder()
                    .message(runCodeMessage.getExceptionMessage())
                    .status(CodeBoxExecuteEnum.FAILED.getValue())
                    .build();
        }
        deleteFile(file);
        return ExecuteResponse.builder()
                .status(CodeBoxExecuteEnum.SUCCESS.getValue())
                .outputs(runCodeMessage.getOutputs())
                .executeInfos(runCodeMessage.getExecuteInfos())
                .message(CodeBoxExecuteEnum.SUCCESS.getText())
                .build();
    }

    /**
     * 保存代码文件
     * @param code 用户代码
     * @param language 编程语言
     * @return 代码文件
     */
    private File saveCodeFile(String code,String language){
        // 获取项目路径
        String projectPath = System.getProperty("user.dir");
        String globalCodePath
                = projectPath + File.separator + FileConstant.GLOBAL_CODE_DIR_NAME + File.separator  + language;
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        // 为用户代码生成一个唯一的目录
        Snowflake snowflake = IdUtil.getSnowflake();
        String userCodePath = globalCodePath + File.separator + snowflake.nextId();
        String codeFilePath = userCodePath +File.separator + FileConstant.JAVA_FILE_NAME;
        // 将用户代码写入文件
        return FileUtil.writeString(code, codeFilePath, StandardCharsets.UTF_8);
    }

    /**
     * 编译代码文件
     * @param file 代码文件
     * @return 执行响应
     */
    private CompileMessage compileCode(File file){
        String responseMessage = "";
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
                responseMessage = compileMessage.getErrorMsg();
                return CompileMessage.builder()
                        .success(false)
                        .reason(responseMessage)
                        .build();
            }
        } catch (IOException e) {
            throw new RuntimeException("Process Error", e);
        }
        return CompileMessage.builder()
                .success(true)
                .build();
    }

    /**
     * 删除文件
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

    /**
     * 执行前准备工作
     */
    public abstract PreExecMessage preExec(ExecuteRequest executeRequest);

    /**
     * 运行代码前准备
     */
    public abstract PreRunMessage preRunCode(String userCodePath);

    /**
     * 运行代码
     */
    public abstract RunCodeMessage runCode(List<String> inputs, String userCodePath,Object data);
}
