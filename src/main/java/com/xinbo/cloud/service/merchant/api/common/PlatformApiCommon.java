package com.xinbo.cloud.service.merchant.api.common;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.EnumUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.xinbo.cloud.common.enums.PlatGameTypeEnum;
import com.xinbo.cloud.common.utils.MapperUtil;
import org.apache.commons.lang3.StringUtils;
import java.util.List;
import java.util.Map;

/**
 * @author 汉斯
 * @date 2020/3/17 9:50
 * @desc 商户对外接口通用方法
 */
public class PlatformApiCommon {
    public static boolean validateSign(Object obj, String merchantKey) {
        Map map = MapperUtil.to(obj, Map.class);
        String strSign = MapUtil.getStr(map, "sign");
        map.remove("sign");
        String str = StringUtils.join(map.values(), "");
        str += merchantKey;
        String strMd5 = DigestUtil.md5Hex(str);
        return strMd5 == strSign;
    }

    public static boolean validateGameId(int gameId) {
        List<Object> listValues = EnumUtil.getFieldValues(PlatGameTypeEnum.class, "code");
        boolean isValidate = listValues.contains(gameId);
        return isValidate;
    }
}
