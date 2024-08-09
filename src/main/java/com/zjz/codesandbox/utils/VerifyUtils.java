package com.zjz.codesandbox.utils;

import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import lombok.extern.slf4j.Slf4j;
/**
 * 校验工具类
 */
@Slf4j
public class VerifyUtils {

    /**
     * 敏感词黑名单字典树
     */
    private static final WordTree WORD_TREE = new WordTree();
    static {
        WORD_TREE.addWords("File","Files","exec");
    }

    public static boolean verifyCodeSecurity(String code) {
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            log.warn("敏感词校验不通过,匹配到的敏感词为:{}", foundWord.getFoundWord());
            return true;
        }
        return false;
    }
}
