package com.nylg.zziiaicodemother.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.nylg.zziiaicodemother.constant.AppConstant;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模板方法模式
 * */
public abstract class CodeFileSaverTemplate<T> {
    //文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模板方法：保存代码的标准流程
     *
     * @param result 代码结果对象
     * @return 保存的目录
     */
    public final File saveCode(T result, Long appId) {
        //验证输入
        validateInput(result);
        //构建唯一目录
        String dirPath = buildDirPath(appId);
        //保存代码文件（具体实现由子类完成）
        saveCodeFile(result, dirPath);
        //返回文件对象
        return new File(dirPath);
    }

    /**
     * 验证输入
     *
     * @param result 代码结果对象
     */
    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     *
     * @return 目录路径
     */
    //构建唯一目录路径：tmp/code_output/bizType_雪花ID
    protected String buildDirPath(Long appId) {
        if (appId == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用 id 不能为空");
        }
        String bizType = getCodeType().getValue();
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + bizType + "_" + appId;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    /**
     * 保存单个文件
     *
     * @param dirPath  目录路径
     * @param filename 文件名
     * @param content  文件内容
     */
    //保存单个文件
    public final void saveFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取代码类型（具体实现由子类完成）
     *
     * @return 代码类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存代码文件（具体实现由子类完成）
     *
     * @param result 代码结果对象
     * @param dirPath  目录路径
     */
    protected abstract void saveCodeFile(T result, String dirPath);

}
