package com.lxh11111.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX="lock:";
    private static final String ID_PREFIX= UUID.randomUUID().toString()+"-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean trylock(long timeoutSec) {
        //获取线程标识
        String threadId=ID_PREFIX+Thread.currentThread().getId();
        //获取锁
        Boolean success=stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX+name,threadId,timeoutSec, TimeUnit.SECONDS);
        //防自动拆箱产生空指针
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock(){
        //调用lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX+Thread.currentThread().getId());
    }
    //redis简单释放锁
//    @Override
//    public void unlock() {
//        //获取线程标识
//        String threadId=ID_PREFIX+Thread.currentThread().getId();
//        //获取锁标识
//        String keyId=stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
//        //判断一致
//        if(threadId.equals(keyId)) {
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//    }
}
