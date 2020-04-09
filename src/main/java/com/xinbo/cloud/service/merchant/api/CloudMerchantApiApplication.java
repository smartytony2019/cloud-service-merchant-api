package com.xinbo.cloud.service.merchant.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import springfox.documentation.swagger2.annotations.EnableSwagger2;
import tk.mybatis.spring.annotation.MapperScan;

/**
 * @author 汉斯
 * @date 2020/3/23 12:28
 * @desc 商户接口服务入口
 */
@EnableSwagger2
@EnableDiscoveryClient
@MapperScan(basePackages = "com.xinbo.cloud.common.mapper")
@SpringBootApplication(scanBasePackages = "com.xinbo.cloud", exclude= {DataSourceAutoConfiguration.class})
public class CloudMerchantApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudMerchantApiApplication.class, args);
    }
}
