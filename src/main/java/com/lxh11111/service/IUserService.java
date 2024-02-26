package com.lxh11111.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lxh11111.dto.LoginFormDTO;
import com.lxh11111.dto.Result;
import com.lxh11111.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
