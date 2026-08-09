package com.wisetour.service;

import com.wisetour.dto.Result;
import com.wisetour.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckkillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}

