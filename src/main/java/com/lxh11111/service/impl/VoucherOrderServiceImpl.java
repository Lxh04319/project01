package com.lxh11111.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.lxh11111.dto.Result;
import com.lxh11111.entity.VoucherOrder;
import com.lxh11111.mapper.VoucherOrderMapper;
import com.lxh11111.service.ISeckillVoucherService;
import com.lxh11111.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lxh11111.utils.RedisIdWorker;
import com.lxh11111.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @Resource
    private RedissonClient redissonClient;

//    private BlockingQueue<VoucherOrder> orderTasks=new ArrayBlockingQueue<>(1024*1024);
//    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
//    //lua脚本配置
//    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
//    static {
//        SECKILL_SCRIPT=new DefaultRedisScript<>();
//        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
//        SECKILL_SCRIPT.setResultType(Long.class);
//    }
//
//    //提前获取 不然子线程无法获取父线程
//    private IVoucherOrderService proxy;
//    //类初始化后立刻执行
//    @PostConstruct
//    private void init(){
//        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
//    }
//    private class VoucherOrderHandler implements Runnable{
//        @Override
//        public void run(){
//            while(true){
//                try{
//                    //获取队列中的订单信息
//                    VoucherOrder voucherOrder=orderTasks.take();
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                }catch(Exception e){
//                    log.error("订单异常");
//                }
//            }
//        }
//    }
//
//    private void handleVoucherOrder(VoucherOrder voucherOrder) {
//        //获取用户--不通过主线程池
//        Long userId=voucherOrder.getUserId();
//        //创建锁对象
//        RLock lock=redissonClient.getLock("lock:order:"+userId);
//        //获取锁
//        boolean isLock=lock.tryLock();
//        //判断锁是否获取成功
//        if(!isLock){
//            return ;
//        }
//        try {
//            //获取代理对象 事务
//            proxy.creatVoucherOrder(voucherOrder);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR= Executors.newSingleThreadExecutor();
    //lua脚本配置
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT=new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //提前获取 不然子线程无法获取父线程
    private IVoucherOrderService proxy;
    //类初始化后立刻执行
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable{
        String queueName="stream.orders";
        @Override
        public void run(){
            while(true){
                try{
                    //获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list=stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //获取是否成功
                    if(list==null||list.isEmpty()){
                        //获取失败--没有消息，下一步循环
                        continue;
                    }
                    //解析订单
                    MapRecord<String,Object,Object> record=list.get(0);
                    Map<Object,Object> values=record.getValue();
                    VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(values,new VoucherOrder(),true);
                    //获取成功--处理，下单
                    handleVoucherOrder(voucherOrder);
                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch(Exception e){
                    log.error("订单异常");
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while(true){
                try{
                    //获取pending-list中的订单信息
                    List<MapRecord<String, Object, Object>> list=stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //获取是否成功
                    if(list==null||list.isEmpty()){
                        //获取失败--没有消息，下一步循环
                        break;
                    }
                    //解析订单
                    MapRecord<String,Object,Object> record=list.get(0);
                    Map<Object,Object> values=record.getValue();
                    VoucherOrder voucherOrder= BeanUtil.fillBeanWithMap(values,new VoucherOrder(),true);
                    //获取成功--处理，下单
                    handleVoucherOrder(voucherOrder);
                    //ack确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                }catch(Exception e){
                    log.error("处理pending-list异常");
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //获取用户--不通过主线程池
        Long userId=voucherOrder.getUserId();
        //创建锁对象
        RLock lock=redissonClient.getLock("lock:order:"+userId);
        //获取锁
        boolean isLock=lock.tryLock();
        //判断锁是否获取成功
        if(!isLock){
            return ;
        }
        try {
            //获取代理对象 事务
            proxy.creatVoucherOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    @Override
    public Result seckillVoucher(Long voucherId){
        Long userId=UserHolder.getUser().getId();
        long orderId=redisIdWorker.nextId("order");
        //lua脚本
        Long result=stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),userId.toString(),String.valueOf(orderId)
        );
        //判断结果为0
        int r=result.intValue();
        //不为0--没资格购买
        if(r!=0){
            return Result.fail(r==1?"库存不足":"已经购买过一次");
        }

        //获取代理对象
        proxy=(IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }

    @Transactional
    public void creatVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        Long userId=voucherOrder.getUserId();
        //查询订单
        int count=query().eq("user_id",userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //判断是否购买过
        if(count>0){
            //return Result.fail("已经购买过一次！");
            log.error("用户已经购买过一次");
        }
        //减库存
        boolean success=seckillVoucherService.update()
                .setSql("stock=stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock",0)
                .update();
        if(!success){
            //return Result.fail("库存不足");
            log.error("库存不足");
        }
        //保存
        save(voucherOrder);
    }
}

//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher voucher=seckillVoucherService.getById(voucherId);
//        //秒杀是否开始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀未开始");
//        }
//        //秒杀是否结束
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束");
//        }
//        //库存是否充足
//        if(voucher.getStock()<1){
//            return Result.fail("库存不足");
//        }
//
//        Long userId=UserHolder.getUser().getId();
//        //创建锁对象
//        //SimpleRedisLock lock=new SimpleRedisLock("order:"+userId,stringRedisTemplate);
//        RLock lock=redissonClient.getLock("lock:order:"+userId);
//        //获取锁
//        boolean isLock=lock.tryLock();
//        //判断锁是否获取成功
//        if(!isLock){
//            return Result.fail("不允许重复下单!");
//        }
//        try {
//            //获取代理对象 事务
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.creatVoucherOrder(voucherId);
//        }finally {
//            //释放锁
//            lock.unlock();
//        }
//
//    }

//    @Transactional
//    public Result creatVoucherOrder(Long voucherId) {
//        //一人一单
//        Long userId=UserHolder.getUser().getId();
//        //查询订单
//        int count=query().eq("user_id",userId).eq("voucher_id", voucherId).count();
//        //判断是否购买过
//        if(count>0){
//            return Result.fail("已经购买过一次！");
//        }
//        //减库存
//        boolean success=seckillVoucherService.update()
//                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId)
//                .gt("stock",0)
//                .update();
//        if(!success){
//            return Result.fail("库存不足");
//        }
//        //创建订单
//        VoucherOrder voucherOrder=new VoucherOrder();
//        //订单id
//        long orderId=redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //用户id
//        //Long userId= UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//        //代金券id
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//        //返回订单id
//        return Result.ok(voucherOrder);
//    }
//}
