package com.lxh11111.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.lxh11111.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //方法一：Java对象序列化成json并存储在string类型的key中，可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit unit){
        this.stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //方法二：Java对象序列化成json并存储在string类型的key中，可以设置逻辑过期时间，处理缓存击穿
    public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
        //设置过期
        RedisData redisData=new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //存入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //方法三：指定key查询缓存，并反序列化json为指定对象，利用缓存空值解决缓存穿透问题
    public  <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        //redis查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在，返回商铺信息
            return JSONUtil.toBean(json,type);
        }
        //判断命中是否为空值
        if (json != null) {
            return null;
        }
        //不存在，id查询数据库，此时不知道查询数据库的哪个表，所以要传入一段逻辑(Function)
        R r = dbFallback.apply(id);
        //不存在，返回错误信息
        if (r == null) {
            //缓存穿透，空值也需写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，存入redis
        this.set(key,r,time,unit);
        //返回
        return r;
    }

    //方法四：指定key查询缓存，并反序列化json为指定对象，利用逻辑过期解决缓存击穿
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    //逻辑过期解决缓存击穿
    public <R,ID> R queryWithLogic(
            String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time,TimeUnit unit) {
        String key = keyPrefix + id;
        //redis查询
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //存在，返回商铺信息
            return null;
        }
        //命中,json转为对象
        RedisData redisData=JSONUtil.toBean(json, RedisData.class);
        R r=JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime=redisData.getExpireTime();
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期,返回shop
            return r;
        }
        //过期，缓存重建
        //1.获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock=tryLock(lockKey);
        //2.判断
        if(isLock){
            //3.成功，开启独立线程，缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                //重建缓存
                try {
                    //查询数据库
                    R r1=dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //4.返回过期信息
        //返回
        return r;
    }
    //互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
