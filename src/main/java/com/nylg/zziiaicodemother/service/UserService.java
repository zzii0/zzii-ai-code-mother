package com.nylg.zziiaicodemother.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.nylg.zziiaicodemother.model.dto.UserLoginRequest;
import com.nylg.zziiaicodemother.model.dto.UserQueryRequest;
import com.nylg.zziiaicodemother.model.dto.UserRegisterRequest;
import com.nylg.zziiaicodemother.model.dto.UserUpdateMyRequest;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.vo.LoginUserVo;
import com.nylg.zziiaicodemother.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 *  服务层。
 *
 * @author zzii
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册对象
     * @return 注册的用户id
     */
    long userRegister(UserRegisterRequest userRegisterRequest);

    /**
     * 获取加密后的密码
     * @param password  密码
     * @return          加密后的密码
     */
    String getEncryptionPassword(String password);

    /**
     * 获取登录用户信息
     * @param user 用户
     * @return 登录用户信息
     */
    LoginUserVo getLoginUserVo(User user);

    /**
     * 用户登录
     * @param userLoginRequest 用户登录对象
     * @return 登录用户信息
     */
    LoginUserVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request);

    /**
     * 获取当前登录用户信息
     * @param request request
     * @return 登录用户信息
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 登出
     * @param request request
     * @return 登出结果
     */
    boolean loginOut(HttpServletRequest request);

    /**
     * 获取脱敏后的用户信息
     * @param user 用户
     * @return 用户信息
     */
    public UserVO getUserVO(User user);

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 用户列表
     * @return 用户信息列表
     */
    public List<UserVO> getUserVOList(List<User> userList);
    /**
     * 根据查询条件构造数据查询参数
     * @param userQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 更新用户信息
     * @param userUpdateMyRequest 更新用户信息对象
     * @param request request
     * @return 更新结果
     */
    boolean updateMyUser(UserUpdateMyRequest userUpdateMyRequest, HttpServletRequest request);

    /**
     * 上传用户头像
     * @param file 头像文件
     * @param request request
     * @return 头像访问路径
     */
    String uploadAvatar(MultipartFile file, HttpServletRequest request);

    /**
     * 校验头像地址是否合法
     * @param userAvatar 头像地址
     */
    void validateAvatarUrl(String userAvatar);
}
