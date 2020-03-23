package com.xinbo.cloud.service.merchant.api.service;

import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.JwtUser;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author 汉斯
 * @date 2020/3/17 17:03
 * @desc file desc
 */
@FeignClient(name = "cloud-service-oauth")
public interface JwtService {


    @PostMapping("/gw-oauth/oauth/generateToken")
    ActionResult generateToken(JwtUser jwtParams);

    @PostMapping("/gw-oauth/oauth/parseToken")
    ActionResult parseToken(String token);

}
