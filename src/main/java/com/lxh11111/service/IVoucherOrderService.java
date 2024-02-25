package com.lxh11111.service;

import com.lxh11111.dto.Result;
import com.lxh11111.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void creatVoucherOrder(VoucherOrder voucherOrder);
}
