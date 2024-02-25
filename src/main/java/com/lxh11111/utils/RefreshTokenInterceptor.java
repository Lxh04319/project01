package com.lxh11111.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.lxh11111.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取token
        String token=request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            return true;
        }
        //token获取redis中的用户
        String key=RedisConstants.LOGIN_USER_KEY+token;
        Map<Object,Object> userMap=stringRedisTemplate.opsForHash().entries(key);
        //判断用户是否存在
        if(userMap.isEmpty()){
            return true;
        }
        //存在，保存到线程池
        UserDTO userDTO=BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        UserHolder.saveUser(userDTO);
        //刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
