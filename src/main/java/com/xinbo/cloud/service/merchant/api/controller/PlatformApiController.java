package com.xinbo.cloud.service.merchant.api.controller;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xinbo.cloud.common.config.RocketMQConfig;
import com.xinbo.cloud.common.constant.RocketMQTopic;
import com.xinbo.cloud.common.domain.statistics.SportActiveUserStatistics;
import com.xinbo.cloud.common.dto.RocketMessage;
import com.xinbo.cloud.common.dto.common.UserBalanceOperationDto;
import com.xinbo.cloud.common.enums.UserStatusEnum;
import com.xinbo.cloud.common.enums.UserTypeEnum;
import com.xinbo.cloud.common.enums.MoneyChangeEnum;
import com.xinbo.cloud.common.enums.RocketMessageIdEnum;
import com.xinbo.cloud.common.config.ZookeeperConfig;
import com.xinbo.cloud.common.constant.ApiStatus;
import com.xinbo.cloud.common.constant.ZookeeperLockKey;
import com.xinbo.cloud.common.domain.common.GameAddress;
import com.xinbo.cloud.common.domain.common.Merchant;
import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.JwtUser;
import com.xinbo.cloud.common.dto.ResultFactory;
import com.xinbo.cloud.common.dto.common.UserInfoDto;
import com.xinbo.cloud.common.enums.PlatGameTypeEnum;
import com.xinbo.cloud.common.library.DesEncrypt;
import com.xinbo.cloud.common.library.DistributedLock;
import com.xinbo.cloud.common.service.library.rocketmq.RocketMQService;
import com.xinbo.cloud.common.service.merchant.api.PlatformApiService;
import com.xinbo.cloud.common.vo.common.UpdateUserInfoMoneyVo;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.common.vo.common.UserMoneyFlowVo;
import com.xinbo.cloud.common.vo.merchanta.api.PlatformApiRequestVo;
import com.xinbo.cloud.common.vo.merchanta.api.TransRecordRequestVo;
import com.xinbo.cloud.common.vo.merchanta.api.TranslateRequestVo;
import com.xinbo.cloud.service.merchant.api.service.JwtService;
import com.xinbo.cloud.service.merchant.api.service.UserService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * @author 汉斯
 * @date 2020/3/16 11:29
 * @desc 商户对接模块api接口
 */
@Slf4j
@RestController
@RequestMapping("platformApi")
public class PlatformApiController {
    @Autowired
    @Qualifier("platformApiServiceImpl")
    private PlatformApiService platformApiService;

    @Autowired
    @Qualifier("rocketMQServiceImpl")
    private RocketMQService rocketMQService;

    @Autowired
    @SuppressWarnings("all")
    private UserService userService;

    @Autowired
    @SuppressWarnings("all")
    private JwtService jwtService;

    @Value("${rocketmq.name-server}")
    private String nameServer;
    @Value("${rocketmq.producer-group}")
    private String producerGroup;
    @Value("${rocketmq.producer-timeout}")
    private int producerTimeout;

    @Value("${zookeeper.server-addr}")
    private String zookeeperServerAddr;

    @Value("${zookeeper.session-timeout}")
    private int zookeeperSessionTimeout;

    @ApiOperation(value = "获取游戏链接", notes = "")
    @PostMapping("playGame")
    public ActionResult playGame(@Valid @RequestBody PlatformApiRequestVo playGameVo) {
        int gameId = Integer.parseInt(playGameVo.getGameId());
        //Step 1: 验证游戏Id
        PlatGameTypeEnum platGameTypeEnum = PlatGameTypeEnum.valueOf(gameId);
        if (platGameTypeEnum == null){
            ResultFactory.error("游戏不存在");
        }
        //Step 2: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(playGameVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 3: 验证签名
        boolean isValidate = platformApiService.validateSign(playGameVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }

        //Step 4: 验证用户
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(playGameVo.getUsername()).dataNode(merchant.getDataNode()).build();
        ActionResult actionResult = userService.getUser(userInfoVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }
        UserInfoDto userInfoDto = new ObjectMapper().convertValue(actionResult.getData(), UserInfoDto.class);
        //Step 5: 返回游戏链接Url
        PlatGameTypeEnum platGameType = (gameId >= 1000 && gameId < 3000) ? PlatGameTypeEnum.Lottery : PlatGameTypeEnum.Sport;
        List<GameAddress> listGameAddress = platformApiService.getGameAddressList(platGameType.getCode());
        if (listGameAddress.isEmpty()) {
            return ResultFactory.error("游戏链接不存在");
        }
        int index = RandomUtil.randomInt(0, listGameAddress.size());
        GameAddress gameAddress = listGameAddress.get(index);
        String gameUrl = gameAddress.getGameUrl() + ((gameId != PlatGameTypeEnum.Lottery.getCode() && gameId != PlatGameTypeEnum.Sport.getCode()) ? "/lottery-bet/" + playGameVo.getGameId() : "");

        //Step 6.生成token并加入redis
        JwtUser jwtUser = JwtUser.builder().id(userInfoDto.getUserId()).username(userInfoDto.getUserName()).dataNode(merchant.getDataNode()).build();
        ActionResult jwtResult = jwtService.generateToken(jwtUser);
        if (jwtResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error("加密失败");
        }
        String token = jwtResult.getData().toString();
        //Step 7.生成最后的游戏链接
        gameUrl += "?token=" + token;
        return ResultFactory.success(gameUrl);
    }


    @ApiOperation(value = "创建用户", notes = "")
    @PostMapping("createAccount")
    public ActionResult createAccount(@Valid @RequestBody PlatformApiRequestVo createAccountVo) {

        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(createAccountVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
        boolean isValidate = platformApiService.validateSign(createAccountVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }
        //Step 3: 添加用户
        String ip = NetUtil.getLocalhostStr();
        UserInfo userinfo = UserInfo.builder().userName(createAccountVo.getUsername()).merchantCode(merchant.getMerchantCode())
                .dataNode(merchant.getDataNode()).regTime(new Date()).status(UserStatusEnum.Normal.getCode())
                .regIp(ip).loginIp(ip).money(0).frozen_money(0)
                .type(UserTypeEnum.Formal.getCode()).passWord(DesEncrypt.Encrypt("123456")).build();
        ActionResult actionResult = userService.addUser(userinfo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            log.info("添加用户失败，原因：{}", actionResult.getMsg());
        }
        UserInfoDto userInfoDto = new ObjectMapper().convertValue(actionResult.getData(), UserInfoDto.class);

        //Step 4：用户活跃统计初使化
        SportActiveUserStatistics sportActiveUserStatistics = SportActiveUserStatistics.builder().merchantCode(merchant.getMerchantCode())
                .merchantName(merchant.getMerchantName()).userName(createAccountVo.getUsername()).day(new Date()).regIp(ip).regTime(new Date())
                .dataNode(merchant.getDataNode()).build();
        setRocketSportActiveUserInto(sportActiveUserStatistics);

        return ResultFactory.success(userInfoDto.get_userId());
    }

    private void setRocketSportActiveUserInto(SportActiveUserStatistics sportActiveUserStatistics) {
        UserInfo userinfo = UserInfo.builder().build();
        RocketMQConfig rocketMQConfig = RocketMQConfig.builder()
                .nameServer(nameServer)
                .producerGroup(producerGroup)
                .producerTimeout(producerTimeout)
                .producerTopic(RocketMQTopic.STATISTICS_TOPIC)
                .build();
        //构建队列消息
        RocketMessage message = RocketMessage.<String>builder().messageBody(JSONUtil.toJsonStr(sportActiveUserStatistics)).messageId(RocketMessageIdEnum.Sport_ActiveUserInto.getCode()).build();
        //发送事务消息
        SendResult sendResult = rocketMQService.setRocketMQConfig(rocketMQConfig).send(message);
    }

    @ApiOperation(value = "余额转入", notes = "")
    @PostMapping("translateIn")
    public ActionResult translateIn(@Valid @RequestBody TranslateRequestVo translateRequestVo) {
        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(translateRequestVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
//        boolean isValidate = platformApiService.validateSign(translateRequestVo, merchant.getMerchantKey());
//        if (!isValidate) {
//            return ResultFactory.error("验证签名失败");
//        }

        //Step 3: 验证用户
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(translateRequestVo.getUsername()).dataNode(merchant.getDataNode()).build();
        ActionResult actionResult = userService.getUser(userInfoVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }
        UserInfoDto userInfoDto = new ObjectMapper().convertValue(actionResult.getData(), UserInfoDto.class);
        //Step 4: 验证金额
        if (translateRequestVo.getAmount() <= 0) {
            return ResultFactory.error("金额有误");
        }
        boolean isMatch = ReUtil.isMatch("(^-?[1-9](\\d+)?(\\.\\d{1,2})?$)|(^-?0$)|(^-?\\d\\.\\d{1,2}$)", Double.toString(translateRequestVo.getAmount()));
        if (!isMatch) {
            return ResultFactory.error("金额有误");
        }
        UpdateUserInfoMoneyVo userInfoMoneyVo = UpdateUserInfoMoneyVo.builder().userName(translateRequestVo.getUsername())
                .dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial()).money(translateRequestVo.getAmount())
                .moneyChangeEnum(MoneyChangeEnum.MoneyIn.getCode()).build();

        UserBalanceOperationDto balanceOperationDto = UserBalanceOperationDto.builder().userId(userInfoDto.getUserId()).userName(userInfoDto.getUserName())
                .merchantCode(merchant.getMerchantCode()).dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial())
                .operationMoney(translateRequestVo.getAmount()).operationType(MoneyChangeEnum.MoneyIn.getCode()).remark(MoneyChangeEnum.MoneyIn.getMsg()).build();

        //Step 5: 开始转入
        String lockName = String.format(ZookeeperLockKey.USER_LOCK, "moneyIn");
        ZookeeperConfig config = ZookeeperConfig.builder()
                .serverAddr(zookeeperServerAddr)
                .sessionTimeout(zookeeperSessionTimeout)
                .lockName(lockName)
                .build();

        DistributedLock lock = null;
        try {
            lock = new DistributedLock(config);
            //启动锁
            lock.lock();
            //本地事务Transaction
            Function<UpdateUserInfoMoneyVo, Boolean> transactionFunc = m -> {
                Boolean result = false;
                try {
                    ActionResult translateInResult = userService.translateOut(m);
                    result = translateInResult.getCode() == 0;
                } catch (Exception ex) {
                    log.error("余额转入失败", ex);
                }
                return result;
            };

            //构建队列参数
            RocketMQConfig rocketMQConfig = RocketMQConfig.builder()
                    .nameServer(nameServer)
                    .producerGroup(producerGroup)
                    .producerTimeout(producerTimeout)
                    .producerTopic(RocketMQTopic.STATISTICS_TOPIC)
                    .build();
            //构建队列消息
            RocketMessage message = RocketMessage.<String>builder().messageBody(JSONUtil.toJsonStr(balanceOperationDto)).messageId(MoneyChangeEnum.MoneyIn.getCode()).build();
            //发送事务消息
            SendResult sendResult = rocketMQService.setRocketMQConfig(rocketMQConfig).send(message, userInfoMoneyVo, transactionFunc);
            return sendResult.getSendStatus() == SendStatus.SEND_OK ? ResultFactory.success(sendResult) : ResultFactory.error();

        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }


    @ApiOperation(value = "余额转出", notes = "")
    @PostMapping("translateOut")
    public ActionResult translateOut(@Valid @RequestBody TranslateRequestVo translateRequestVo) {

        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(translateRequestVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
        boolean isValidate = platformApiService.validateSign(translateRequestVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }

        //Step 3: 验证用户
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(translateRequestVo.getUsername()).dataNode(merchant.getDataNode()).build();
        ActionResult actionResult = userService.getUser(userInfoVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }
        UserInfoDto userInfoDto = new ObjectMapper().convertValue(actionResult.getData(), UserInfoDto.class);

        //Step 4: 验证金额
        if (translateRequestVo.getAmount() <= 0) {
            return ResultFactory.error("金额有误");
        }
        boolean isMatch = ReUtil.isMatch("(^-?[1-9](\\d+)?(\\.\\d{1,2})?$)|(^-?0$)|(^-?\\d\\.\\d{1,2}$)", Double.toString(translateRequestVo.getAmount()));
        if (!isMatch) {
            return ResultFactory.error("金额有误");
        }

        UpdateUserInfoMoneyVo userInfoMoneyVo = UpdateUserInfoMoneyVo.builder().userName(translateRequestVo.getUsername())
                .dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial()).money(translateRequestVo.getAmount())
                .moneyChangeEnum(MoneyChangeEnum.MoneyOut.getCode()).build();

        UserBalanceOperationDto balanceOperationDto = UserBalanceOperationDto.builder().userId(userInfoDto.getUserId()).userName(userInfoDto.getUserName())
                .merchantCode(merchant.getMerchantCode()).dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial())
                .operationMoney(translateRequestVo.getAmount()).operationType(MoneyChangeEnum.MoneyOut.getCode()).remark(MoneyChangeEnum.MoneyOut.getMsg()).build();
        //Step 5: 开始转出
        String lockName = String.format(ZookeeperLockKey.USER_LOCK, "moneyOut");
        ZookeeperConfig config = ZookeeperConfig.builder()
                .serverAddr(zookeeperServerAddr)
                .sessionTimeout(zookeeperSessionTimeout)
                .lockName(lockName)
                .build();
        DistributedLock lock = null;
        try {
            lock = new DistributedLock(config);
            //启动锁
            lock.lock();

            //本地事务Transaction
            Function<UpdateUserInfoMoneyVo, Boolean> transactionFunc = m -> {
                Boolean result = false;
                try {
                    ActionResult translateOutResult = userService.translateOut(m);
                    result = translateOutResult.getCode() == 0;
                } catch (Exception ex) {
                    log.error("RocketMQ本地事务执行失败", ex);
                }
                return result;
            };

            //构建队列参数
            RocketMQConfig rocketMQConfig = RocketMQConfig.builder()
                    .nameServer(nameServer)
                    .producerGroup(producerGroup)
                    .producerTimeout(producerTimeout)
                    .producerTopic(RocketMQTopic.STATISTICS_TOPIC)
                    .build();
            //构建队列消息
            RocketMessage message = RocketMessage.<String>builder().messageBody(JSONUtil.toJsonStr(balanceOperationDto)).messageId(MoneyChangeEnum.MoneyOut.getCode()).build();
            //发送事务消息
            SendResult sendResult = rocketMQService.setRocketMQConfig(rocketMQConfig).send(message, userInfoMoneyVo, transactionFunc);
            return sendResult.getSendStatus() == SendStatus.SEND_OK ? ResultFactory.success(sendResult) : ResultFactory.error();
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    @ApiOperation(value = "查询余额", notes = "")
    @PostMapping("queryBalance")
    public ActionResult queryBalance(@Valid @RequestBody PlatformApiRequestVo queryBalanceVo) {

        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(queryBalanceVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
        boolean isValidate = platformApiService.validateSign(queryBalanceVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }

        //Step 4: 验证用户
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(queryBalanceVo.getUsername()).dataNode(merchant.getDataNode()).build();
        ActionResult actionResult = userService.getUser(userInfoVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }
        UserInfoDto userInfoDto = new ObjectMapper().convertValue(actionResult.getData(), UserInfoDto.class);
        return ResultFactory.success(userInfoDto.getMoney());
    }


    @ApiOperation(value = "查询订单状态", notes = "")
    @PostMapping("transRecord")
    public ActionResult transRecord(@Valid @RequestBody TransRecordRequestVo transRecordRequestVo) {

        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(transRecordRequestVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
        boolean isValidate = platformApiService.validateSign(transRecordRequestVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }

        //Step 3: 验证用户
        UserInfoVo userInfoVo = UserInfoVo.builder().userName(transRecordRequestVo.getUsername()).dataNode(merchant.getDataNode()).build();
        ActionResult actionResult = userService.getUser(userInfoVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }

        //Step 4: 验证订单
        UserMoneyFlowVo userMoneyFlowVo = UserMoneyFlowVo.builder().merchantCode(merchant.getMerchantCode()).dataNode(merchant.getDataNode())
                .merchantSerial(transRecordRequestVo.getMerchantSerial()).build();
        actionResult = userService.transRecord(userMoneyFlowVo);
        if (actionResult.getCode() != ApiStatus.SUCCESS) {
            return ResultFactory.error(actionResult.getMsg());
        }
        return ResultFactory.success();
    }


    @ApiOperation(value = "踢出游戏", notes = "")
    @PostMapping("loginOut")
    public ActionResult loginOut(@Valid @RequestBody PlatformApiRequestVo loginOutVo) {

        //Step 1: 验证渠道号
        Merchant merchant = platformApiService.getByMerchantCode(loginOutVo.getChannel());
        if (merchant == null) {
            return ResultFactory.error("渠道不存在");
        }

        //Step 2: 验证签名
        boolean isValidate = platformApiService.validateSign(loginOutVo, merchant.getMerchantKey());
        if (!isValidate) {
            return ResultFactory.error("验证签名失败");
        }

        ActionResult actionResult = userService.loginOut(null);
        return actionResult;
    }
}
