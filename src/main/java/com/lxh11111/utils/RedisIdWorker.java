package com.lxh11111.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    //初始时间
    private static final long BEGIN_TIMESTAMP=1640995200L;
    //序列号长度
    private static final int COUNT_BITS=32;
    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间数
        LocalDateTime now=LocalDateTime.now();
        long nowSecond=now.toEpochSecond(ZoneOffset.UTC);
        long timestamp=nowSecond-BEGIN_TIMESTAMP;
        //生成序列号
        //1.获取当前日期，每天从头更新
        String date=now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.自增长
        Long count=stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        //拼接--位运算
        return timestamp<<COUNT_BITS | count;
    }
}

