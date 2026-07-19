package com.nylg.zziiaicodemother.aop;

import com.nylg.zziiaicodemother.annotation.AuthCheck;
import com.nylg.zziiaicodemother.exception.BusinessException;
import com.nylg.zziiaicodemother.exception.ErrorCode;
import com.nylg.zziiaicodemother.model.entity.User;
import com.nylg.zziiaicodemother.model.enums.UserRoleEnum;
import com.nylg.zziiaicodemother.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private HttpServletRequest request;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        //不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        //以下需要权限
        String userRole = loginUser.getUserRole();
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        //没有权限，拒绝
        if (userRoleEnum==null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        //需要管理员权限，但是当前用户不是管理员
        if (mustRoleEnum.equals(UserRoleEnum.ADMIN) && !userRoleEnum.equals(UserRoleEnum.ADMIN)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        //通过权限校验，放行
        return joinPoint.proceed();
    }
}
