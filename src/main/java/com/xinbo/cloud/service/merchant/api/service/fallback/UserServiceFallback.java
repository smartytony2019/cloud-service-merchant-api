package com.xinbo.cloud.service.merchant.api.service.fallback;

import com.xinbo.cloud.common.constant.FallbackMessage;
import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.ResultFactory;
import com.xinbo.cloud.common.vo.common.UpdateUserInfoMoneyVo;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.common.vo.common.UserMoneyFlowVo;
import com.xinbo.cloud.service.merchant.api.service.UserService;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;

/**
 * @author 熊二
 * @date 2020/3/24 20:56
 * @desc 熔断器
 */

@Component
public class UserServiceFallback implements UserService {

    @Override
    public ActionResult getUser(UserInfoVo vo) {
        return ResultFactory.fallback(MessageFormat.format(FallbackMessage.MSG_FORMAT, UserService.class.getSimpleName()));
    }

    @Override
    public ActionResult addUser(UserInfo userinfo) {
        return ResultFactory.fallback(MessageFormat.format(FallbackMessage.MSG_FORMAT, UserService.class.getSimpleName()));
    }

    @Override
    public ActionResult translate(UpdateUserInfoMoneyVo userInfoMoneyVo) {
        return ResultFactory.fallback(MessageFormat.format(FallbackMessage.MSG_FORMAT, UserService.class.getSimpleName()));
    }

    @Override
    public ActionResult transRecord(UserMoneyFlowVo userMoneyFlowVo) {
        return ResultFactory.fallback(MessageFormat.format(FallbackMessage.MSG_FORMAT, UserService.class.getSimpleName()));
    }

    @Override
    public ActionResult loginOut(UserInfo userinfo) {
        return ResultFactory.fallback(MessageFormat.format(FallbackMessage.MSG_FORMAT, UserService.class.getSimpleName()));
    }
}
