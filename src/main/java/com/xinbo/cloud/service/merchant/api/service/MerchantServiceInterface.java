package com.xinbo.cloud.service.merchant.api.service;

import com.xinbo.cloud.common.dto.ActionResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author 汉斯
 * @date 2020/3/23 13:17
 * @desc 商户信息接口
 */
@FeignClient(name = "cloud-service-merchant")
public interface MerchantServiceInterface {
    /**
     * 通过用户名称和数据接口获取用户信息
     * @param merchantCode
     * @return
     */
    @PostMapping("/merchant/getByMerchantCode")
    ActionResult getByMerchantCode(@RequestBody String merchantCode);

    @PostMapping("/merchant/getListByGameType")
    ActionResult getGameAddressList(@RequestBody int gameType);
}
