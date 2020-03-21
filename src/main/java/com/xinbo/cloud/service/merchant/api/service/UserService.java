package com.xinbo.cloud.service.merchant.api.service;

import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.vo.common.UpdateUserInfoMoneyVo;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.common.vo.common.UserMoneyFlowVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * @author 汉斯
 * @date 2020/3/16 16:36
 * @desc 商户对接接口内部方法
 */
@FeignClient(name = "cloud-service-user")
public interface UserService {
    /**
     * 通过用户名称和数据接口获取用户信息
     * @param vo   用户名：merchantKey_username
     * @return
     */
    @PostMapping("/userInfo/getUserInfoByUserName")
    ActionResult getUser(UserInfoVo vo);

    /**
     * 添加用户
     * @param userinfo
     * @return
     */
    @PostMapping("/userInfo/insertUserInfo")
    ActionResult addUser(UserInfo userinfo);

    /**
     * 余额转入
     * @param userInfoMoneyVo
     * @return
     */
    @PostMapping("/userInfo/updateUserInfoMoney")
    ActionResult translateIn(UpdateUserInfoMoneyVo userInfoMoneyVo);

    /**
     * 余额转出
     * @param userInfoMoneyVo
     * @return
     */
    @PostMapping("/userInfo/updateUserInfoMoney")
    ActionResult translateOut(UpdateUserInfoMoneyVo userInfoMoneyVo);

    /**
     * 查询订单状态
     * @param userMoneyFlowVo
     * @return
     */
    @PostMapping("/userInfo/getTranslateIsSuccess")
    ActionResult transRecord(UserMoneyFlowVo userMoneyFlowVo);

    /**
     * 用户登录
     * @param userinfo
     * @return
     */
    @PostMapping("/userInfo/stringGet")
    ActionResult loginOut(UserInfo userinfo);

}
