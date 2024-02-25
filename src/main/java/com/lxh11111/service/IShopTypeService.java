package com.lxh11111.service;

import com.lxh11111.dto.Result;
import com.lxh11111.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeLists();
}
