package com.nylg.zziiaicodemother.service;

import com.nylg.zziiaicodemother.model.dto.app.AppVersionCompareRequest;
import com.nylg.zziiaicodemother.model.dto.app.AppVersionRollbackRequest;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.CodeGenTypeEnum;
import com.nylg.zziiaicodemother.model.vo.AppVersionCompareVO;
import com.nylg.zziiaicodemother.model.vo.AppVersionVO;

import java.util.List;

/**
 * 应用版本管理服务
 */
public interface AppVersionService {

    /**
     * 归档当前代码目录为历史版本（目录为空则跳过）
     *
     * @param appId           应用 id
     * @param codeGenTypeEnum 代码生成类型
     * @return 归档后的 versionKey，未归档返回 null
     */
    String archiveCurrentVersion(Long appId, CodeGenTypeEnum codeGenTypeEnum);

    /**
     * 列出应用版本（含当前版本）
     */
    List<AppVersionVO> listAppVersions(Long appId, User loginUser);

    /**
     * 对比两个版本的指定文件
     */
    AppVersionCompareVO compareAppVersion(AppVersionCompareRequest request, User loginUser);

    /**
     * 回退到指定历史版本
     */
    Boolean rollbackAppVersion(AppVersionRollbackRequest request, User loginUser);
}
