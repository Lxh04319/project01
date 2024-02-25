package com.lxh11111.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lxh11111.dto.Result;
import com.lxh11111.entity.ShopType;
import com.lxh11111.mapper.ShopTypeMapper;
import com.lxh11111.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.lxh11111.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeLists() {
        //redis查询
        String shopType=stringRedisTemplate.opsForValue().get("shopTye");
        //判断是否存在
        if(StrUtil.isNotBlank(shopType)){
            //存在，返回信息
            List<ShopType> shopTypes=JSONUtil.toList(shopType,ShopType.class);
            return Result.ok(shopTypes);
        }
        //不存在，查询数据库
        List<ShopType> shopTypes=query().orderByAsc("sort").list();
        //不存在，返回错误
        if(shopTypes==null){
            return Result.fail("分类不存在");
        }
        //存入redis
        stringRedisTemplate.opsForValue().set("shopType",JSONUtil.toJsonStr(shopTypes) ,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}
