package com.walker.transaction.usd.service;

import com.walker.transaction.Constant;
import com.walker.transaction.rmb.RmbAccountService;
import com.walker.transaction.usd.UsdAccount;
import com.walker.transaction.usd.UsdAccountFreeze;
import com.walker.transaction.usd.UsdAccountService;
import com.walker.transaction.usd.repository.UsdAccountFreezeRepository;
import com.walker.transaction.usd.repository.UsdAccountRepository;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.dromara.hmily.annotation.HmilyTCC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author fcwalker
 * @date 2020/12/18 17:38
 **/
@DubboService(version = "1.0.0")
@Service
@Transactional
public class UsdAccountServiceImpl implements UsdAccountService {

    @Autowired
    private UsdAccountRepository usdAccountRepository;

    @Autowired
    private UsdAccountFreezeRepository usdAccountFreezeRepository;

    @Override
    public boolean usdPayment(Integer payerId, Integer count) throws Exception {
        UsdAccount usdAccount = usdAccountRepository.getOne(payerId);
        Integer amount = usdAccount.getAmount();
        if (amount < count) {
            throw new Exception("余额不足");
        }
        UsdAccountFreeze accountFreeze = usdAccountFreezeRepository.findByUserIdAndFreezeType(payerId, 1);
        int freezeCount = 0;
        if (null == accountFreeze) {
            accountFreeze = new UsdAccountFreeze();
            accountFreeze.setUserId(payerId);
            accountFreeze.setAmount(count);
            accountFreeze.setFreezeType(1);
            usdAccountFreezeRepository.save(accountFreeze);
            freezeCount = 1;
        } else {
            freezeCount = usdAccountFreezeRepository.accountFreeze(payerId, count, 1);
        }
        int payCount = usdAccountRepository.payment(payerId, count);
        if (payCount > 0 && freezeCount > 0) {
            return true;
        }
        throw new Exception("支付失败");
    }

    public void confirmUsdPayment(Integer payerId, Integer count) {
        System.out.println("支付成功");
        usdAccountFreezeRepository.accountFinish(payerId, count, 1);
    }

    public void cancelUsdPayment(Integer payerId, Integer count) {
        System.out.println("支付cancel");
        usdAccountRepository.paymentCancel(payerId, count);
        usdAccountFreezeRepository.accountFinish(payerId, count, 1);
    }

    @Override
    public boolean usdCollection(Integer payerId, Integer count) throws Exception {
        UsdAccountFreeze accountFreeze = usdAccountFreezeRepository.findByUserIdAndFreezeType(payerId, 2);
        int freezeCount = 0;
        if (null == accountFreeze) {
            accountFreeze = new UsdAccountFreeze();
            accountFreeze.setUserId(payerId);
            accountFreeze.setAmount(count);
            accountFreeze.setFreezeType(2);
            usdAccountFreezeRepository.save(accountFreeze);
            freezeCount = 1;
        } else {
            freezeCount = usdAccountFreezeRepository.accountFreeze(payerId, count, 2);
        }
        if (freezeCount > 0) {
            return true;
        }
        throw new Exception("支付失败");
    }

    public void confirmUsdCollection(Integer payerId, Integer count) {
        System.out.println("收款成功");
        usdAccountRepository.collection(payerId, count);
        usdAccountFreezeRepository.accountFinish(payerId, count, 2);
    }

    public void cancelUsdCollection(Integer payerId, Integer count) {
        System.out.println("收款cancel");
        usdAccountFreezeRepository.accountFinish(payerId, count, 2);
    }

    @DubboReference(version = "1.0.0", url = "dubbo://127.0.0.1:12390")
    private RmbAccountService rmbAccountService;

    @Override
    @HmilyTCC(confirmMethod = "confirmUsdTrade", cancelMethod = "cancelUsdTrade")
    public boolean usdTrade(Integer payerId, Integer payeeId, Integer usdCount)  throws Exception {
        // 兑换人支付美元
        usdPayment(payerId, usdCount);
        Integer rmbCount = usdCount * Constant.USD_RATE;
        // 被兑换人支付人民币
        rmbAccountService.rmbPayment(payeeId, rmbCount);
        // 兑换人收取人民币
        rmbAccountService.rmbCollection(payerId, rmbCount);
        // 被兑换人收取美元
        usdCollection(payeeId, usdCount);
        return true;
    }

    public void confirmUsdTrade(Integer payerId, Integer count) {
        System.out.println("支付成功");
        usdAccountFreezeRepository.accountFinish(payerId, count, 1);
        System.out.println("收款成功");
        usdAccountRepository.collection(payerId, count);
        usdAccountFreezeRepository.accountFinish(payerId, count, 2);
    }

    public void cancelUsdTrade(Integer payerId, Integer count) {
        usdAccountRepository.paymentCancel(payerId, count);
        usdAccountFreezeRepository.accountFinish(payerId, count, 1);
        System.out.println("收款cancel");
        usdAccountFreezeRepository.accountFinish(payerId, count, 2);
    }

}