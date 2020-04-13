package com.xinbo.cloud.service.merchant.api.controller;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xinbo.cloud.common.config.RocketMQConfig;
import com.xinbo.cloud.common.constant.CacheConfig;
import com.xinbo.cloud.common.constant.RocketMQTopic;
import com.xinbo.cloud.common.domain.common.UserInfo;
import com.xinbo.cloud.common.dto.RocketMessage;
import com.xinbo.cloud.common.dto.common.GameAddressDto;
import com.xinbo.cloud.common.dto.common.MerchantDto;
import com.xinbo.cloud.common.dto.common.UserToken;
import com.xinbo.cloud.common.dto.statistics.SportActiveUserOperationDto;
import com.xinbo.cloud.common.dto.statistics.UserBalanceOperationDto;
import com.xinbo.cloud.common.enums.*;
import com.xinbo.cloud.common.config.ZookeeperConfig;
import com.xinbo.cloud.common.constant.ZookeeperLockKey;
import com.xinbo.cloud.common.dto.ActionResult;
import com.xinbo.cloud.common.dto.ResultFactory;
import com.xinbo.cloud.common.dto.common.UserInfoDto;
import com.xinbo.cloud.common.library.DesEncrypt;
import com.xinbo.cloud.common.library.DistributedLock;
import com.xinbo.cloud.common.library.rocketmq.RocketMQService;
import com.xinbo.cloud.common.service.api.*;
import com.xinbo.cloud.common.vo.common.UpdateUserInfoMoneyVo;
import com.xinbo.cloud.common.vo.common.UserInfoVo;
import com.xinbo.cloud.common.vo.library.cache.StringVo;
import com.xinbo.cloud.common.vo.merchanta.api.PlatformApiRequestVo;
import com.xinbo.cloud.common.vo.merchanta.api.TransRecordRequestVo;
import com.xinbo.cloud.common.vo.merchanta.api.TranslateRequestVo;
import com.xinbo.cloud.service.merchant.api.common.PlatformApiCommon;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
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
    @Qualifier("rocketMQServiceImpl")
    private RocketMQService rocketMQService;

    @Reference(version = "1.0.0", mock = "com.xinbo.cloud.common.service.mock.UserServiceMock")
    private UserServiceApi userServiceApi;

    @Reference(version = "1.0.0", mock = "com.xinbo.cloud.common.service.mock.MerchantServiceMock")
    private MerchantServiceApi merchantServiceApi;

    @Reference(version = "1.0.0", mock = "com.xinbo.cloud.common.service.mock.GameAddressServiceMock")
    private GameAddressServiceApi gameAddressServiceApi;


    @Reference(version = "1.0.0", mock = "com.xinbo.cloud.common.service.mock.RedisServiceMock")
    private RedisServiceApi redisServiceApi;

    @Reference(version = "1.0.0", mock = "com.xinbo.cloud.common.service.mock.OAuthServiceMock")
    private OAuthServiceApi oAuthServiceApi;


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

    @Autowired
    private HttpServletRequest request;

    @ApiOperation(value = "获取游戏链接", notes = "")
    @PostMapping("playGame")
    public ActionResult playGame(@Valid @RequestBody PlatformApiRequestVo playGameVo) {
        try {

            int gameId = Integer.parseInt(playGameVo.getGameId());
            //Step 1: 验证游戏Id
            PlatGameTypeEnum platGameTypeEnum = PlatGameTypeEnum.valueOf(gameId);
            if (platGameTypeEnum == null) {
                ResultFactory.error("游戏不存在");
            }
            //Step 2: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, playGameVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), playGameVo.getUsername());
            //Step 3: 验证签名
            PlatformApiCommon.validateSign(playGameVo, merchant.getMerchantKey());

            //Step 4: 验证用户
            UserInfoDto userInfoDto = PlatformApiCommon.getUserInfo(userServiceApi, username, merchant.getDataNode());

            //Step 5: 返回游戏链接Url
            PlatGameTypeEnum platGameType = (gameId >= 1000 && gameId < 3000) ? PlatGameTypeEnum.Lottery : PlatGameTypeEnum.Sport;
            List<GameAddressDto> listGameAddressDto = gameAddressServiceApi.getListByPlatGameType(platGameType.getCode());
            if (listGameAddressDto == null || listGameAddressDto.size() == 0) {
                return ResultFactory.error("游戏链接不存在");
            }
            int index = RandomUtil.randomInt(0, listGameAddressDto.size());
            GameAddressDto gameAddress = listGameAddressDto.get(index);
            String gameUrl = gameAddress.getGameUrl() + ((gameId != PlatGameTypeEnum.Lottery.getCode() && gameId != PlatGameTypeEnum.Sport.getCode()) ? "/lottery-bet/" + playGameVo.getGameId() : "");

            //Step 6.生成token并加入redis
            String token = UUID.randomUUID().toString();
            UserToken userToken = UserToken.builder().merchantCode(merchant.getMerchantCode()).token(token).time(new Date())
                    .userId(userInfoDto.getUserId()).dataNode(merchant.getDataNode()).userName(userInfoDto.getUserName()).build();

            String userTokenKey = MessageFormat.format(CacheConfig.USER_TOKEN_KEY, token);
            redisServiceApi.stringSet(StringVo.builder().key(userTokenKey).value(userToken).expire(CacheConfig.ONE_HOUR).build());

            //Step 7.生成最后的游戏链接
            gameUrl += "?token=" + token;
            return ResultFactory.success(gameUrl);
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }


    @ApiOperation(value = "创建用户", notes = "")
    @PostMapping("createAccount")
    public ActionResult createAccount(@Valid @RequestBody PlatformApiRequestVo createAccountVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, createAccountVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), createAccountVo.getUsername());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(createAccountVo, merchant.getMerchantKey());

            //Step 3: 添加用户
            String ip = NetUtil.getLocalhostStr();
            UserInfo userinfo = UserInfo.builder().userName(username).
                    merchantCode(merchant.getMerchantCode()).merchantName(merchant.getMerchantName())
                    .dataNode(merchant.getDataNode()).regIp(ip).loginIp(ip)
                    .type(UserTypeEnum.Formal.getCode()).passWord(DesEncrypt.Encrypt("123456")).build();
            UserInfoDto userInfoDto = userServiceApi.insertUserInfo(userinfo);
            if (userInfoDto == null) {
                return ResultFactory.error("创建用户失败");
            }
            if (StrUtil.isBlank(userInfoDto.get_userId())) {
                return ResultFactory.error("创建用户失败");
            }
            //Step 4：用户活跃统计初使化
            setRocketSportActiveUserInto(userInfoDto);
            return ResultFactory.success(userInfoDto.get_userId());
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }

    private void setRocketSportActiveUserInto(UserInfoDto userInfoDto) {
        try {
            SportActiveUserOperationDto sportActiveUserOperationDto = SportActiveUserOperationDto.builder().merchantCode(userInfoDto.getMerchantCode())
                    .merchantName(userInfoDto.getMerchantName()).userName(userInfoDto.getUserName()).operationTime(new Date()).ip(userInfoDto.getRegIp())
                    .dataNode(userInfoDto.getDataNode()).build();
            RocketMQConfig rocketMQConfig = RocketMQConfig.builder().nameServer(nameServer).producerGroup(producerGroup)
                    .producerTimeout(producerTimeout).producerTopic(RocketMQTopic.STATISTICS_TOPIC).build();
            //构建队列消息

            RocketMessage message = RocketMessage.<String>builder().messageBody(JSONUtil.toJsonStr(sportActiveUserOperationDto)).messageId(RocketMessageIdEnum.Sport_ActiveUserInto.getCode()).build();
            //发送事务消息
            SendResult sendResult = rocketMQService.setRocketMQConfig(rocketMQConfig).send(message);
            boolean isCommit = JSONUtil.toJsonStr(sendResult).indexOf("COMMIT_MESSAGE") != -1;
            if (sendResult.getSendStatus() != SendStatus.SEND_OK || isCommit) {
                log.debug("体育活跃用户登录统计初使化写入队列失败");
            }
        } catch (Exception ex) {
            log.debug(MessageFormat.format("体育活跃用户登录统计初使化写入队列失败，原因：{0}", ex.toString()));
        }
    }

    @ApiOperation(value = "余额转入", notes = "")
    @PostMapping("translateIn")
    public ActionResult translateIn(@Valid @RequestBody TranslateRequestVo translateRequestVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, translateRequestVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), translateRequestVo.getUsername());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(translateRequestVo, merchant.getMerchantKey());
            //Step 3: 验证用户
            UserInfoDto userInfoDto = PlatformApiCommon.getUserInfo(userServiceApi, username, merchant.getDataNode());

            //Step 4: 验证金额
            PlatformApiCommon.validateMoney(translateRequestVo.getAmount());
            float amount = Float.parseFloat(translateRequestVo.getAmount());

            //Step 5: 验证订单是否存在
            PlatformApiCommon.validateSerial(userServiceApi, translateRequestVo.getMerchantSerial(), merchant.getMerchantCode(), merchant.getDataNode());

            //Step 6: 开始转入
            UpdateUserInfoMoneyVo userInfoMoneyVo = UpdateUserInfoMoneyVo.builder().userId(userInfoDto.getUserId()).merchantCode(merchant.getMerchantCode())
                    .dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial()).money(amount)
                    .moneyChangeEnum(MoneyChangeEnum.MoneyIn.getCode()).build();

            UserBalanceOperationDto balanceOperationDto = UserBalanceOperationDto.builder().userId(userInfoDto.getUserId()).userName(userInfoDto.getUserName())
                    .merchantName(merchant.getMerchantName()).merchantCode(merchant.getMerchantCode()).dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial())
                    .operationMoney(amount).operationType(MoneyChangeEnum.MoneyIn.getCode())
                    .remark(MoneyChangeEnum.MoneyIn.getMsg()).operationDate(DateUtil.parse(DateUtil.today())).build();

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
                        result = userServiceApi.updateUserInfoMoney(m);
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
                boolean isCommit = JSONUtil.toJsonStr(sendResult).contains("COMMIT_MESSAGE");
                return sendResult.getSendStatus() == SendStatus.SEND_OK && isCommit ? ResultFactory.success() : ResultFactory.error();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }


    @ApiOperation(value = "余额转出", notes = "")
    @PostMapping("translateOut")
    public ActionResult translateOut(@Valid @RequestBody TranslateRequestVo translateRequestVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, translateRequestVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), translateRequestVo.getUsername());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(translateRequestVo, merchant.getMerchantKey());

            //Step 3: 验证用户
            UserInfoDto userInfoDto = PlatformApiCommon.getUserInfo(userServiceApi, username, merchant.getDataNode());

            //Step 4: 验证金额
            PlatformApiCommon.validateMoney(translateRequestVo.getAmount());
            float amount = Float.parseFloat(translateRequestVo.getAmount());

            //Step 5: 验证订单是否存在
            PlatformApiCommon.validateSerial(userServiceApi, translateRequestVo.getMerchantSerial(), merchant.getMerchantCode(), merchant.getDataNode());

            //Step 6: 开始转出
            UpdateUserInfoMoneyVo userInfoMoneyVo = UpdateUserInfoMoneyVo.builder().userId(userInfoDto.getUserId())
                    .dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial()).money(amount)
                    .moneyChangeEnum(MoneyChangeEnum.MoneyOut.getCode()).build();

            UserBalanceOperationDto balanceOperationDto = UserBalanceOperationDto.builder().userId(userInfoDto.getUserId()).userName(userInfoDto.getUserName())
                    .merchantName(merchant.getMerchantName()).merchantCode(merchant.getMerchantCode()).dataNode(merchant.getDataNode()).merchantSerial(translateRequestVo.getMerchantSerial())
                    .operationMoney(amount).operationType(MoneyChangeEnum.MoneyOut.getCode())
                    .remark(MoneyChangeEnum.MoneyOut.getMsg()).operationDate(DateUtil.parse(DateUtil.today())).build();

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
                        result = userServiceApi.updateUserInfoMoney(m);
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
                boolean isCommit = JSONUtil.toJsonStr(sendResult).contains("COMMIT_MESSAGE");
                return sendResult.getSendStatus() == SendStatus.SEND_OK && isCommit ? ResultFactory.success() : ResultFactory.error();
            } finally {
                if (lock != null) {
                    lock.unlock();
                }
            }
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }

    @ApiOperation(value = "查询余额", notes = "")
    @PostMapping("queryBalance")
    public ActionResult queryBalance(@Valid @RequestBody PlatformApiRequestVo queryBalanceVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, queryBalanceVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), queryBalanceVo.getUsername());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(queryBalanceVo, merchant.getMerchantKey());

            //Step 4: 验证用户
            UserInfoDto userInfoDto = PlatformApiCommon.getUserInfo(userServiceApi, username, merchant.getDataNode());
            return ResultFactory.success(userInfoDto.getMoney());
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }

    }


    @ApiOperation(value = "查询订单状态(0:成功 1：失败 2:未知)", notes = "")
    @PostMapping("transRecord")
    public ActionResult transRecord(@Valid @RequestBody TransRecordRequestVo transRecordRequestVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, transRecordRequestVo.getChannel());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(transRecordRequestVo, merchant.getMerchantKey());
            //Step 3: 验证订单
            TransferStatusEnum transferStatusEnum = PlatformApiCommon.orderIsExist(userServiceApi, transRecordRequestVo.getMerchantSerial(), merchant.getMerchantCode(), merchant.getDataNode());
            return transferStatusEnum == TransferStatusEnum.Success ? ResultFactory.success() : ResultFactory.error(transferStatusEnum.getMsg());
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }


    @ApiOperation(value = "踢出游戏", notes = "")
    @PostMapping("loginOut")
    public ActionResult loginOut(@Valid @RequestBody PlatformApiRequestVo loginOutVo) {
        try {
            //Step 1: 验证渠道号
            MerchantDto merchant = PlatformApiCommon.validateMerchant(merchantServiceApi, loginOutVo.getChannel());
            String username = MessageFormat.format("{0}_{1}", merchant.getMerchantCode(), loginOutVo.getUsername());
            //Step 2: 验证签名
            PlatformApiCommon.validateSign(loginOutVo, merchant.getMerchantKey());
            //Step 3: 验证用户
            UserInfoDto userInfoDto = PlatformApiCommon.getUserInfo(userServiceApi, username, merchant.getDataNode());
            UserInfoVo userInfoVo = UserInfoVo.builder().userName(userInfoDto.getUserName()).build();
            boolean bResult = userServiceApi.administratorKicked(username);
            return bResult ? ResultFactory.success() : ResultFactory.error();
        } catch (Exception ex) {
            return ResultFactory.error(ex.getMessage());
        }
    }
}
