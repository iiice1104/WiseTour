package com.hmdp.controller;


import com.hmdp.annotation.RateLimit;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    @RateLimit(key = "seckill_limit",rate = 500,interval = 1,message = "抢购太火爆了，请稍后再试")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckkillVoucher(voucherId);
    }
}
