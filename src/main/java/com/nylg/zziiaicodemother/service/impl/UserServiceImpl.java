package com.nylg.zziiaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.dto.user.UserLoginRequest;
import com.nylg.zziiaicodemother.model.dto.user.UserQueryRequest;
import com.nylg.zziiaicodemother.model.dto.user.UserRegisterRequest;
import com.nylg.zziiaicodemother.model.dto.user.UserUpdateMyRequest;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.mapper.UserMapper;
import com.nylg.zziiaicodemother.model.enums.UserRoleEnum;
import com.nylg.zziiaicodemother.model.vo.LoginUserVo;
import com.nylg.zziiaicodemother.model.vo.UserVO;
import com.nylg.zziiaicodemother.manager.AvatarFileManager;
import com.nylg.zziiaicodemother.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.nylg.zziiaicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 *  服务层实现。
 *
 * @author zzii
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int MAX_AVATAR_LENGTH = 1024;

    @Resource
    private AvatarFileManager avatarFileManager;

    /**
     * 用户注册
     * @param userRegisterRequest   用户注册对象
     * @return 注册的用户id
     */
    @Override
    public long userRegister(UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        //1. 参数校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号长度不能小于4");
        }
        if (userPassword.length() < 6 || checkPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码长度不能小于6");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        //2. 检查用户账号是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号已存在");
        }
        // 兼容历史逻辑删除数据仍占用唯一键的情况
        User deletedAccount = this.mapper.selectDeletedByUserAccount(userAccount);
        if (deletedAccount != null) {
            renameDeletedUserAccount(deletedAccount);
        }
        //3. 加密密码
        String encryptionPassword = getEncryptionPassword(userPassword);
        //4. 插入用户数据
        User user = User.builder()
                .userAccount(userAccount)
                .userPassword(encryptionPassword)
                .userName(userAccount + "001")
                .userRole(UserRoleEnum.USER.getValue())
                .build();
        boolean save = this.save(user);
        if (!save) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户注册失败");
        }
        return user.getId();
    }

    /**
     * 释放已删除的用户账号
     * @param user 用户
     */
    private void renameDeletedUserAccount(User user) {
        String deletedUserAccount = String.format("%s_del_%s", user.getUserAccount(), IdUtil.getSnowflakeNextIdStr());
        int updateRows = this.mapper.updateUserAccountById(user.getId(), deletedUserAccount);
        if (updateRows <= 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "释放已删除账号失败");
        }
    }


    /**
     * 获取加密后的密码
     * @param password  密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptionPassword(String password) {
        String SALT = "zzii";
        return DigestUtils.md5DigestAsHex((password + SALT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取登录用户信息
     * @param user 用户
     * @return 登录用户信息
     */
    @Override
    public LoginUserVo getLoginUserVo(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户信息为空");
        }
        LoginUserVo loginUserVo = new LoginUserVo();
        BeanUtil.copyProperties(user, loginUserVo);
        return loginUserVo;
    }

    /**
     * 用户登录
     * @param userLoginRequest 用户登录对象
     * @return 登录用户信息
     */
    @Override
    public LoginUserVo userLogin(UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户登录信息为空");
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        //检查用户是否存在
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        if (!user.getUserPassword().equals(getEncryptionPassword(userPassword))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }
        //记录用户登录状态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return getLoginUserVo(user);
    }

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User userLogin = (User) userObj;
        if (userLogin == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        Long id = userLogin.getId();
        userLogin = this.getById(id);
        if (userLogin == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户不存在");
        }
        return userLogin;
    }

    /**
     * 登出
     * @param request
     * @return
     */
    @Override
    public boolean loginOut(HttpServletRequest request) {
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    /**
     * 获取单个脱敏后的用户信息
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取多个脱敏后的用户信息
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
       if (CollUtil.isEmpty(userList)){
           return new ArrayList<>();
       }
       return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 更新用户信息
     * @param userUpdateMyRequest
     * @param request
     * @return
     */
    @Override
    public boolean updateMyUser(UserUpdateMyRequest userUpdateMyRequest, HttpServletRequest request) {
        if (userUpdateMyRequest == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        User loginUser = this.getLoginUser(request);
        if (loginUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        String userAvatar = userUpdateMyRequest.getUserAvatar();
        validateAvatarUrl(userAvatar);
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserName(userUpdateMyRequest.getUserName());
        updateUser.setUserAvatar(userAvatar);
        updateUser.setUserProfile(userUpdateMyRequest.getUserProfile());
        return this.updateById(updateUser);
    }

    @Override
    public String uploadAvatar(MultipartFile file, HttpServletRequest request) {
        User loginUser = this.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        // 仅上传文件并返回访问路径，由调用方决定写入哪个用户
        return avatarFileManager.uploadAvatar(file, loginUser.getId());
    }

    @Override
    public void validateAvatarUrl(String userAvatar) {
        if (StrUtil.isBlank(userAvatar)) {
            return;
        }
        if (userAvatar.startsWith("data:")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请通过上传接口设置头像");
        }
        if (userAvatar.length() > MAX_AVATAR_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像地址过长");
        }
    }

}
