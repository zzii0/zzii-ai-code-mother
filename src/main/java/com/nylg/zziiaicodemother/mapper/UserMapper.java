package com.nylg.zziiaicodemother.mapper;

import com.mybatisflex.core.BaseMapper;
import com.nylg.zziiaicodemother.model.entity.User;
import org.apache.ibatis.annotations.Param;

/**
 *  映射层。
 *
 * @author zzii
 */
public interface UserMapper extends BaseMapper<User> {
    /**
     * 查询已逻辑删除的同账号用户
     *
     * @param userAccount 账号
     * @return 用户
     */
    User selectDeletedByUserAccount(@Param("userAccount") String userAccount);

    /**
     * 改写已删除用户账号，释放唯一键占用
     *
     * @param id id
     * @param userAccount 新账号
     * @return 影响行数
     */
    int updateUserAccountById(@Param("id") Long id, @Param("userAccount") String userAccount);
}
