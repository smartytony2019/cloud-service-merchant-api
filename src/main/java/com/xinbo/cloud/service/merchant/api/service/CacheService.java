package com.xinbo.cloud.service.merchant.api.service;

import com.xinbo.cloud.common.vo.library.cache.StringVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author 马仔
 * @date 2020/3/17 13:25
 * @desc redis 调用接口
 */


@FeignClient(value = "cloud-service-cache")
public interface CacheService {

    @PostMapping("/gw-cache/redis/stringSet")
    void stringSet(@RequestBody StringVo stringVo);

    @PostMapping("/gw-cache/redis/stringGet")
    String stringGet(@RequestBody String key);
}
