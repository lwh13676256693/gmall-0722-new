package com.atguigu.gmall.order.controller;


import com.alipay.api.AlipayApiException;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.pay.AlipayTemplate;
import com.atguigu.gmall.order.pay.PayAsyncVo;
import com.atguigu.gmall.order.pay.PayVo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayTemplate alipayTemplate;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    //确认订单信息
    @GetMapping("confirm")
    public Resp<OrderConfirmVo> confirm(){
        OrderConfirmVo confirmVo = this.orderService.confirm();
        return Resp.ok(confirmVo);
    }

    @PostMapping("submit")
    public Resp<Object> submit(@RequestBody OrderSubmitVo submitVo) throws AlipayApiException {
        OrderEntity orderEntity = this.orderService.submit(submitVo);
        PayVo payVo = new PayVo();
        payVo.setOut_trade_no(orderEntity.getOrderSn());
        payVo.setTotal_amount(orderEntity.getPayAmount()!=null?orderEntity.getPayAmount().toString():"100");
        payVo.setSubject("aaaa");
        payVo.setBody("bbbb");

        String form = this.alipayTemplate.pay(payVo);
        System.out.println(form);
        return Resp.ok(null);
    }

    @PostMapping("pay/success")
    public Resp<Object> paySuccess(PayAsyncVo payAsyncVo){

        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","order.pay",payAsyncVo.getOut_trade_no());
        return Resp.ok(null);
    }

    @PostMapping("seckill/{skuId}")
    public Resp<Object> seckill(@PathVariable("skuId")Long skuId){

        //开启并发锁，控制线程最大访问量
        RSemaphore semaphore = this.redissonClient.getSemaphore("lock:" + skuId);
        semaphore.trySetPermits(50);

        if (semaphore.tryAcquire()) {
            //获取redis中库存信息
            String countString = this.stringRedisTemplate.opsForValue().get("order:seckill:" + skuId);
            //没有，秒杀结束
            if (StringUtils.isEmpty(countString) || Integer.parseInt(countString)==0) {
                return Resp.ok("秒杀结束");
            }
            int count = Integer.parseInt(countString);
            //减redis的库存
            this.stringRedisTemplate.opsForValue().set("order:seckill:"+skuId,String.valueOf(--count));

            //发送消息给消息队列，将来真正的减库存
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setCount(1);
            skuLockVo.setSkuId(skuId);
            String orderToken = IdWorker.getIdStr();
            skuLockVo.setOrderToken(orderToken);

            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","order.seckill",skuLockVo);

            RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("count:down:" + orderToken);
            countDownLatch.trySetCount(1);

            //释放锁
            semaphore.release();
            //响应成功
            return Resp.ok("秒杀ok！");
        }
        return Resp.ok("请稍后再试");
    }

    @GetMapping("seckill/{orderToken}")
    public Resp<Object> querySeckill(@PathVariable("orderToken")String orderToken) throws InterruptedException {
        RCountDownLatch countDownLatch = this.redissonClient.getCountDownLatch("count:down:" + orderToken);
        countDownLatch.await();

        //查询订单，并响应
        //发送feign请求 查询订单
        return Resp.ok(null);
    }


}
