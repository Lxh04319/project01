package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher=seckillVoucherService.getById(voucherId);
        //秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀未开始");
        }
        //秒杀是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已结束");
        }
        //库存是否充足
        if(voucher.getStock()<1){
            return Result.fail("库存不足");
        }

        Long userId=UserHolder.getUser().getId();
        SimpleRedisLock lock=new SimpleRedisLock("order:"+userId,stringRedisTemplate);
        boolean isLock=lock.trylock(1200);
        if(!isLock){
            return Result.fail("不允许重复下单!");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //一人一单
        Long userId=UserHolder.getUser().getId();
        //查询订单
        int count=query().eq("user_id",userId).eq("voucher_id", voucherId).count();
        //判断是否购买过
        if(count>0){
            return Result.fail("已经购买过一次！");
        }
        //减库存
        boolean success=seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!success){
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder=new VoucherOrder();
        //订单id
        long orderId=redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id
        //Long userId= UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        //代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        //返回订单id
        return Result.ok(voucherOrder);
    }
}
