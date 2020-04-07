package com.xinbo.cloud.service.merchant.api.common;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.domain.common.UserMoneyFlow;
import com.xinbo.cloud.common.dto.common.MerchantDto;
import com.xinbo.cloud.common.dto.common.UserInfoDto;
import com.xinbo.cloud.common.service.api.MerchantServiceApi;
import com.xinbo.cloud.common.service.api.UserServiceApi;
import com.xinbo.cloud.common.utils.MapperUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import com.xinbo.cloud.common.enums.TransferStatusEnum;

import java.text.MessageFormat;
import java.util.Map;

/**
 * @author 汉斯
 * @date 2020/3/23 13:40
 * @desc file desc
 */
@Slf4j
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
        String str = StringUtils.join(MapUtil.sort(map).values(), "");
        str += merchantKey;
        String strMd5 = DigestUtil.md5Hex(str).toLowerCase();
        if (!strSign.equals(strMd5)) {
            log.debug(MessageFormat.format("验证签名失败,签名字符串：{0},签名结果：{1},第三方签名串:{2}", str, strMd5, strSign));
            throw new RuntimeException("验证签名失败");
        }
    }

    /**
     * 验证转入转出金额
     *
     * @param strAmount
     */
    public static void validateMoney(String strAmount) {
        try {
            float amount = Float.parseFloat(strAmount);
            if (amount <= 0) {
                throw new RuntimeException("金额要大于0");
            }
            boolean isMatch = ReUtil.isMatch("(^-?[1-9](\\d+)?(\\.\\d{1,2})?$)|(^-?0$)|(^-?\\d\\.\\d{1,2}$)", Double.toString(amount));
            if (!isMatch) {
                throw new RuntimeException("金额有误");
            }
        } catch (Exception ex) {
            throw new RuntimeException("金额有误");
        }
    }

    /**
     * @param userServiceApi
     * @param merchantSerial
     * @param merchantCode
     * @param dataNode
     */
    public static TransferStatusEnum orderIsExist(UserServiceApi userServiceApi, String merchantSerial, String merchantCode, int dataNode) {
        UserMoneyFlow userMoneyFlow = UserMoneyFlow.builder().merchantCode(merchantCode).dataNode(dataNode)
                .merchantSerial(merchantSerial).build();
        TransferStatusEnum transferStatusEnum = userServiceApi.getTransferStatus(userMoneyFlow);
        return transferStatusEnum;
    }

    /**
     * @param userServiceApi
     * @param merchantSerial
     * @param merchantCode
     * @param dataNode
     */
    public static void validateSerial(UserServiceApi userServiceApi, String merchantSerial, String merchantCode, int dataNode) {
        TransferStatusEnum transferStatusEnum = orderIsExist(userServiceApi, merchantSerial, merchantCode, dataNode);
        if (transferStatusEnum == TransferStatusEnum.Success)
            throw new RuntimeException("订单已存在");
    }

    /**
     * 验证商户是否存在，并返回商户信息
     *
     * @param merchantServiceApi
     * @param merchantCode
     * @return
     */
    public static MerchantDto validateMerchant(MerchantServiceApi merchantServiceApi, String merchantCode) {
        MerchantDto dto = merchantServiceApi.getByMerchantCode(merchantCode);
        if (dto == null)
            throw new RuntimeException("渠道不存在");
        return dto;
    }


    public static UserInfoDto getUserInfo(UserServiceApi userServiceApi, String username, int dataNode) {
        UserInfo userInfo = UserInfo.builder().userName(username).dataNode(dataNode).build();
        UserInfoDto dto = userServiceApi.getUserInfoByUserName(userInfo);
        if (dto == null) {
            throw new RuntimeException("用户不存在");
        }
        return dto;
    }
}
