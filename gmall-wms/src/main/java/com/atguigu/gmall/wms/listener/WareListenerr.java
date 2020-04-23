package com.atguigu.gmall.wms.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WareListenerr {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Autowired
    private WareSkuDao wareSkuDao;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS-UNLOCK-QUEUE", durable = "true"),
            exchange = @Exchange(value = "GMALL-ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"stock.unlock"}

    ))
    public void unlockListener(String orderToken) {
        String lockJson = this.stringRedisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        if (StringUtils.isEmpty(lockJson)){
            List<SkuLockVo> skuLockVos = JSON.parseArray(lockJson, SkuLockVo.class);
            skuLockVos.forEach(skuLockVo -> {
                this.wareSkuDao.unLockStore(skuLockVo.getWareSkuId(),skuLockVo.getCount());
            });
        }
        //删除redis的
        this.stringRedisTemplate.delete(KEY_PREFIX + orderToken);

    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "WMS-UNLOCK-QUEUE", durable = "true"),
            exchange = @Exchange(value = "GMALL-ORDER-EXCHANGE",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"stock.minus"}

    ))
    public void minusStoreListener(String orderToken) {
        String lockJson = this.stringRedisTemplate.opsForValue().get(KEY_PREFIX + orderToken);
        List<SkuLockVo> skuLockVos = JSON.parseArray(lockJson, SkuLockVo.class);
        skuLockVos.forEach(skuLockVo -> {
            this.wareSkuDao.minusStore(skuLockVo.getWareSkuId(),skuLockVo.getCount());
        });

    }

//    @Scheduled(fixedRate = 10000)
//    public void test(){
//        System.out.println("执行定时任务");
//    }
}
