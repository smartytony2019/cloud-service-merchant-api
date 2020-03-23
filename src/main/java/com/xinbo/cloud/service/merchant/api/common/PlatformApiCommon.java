package com.xinbo.cloud.service.merchant.api.common;

import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xinbo.cloud.common.utils.MapperUtil;
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
}
