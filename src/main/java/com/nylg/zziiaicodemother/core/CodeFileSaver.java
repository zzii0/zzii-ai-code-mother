package com.nylg.zziiaicodemother.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.nylg.zziiaicodemother.ai.model.HtmlCodeResult;
import com.nylg.zziiaicodemother.ai.model.MultiFileCodeResult;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

//代码文件写入类
public class CodeFileSaver {
    //文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 保存HTML代码
     *
     * @param htmlCodeResult HTML代码结果
     * @return 保存目录
     */
    //保存HTML代码
    public static File saveHtmlCodeResult(HtmlCodeResult htmlCodeResult) {
        String dirPath = buildDirPath(CodeGenTypeEnum.HTML.getValue());
        String content = htmlCodeResult.getHtmlCode();
        saveFile(dirPath, "index.html", content);
        return new File(dirPath);
    }

    /**
     * 保存多文件代码
     *
     * @param multiFileCodeResult 多文件代码结果
     * @return 保存目录
     */
    //保存多文件代码
    public static File saveMultiFileCodeResult(MultiFileCodeResult multiFileCodeResult) {
        String dirPath = buildDirPath(CodeGenTypeEnum.MULTI_FILE.getValue());
        saveFile(dirPath, "index.html", multiFileCodeResult.getHtmlCode());
        saveFile(dirPath, "style.css", multiFileCodeResult.getCssCode());
        saveFile(dirPath, "script.js", multiFileCodeResult.getJsCode());
        return new File(dirPath);
    }

    /**
     * 构建唯一目录路径：tmp/code_output/bizType_雪花ID
     *
     * @param bizType 业务类型
     * @return 目录路径
     */
    //构建唯一目录路径：tmp/code_output/bizType_雪花ID
    public static String buildDirPath(String bizType) {
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + bizType + "_" + IdUtil.getSnowflakeNextIdStr();
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
    public static void saveFile(String dirPath, String filename, String content) {
        String filePath = dirPath + File.separator + filename;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}
