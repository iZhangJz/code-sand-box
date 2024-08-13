package com.zjz.codesandbox.sandbox.impl;

import cn.hutool.core.util.ObjectUtil;
import com.zjz.codesandbox.constant.CmdConstant;
import com.zjz.codesandbox.model.dto.PreExecMessage;
import com.zjz.codesandbox.model.dto.PreRunMessage;
import com.zjz.codesandbox.model.dto.RunCodeMessage;
import com.zjz.codesandbox.model.execute.ExecuteInfo;
import com.zjz.codesandbox.model.execute.ExecuteRequest;
import com.zjz.codesandbox.model.execute.ExecuteResponse;
import com.zjz.codesandbox.model.process.ProcessMessage;
import com.zjz.codesandbox.sandbox.CodeBox;
import com.zjz.codesandbox.sandbox.ExecuteCodeTemplate;
import com.zjz.codesandbox.utils.ProcessUtils;
import com.zjz.codesandbox.utils.VerifyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Java 原生代码沙箱
 */
@Service
@Slf4j
public class JavaNativeCodeBox extends ExecuteCodeTemplate implements CodeBox {

//    public static void main(String[] args) throws IOException {
//        JavaNativeCodeBox javaNativeCodeBox = new JavaNativeCodeBox();
//        List<String> list = new ArrayList<>();
//        list.add("1 2");
//        list.add("2 2");
//        list.add("2 9");
//        String code = ResourceUtil.readStr("testcode/Main.java", StandardCharsets.UTF_8);
//        ExecuteRequest java = ExecuteRequest.builder()
//                .code(code)
//                .inputs(list)
//                .language("java")
//                .build();
//        javaNativeCodeBox.executeCode(java);
//    }

    @Override
    public PreExecMessage preExec(ExecuteRequest executeRequest) {
        String code = executeRequest.getCode();
        // 校验用户代码是否保护敏感或危险代码
        if (VerifyUtils.verifyCodeSecurity(code)){
            return PreExecMessage.builder()
                    .success(false)
                    .reason("Dangerous or sensitive code exists")
                    .build();
        }
        return PreExecMessage.builder()
                .success(true)
                .build();
    }

    @Override
    public PreRunMessage preRunCode(String userCodePath) {
        log.info("Preparation is completed before execution");
        return PreRunMessage.builder()
                .success(true)
                .build();
    }

    @Override
    public RunCodeMessage runCode(List<String> inputs,String userCodePath,Object data) {
        if (ObjectUtil.isNull(inputs) || inputs.isEmpty()) {
            return RunCodeMessage.builder()
                    .success(false)
                    .exceptionMessage("The test case is empty")
                    .build();
        }
        List<String> outputs = new ArrayList<>();
        List<ExecuteInfo> executeInfos = new ArrayList<>();
        for (String input : inputs) {
            String runCommand = String.format(CmdConstant.JAVA_RUN_CMD,userCodePath,input);
            try {
                Process exec = Runtime.getRuntime().exec(runCommand);

                ProcessMessage runMessage
                        = ProcessUtils.runProcessAndMessage(exec, CmdConstant.RUN_OPERATION_NAME,input,preMemory);
                ExecuteInfo executeInfo = new ExecuteInfo();
                executeInfo.setTime(runMessage.getExecuteTime());
                executeInfo.setMemory(runMessage.getMemoryUsage());
                log.info("执行用时：" + runMessage.getExecuteTime() + "ms");
                log.info("执行内存：" + runMessage.getMemoryUsage() + "KB");
                if (runMessage.getExitCode() == 0) {
                    executeInfo.setMessage("success");
                    String payload = runMessage.getSuccessMsg();
                    payload = payload.replaceAll("\\R", "");
                    outputs.add(payload);
//                    System.out.println("执行结果：" + runMessage.getSuccessMsg());
                } else {
                    executeInfo.setMessage("failed");
                    String payload = runMessage.getErrorMsg();
                    payload = payload.replaceAll("\\R", "");
                    outputs.add(payload);
//                    System.out.println("执行结果：" + runMessage.getErrorMsg());
                }
                executeInfos.add(executeInfo);
            } catch (IOException e){
                throw new RuntimeException("Process Error");
            }
        }
        return RunCodeMessage.builder()
                .success(true)
                .outputs(outputs)
                .executeInfos(executeInfos)
                .build();
    }

    /**
     * 执行代码
     * @param executeRequest 执行请求
     * @return 执行响应
     */
    @Override
    public ExecuteResponse executeCode(ExecuteRequest executeRequest) {
        return exec(executeRequest);
    }

    @Override
    public String getStatus() {
        return null;
    }
}
