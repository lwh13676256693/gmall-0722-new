package com.atguigu.gmall.wms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.rabbitmq.client.AMQP;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.wms.dao.WareSkuDao;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private WareSkuDao wareSkuDao;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String KEY_PREFIX = "stock:lock";

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageVo(page);
    }

    @Override
    @Transactional
    public String checkAndLockStore(List<SkuLockVo> skuLockVos) {

        if (CollectionUtils.isEmpty(skuLockVos)) {
            return "没有选中的商品";
        }

        //检查并锁定库存
        skuLockVos.forEach(skuLockVo -> {
            lockStore(skuLockVo);
        });

        //获取因库存不足没被锁定的数据
        List<SkuLockVo> unLockSku = skuLockVos.stream().filter(skuLockVo -> skuLockVo.getLock() == false).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(unLockSku)) {
            //解锁已锁定商品的库存
            List<SkuLockVo> lockSku = skuLockVos.stream().filter(SkuLockVo::getLock).collect(Collectors.toList());
            lockSku.forEach(skuLockVo -> {
                this.wareSkuDao.unLockStore(skuLockVo.getWareSkuId(), skuLockVo.getCount());
            });

            //提示锁定失败的商品
            List<Long> skuIds = unLockSku.stream().map(SkuLockVo::getSkuId).collect(Collectors.toList());
            return "下单失败，库存不足" + skuIds.toString();
        }
        String orderToken = skuLockVos.get(0).getOrderToken();

        this.stringRedisTemplate.opsForValue().set(KEY_PREFIX+orderToken, JSON.toJSONString(skuLockVos));

        //锁定成功，发送延时消息，定时解锁
        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","stock.ttl",orderToken);

        return null;
    }

    private void lockStore(SkuLockVo skuLockVo) {
        RLock lock = this.redissonClient.getLock("stock:" + skuLockVo.getSkuId());
        lock.lock();
        //查询库存够不够
        List<WareSkuEntity> wareSkuEntities = this.wareSkuDao.checkStore(skuLockVo.getSkuId(), skuLockVo.getCount());
        if (!CollectionUtils.isEmpty(wareSkuEntities)) {
            //锁定库存信息
            this.wareSkuDao.lockDao(wareSkuEntities.get(0).getId(), skuLockVo.getCount());
            skuLockVo.setLock(true);
            skuLockVo.setWareSkuId(wareSkuEntities.get(0).getId());
        } else {
            skuLockVo.setLock(false);
        }
        lock.unlock();
    }

}