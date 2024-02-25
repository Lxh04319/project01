package com.lxh11111.service.impl;

import com.lxh11111.entity.SeckillVoucher;
import com.lxh11111.mapper.SeckillVoucherMapper;
import com.lxh11111.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
