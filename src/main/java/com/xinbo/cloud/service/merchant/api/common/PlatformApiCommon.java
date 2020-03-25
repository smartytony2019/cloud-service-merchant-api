package com.xinbo.cloud.service.merchant.api.common;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xinbo.cloud.common.constant.ApiStatus;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.common.MerchantDto;
import com.xinbo.cloud.common.utils.MapperUtil;
import com.xinbo.cloud.service.merchant.api.service.MerchantService;
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
     * @param obj
     * @param merchantKey
     * @return
     */
    public static boolean validateSign(Object obj, String merchantKey) {
        Map map = MapperUtil.to(obj, Map.class);
        String strSign = MapUtil.getStr(map, "sign");
        map.remove("sign");
        String str = StringUtils.join(map.values(), "");
        str += merchantKey;
        String strMd5 = DigestUtil.md5Hex(str);
        return strMd5 == strSign;
    }

    /**
     * 验证商户是否存在，并返回商户信息
     * @param merchantService
     * @param merchantCode
     * @return
     */
    public static MerchantDto validateMerchant(MerchantService merchantService,String merchantCode) {
        //Step 1: 验证渠道号
        ActionResult result = merchantService.getByMerchantCode(merchantCode);
        if (result.getCode() == ApiStatus.FALLBACK) {
            throw new RuntimeException(result.getMsg());
        }
        if (result.getCode() != ApiStatus.SUCCESS) {
            throw new RuntimeException("渠道不存在");
        }
        MerchantDto dto = Convert.convert(MerchantDto.class,result.getData());
        return  dto;
    }
}
