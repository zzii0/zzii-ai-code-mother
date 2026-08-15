package com.nylg.zziiaicodemother.core.saver;

import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.ai.model.result.MultiFileCodeResult;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;

/**
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult>{
    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveCodeFile(MultiFileCodeResult result, String dirPath) {
        saveFile(dirPath, "index.html", result.getHtmlCode());
        saveFile(dirPath, "style.css", result.getCssCode());
        saveFile(dirPath, "script.js", result.getJsCode());
    }

    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        // 多文件模式必须三文件齐全，避免只落盘 index.html
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML代码内容不能为空");
        }
        if (StrUtil.isBlank(result.getCssCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "CSS代码内容不能为空");
        }
        if (StrUtil.isBlank(result.getJsCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JavaScript代码内容不能为空");
        }
    }
}
