package com.atguigu.gmall.cart.listener;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CartListener {

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "gmall:cart:";

    private static final String PRICE_PREFIX = "gmall:sku:";

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "CART-ITEM-QUEUE", durable = "true"),
            exchange = @Exchange(value = "GMALL-PMS-EXCHANG",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"item.update"}
    ))
    public void listener(Long spuId) {
        Resp<List<SkuInfoEntity>> listResp = this.gmallPmsClient.querySkusBySpuId(spuId);
        List<SkuInfoEntity> infoEntityList = listResp.getData();
        infoEntityList.forEach(entity->{
            this.stringRedisTemplate.opsForValue().set(PRICE_PREFIX+entity.getSkuId(),entity.getPrice().toString());
        });

    }
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "ORDER-CART-QUEUE",durable = "true"),
            exchange = @Exchange(value = "GMALL-ORDER-EXCHANG",ignoreDeclarationExceptions = "true",type = ExchangeTypes.TOPIC),
            key = {"cart.delete"}
    ))
    public void deleteListener(Map<String,Object> map){
        Long userId =(Long) map.get("userId");
        List<Object> skuIds = (List<Object>)map.get("skuIds");
        BoundHashOperations<String, Object, Object> hashOps = this.stringRedisTemplate.boundHashOps(KEY_PREFIX + userId);
        List<String> skus = skuIds.stream().map(skuId -> skuId.toString()).collect(Collectors.toList());
        String[] ids = skus.toArray(new String[skus.size()]);
        hashOps.delete(ids);


    }
}
