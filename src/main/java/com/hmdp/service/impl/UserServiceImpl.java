package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author lxh
 * @since 2024-2-14
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        //不符合，返回
        //符合，验证码
        String code= RandomUtil.randomNumbers(6);
        //保存验证码
        session.setAttribute("code",code);
        //发送验证码
        log.debug("发送验证码成功：{}",code);
        //返回
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone=loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误");
        }
        //校验验证码
        Object cachecode=session.getAttribute("code");
        String code=loginForm.getCode();
        if(cachecode==null||!cachecode.toString().equals(code)){
            return Result.fail("验证码错误");
        }
        //不一致报错
        //一致，查询用户
        User user=query().eq("phone",phone).one();
        //用户不存在，注册
        if(user==null){
            user=createUserWithPhone(phone);
        }
        //保存到session
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return null;
    }

    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
