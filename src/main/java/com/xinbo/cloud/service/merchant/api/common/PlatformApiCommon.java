package com.xinbo.cloud.service.merchant.api.common;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xinbo.cloud.common.constant.ApiStatus;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.ResultFactory;
import com.xinbo.cloud.common.dto.common.MerchantDto;
import com.xinbo.cloud.common.dto.common.UserInfoDto;
import com.xinbo.cloud.common.utils.MapperUtil;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.service.merchant.api.service.MerchantService;
import com.xinbo.cloud.service.merchant.api.service.UserService;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * @author 汉斯
 * @date 2020/3/23 13:40
 * @desc file desc
 */
public class PlatformApiCommon {
    /**
     * 验证签名
     *
     * @param obj
     * @param merchantKey
     * @return
     */
    public static void validateSign(Object obj, String merchantKey) {
        Map map = MapperUtil.to(obj, Map.class);
        String strSign = MapUtil.getStr(map, "sign");
        map.remove("sign");
        String str = StringUtils.join(map.values(), "");
        str += merchantKey;
        String strMd5 = DigestUtil.md5Hex(str);
        if (!strSign.equals(strMd5)) {
            throw new RuntimeException("验证签名失败");
        }
    }

    /**
     * 验证转入转出金额
     * @param amount
     */
    public static void validateMoney(float amount) {
        if (amount <= 0) {
            throw new RuntimeException("金额要大于0");
        }
        boolean isMatch = ReUtil.isMatch("(^-?[1-9](\\d+)?(\\.\\d{1,2})?$)|(^-?0$)|(^-?\\d\\.\\d{1,2}$)", Double.toString(amount));
        if (!isMatch) {
            throw new RuntimeException("金额有误");
        }
    }

    /**
     * 验证商户是否存在，并返回商户信息
     *
     * @param merchantService
     * @param merchantCode
     * @return
     */
    public static MerchantDto validateMerchant(MerchantService merchantService, String merchantCode) {
        //Step 1: 验证渠道号
        ActionResult result = merchantService.getByMerchantCode(merchantCode);
        if (result.getCode() == ApiStatus.FALLBACK) {
            throw new RuntimeException(result.getMsg());
        }
        if (result.getCode() != ApiStatus.SUCCESS) {
            throw new RuntimeException("渠道不存在");
        }
        MerchantDto dto = Convert.convert(MerchantDto.class, result.getData());
        return dto;
    }


    public static UserInfoDto getUserInfo(UserService userService, String username, int dataNode) {
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(username).dataNode(dataNode).build();
        ActionResult result = userService.getUser(userInfoVo);
        if (result.getCode() == ApiStatus.FALLBACK) {
            throw new RuntimeException(result.getMsg());
        }
        if (result.getCode() != ApiStatus.SUCCESS) {
            throw new RuntimeException("用户不存在");
        }
        UserInfoDto dto = Convert.convert(UserInfoDto.class, result.getData());
        return dto;
    }
}
