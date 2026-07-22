package com.nylg.zziiaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.nylg.zziiaicodemother.model.dto.app.AppQueryRequest;
import com.nylg.zziiaicodemother.model.entity.App;
import com.nylg.zziiaicodemother.model.vo.AppVO;

import java.util.List;

/**
 *  服务层。
 *
 * @author zzii
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装类。
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);
}
