package com.lxh11111.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lxh11111.dto.LoginFormDTO;
import com.lxh11111.dto.Result;
import com.lxh11111.dto.UserDTO;
import com.lxh11111.entity.User;
import com.lxh11111.mapper.UserMapper;
import com.lxh11111.service.IUserService;
import com.lxh11111.utils.RegexUtils;
import com.lxh11111.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.lxh11111.utils.RedisConstants.*;
import static com.lxh11111.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式不正确");
        }
        //不符合，返回
        //符合，验证码
        String code= RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
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
        String cachecode=stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
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
        //保存到redis
        String token=UUID.randomUUID().toString();
        UserDTO userDTO=BeanUtil.copyProperties(user,UserDTO.class);
        Map<String, Object> userMap=BeanUtil.beanToMap(userDTO ,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString()));
        String tokenKey=LOGIN_USER_KEY+token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //获取当前用户和当前时间日期
        Long userId= UserHolder.getUser().getId();
        LocalDateTime now=LocalDateTime.now();
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId;
        int dayOfMonth=now.getDayOfMonth();
        //redis签到--BitMap
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth,true);
        return Result.ok();
        //返回
    }

    @Override
    public Result signCount() {
        Long userId= UserHolder.getUser().getId();
        LocalDateTime now=LocalDateTime.now();
        String keySuffix=now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key=USER_SIGN_KEY+userId;
        int dayOfMonth=now.getDayOfMonth();
        //获取截至今天所有签到记录--返回十进制
        List<Long> result=stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0)
        );
        if(result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num=result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }
        int cnt=0;
        //循环遍历
        while (true){
            //与1做与运算--取最后一位数字的bit值
            if((num&1)==0){
                break;
            } else{
                cnt++;
            }
            //右移一位
            num>>>=1;
        }
        return Result.ok(cnt);
    }

    private User createUserWithPhone(String phone) {
        User user=new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
