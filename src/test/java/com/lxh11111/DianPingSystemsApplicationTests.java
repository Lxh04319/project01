package com.lxh11111;

import com.lxh11111.service.impl.ShopServiceImpl;
import com.lxh11111.utils.CacheClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
public class DianPingSystemsApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

//    //@Test
//    //public void testSaveShop() throws InterruptedException{
//        Shop shop =shopService.getById(1L);
//        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+1L,shop,10L, TimeUnit.SECONDS);
//    }

//    //加载位置信息
//    @Test
//    public void loadShopData(){
//        //查询店铺信息
//        List<Shop> list=shopService.list();
//        //店铺按类型分组
//        Map<Long,List<Shop>> map=list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
//        //写入redis
//        for(Map.Entry<Long,List<Shop>> entry:map.entrySet()){
//            //获取类型
//            Long typeId=entry.getKey();
//            String key="shop:geo:"+typeId;
//            //获取该类型的所有店铺集合
//            List<Shop> value=entry.getValue();
//            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>(value.size());
//            for (Shop shop : value) {
//                //不采用stringRedisTemplate挨个点发请求添加
//                locations.add(new RedisGeoCommands.GeoLocation<>(
//                        shop.getId().toString(),new Point(shop.getX(),shop.getY())
//                ));
//            }
//            stringRedisTemplate.opsForGeo().add(key,locations);
//        }
//
//    }
}
