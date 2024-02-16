package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    //查询店铺信息
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop=queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        //Shop shop=queryWithMutex(id);

        //逻辑过期解决缓存击穿
        Shop shop=queryWithLogic(id);
        if(shop==null){
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    //逻辑过期解决缓存击穿
    private Shop queryWithLogic(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }

        //不存在，id查询数据库
        Shop shop = getById(id);

        //存在，存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        //返回
        return shop;
    }

    //缓存穿透
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回商铺信息
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中是否为空值
        if (shopJson != null) {
            return null;
        }
        //不存在，id查询数据库
        Shop shop = getById(id);
        //不存在，返回错误信息
        if (shop == null) {
            //缓存穿透，空值也需写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //存在，存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        //返回
        return shop;
    }

    //互斥锁解决缓存击穿
    public Shop queryWithMutex(Long id){
        String key=CACHE_SHOP_KEY+id;
        //redis查询
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //存在，返回商铺信息
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //判断命中是否为空值
        if(shopJson!=null){
            return null;
        }
        //缓存重建
        //1.获取互斥锁
        String lockKey=LOCK_SHOP_KEY+id;
        Shop shop= null;
        try {
            boolean isLock=tryLock(lockKey);
            //2.判断是否获取成功
            if(!isLock){
                //3.失败，休眠后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //不存在，id查询数据库
            shop = getById(id);
            //不存在，返回错误信息
            if(shop==null){
                //缓存穿透，空值也需写入redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //存在，存入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }
        //返回
        return shop;
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

    //提前写入缓存redis--逻辑过期解决缓存击穿
    public void saveShop2Redis(Long id,Long expireSeconds){
        //查询店铺数据
        Shop shop=getById(id);
        //封装过期时间
        RedisData redisData=new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    //更新店铺信息
    public Result update(Shop shop) {
        Long id = shop.getId();
        updateById(shop);
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
