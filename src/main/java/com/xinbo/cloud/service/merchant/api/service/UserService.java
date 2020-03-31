package com.xinbo.cloud.service.merchant.api.service;

import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.domain.common.UserMoneyFlow;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.common.UserInfoDto;
import com.xinbo.cloud.common.vo.common.UpdateUserInfoMoneyVo;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.common.vo.common.UserMoneyFlowVo;
import com.xinbo.cloud.service.merchant.api.service.fallback.UserServiceFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * @author 汉斯
 * @date 2020/3/16 16:36
 * @desc 商户对接接口内部方法
 */
@FeignClient(name = "cloud-service-user", fallback = UserServiceFallback.class)
public interface UserService {
    /**
     * 通过用户名称和数据接口获取用户信息
     *
     * @param vo 用户名：merchantKey_username
     * @return
     */
    @PostMapping("/gw-user/userInfo/getUserInfoByUserName")
    ActionResult getUser(@RequestBody UserInfoVo vo);

    /**
     * 添加用户
     *
     * @param userInfoVo
     * @return
     */
    @PostMapping("/gw-user/userInfo/insertUserInfo")
    ActionResult addUser(UserInfoVo userInfoVo);

    /**
     * 余额转入转出
     *
     * @param userInfoMoneyVo
     * @return
     */
    @PostMapping("/gw-user/userInfo/updateUserInfoMoney")
    ActionResult translate(@RequestBody UpdateUserInfoMoneyVo userInfoMoneyVo);


    /**
     * 查询订单状态
     *
     * @param vo
     * @return
     */
    @PostMapping("/gw-user/userInfo/getTransferIsSuccess")
    ActionResult transRecord(@RequestBody UserMoneyFlowVo vo);

    /**
     * 用户登录
     *
     * @param userInfoVo
     * @return
     */
    @PostMapping("/gw-user/userInfo/administratorKicked")
    ActionResult loginOut(@RequestBody UserInfoVo userInfoVo);

}
