package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.controller.ShopTypeController;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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
        stringRedisTemplate.opsForValue().set("shopType",JSONUtil.toJsonStr(shopTypes));
        return Result.ok(shopTypes);
    }
}
