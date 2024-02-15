package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        String key=CACHE_SHOP_KEY+id;
        //redis查询
        String shopJson=stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //存在，返回商铺信息
            Shop shop=JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        //不存在，id查询数据库
        Shop shop=getById(id);
        //不存在，返回错误信息
        if(shop==null){
            return Result.fail("店铺不存在！");
        }
        //存在，存入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop));
        //返回
        return Result.ok(shop);
    }
}
