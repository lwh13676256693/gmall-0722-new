package com.atguigu.gmall.order.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.exception.OrderException;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptors.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVo;
import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.oms.vo.OrderSubmitVo;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import net.bytebuddy.asm.Advice;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private GmallCartClient gmallCartClient;

    @Autowired
    private GmallOmsClient gmallOmsClient;

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private GmallUmsClient gmallUmsClient;

    @Autowired
    private GmallWmsClient gmallWmsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String TOKEN_PREFIX = "order:token:";

    public OrderConfirmVo confirm() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        //通过拦截器的拦截得到用户的id
        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getId();
        if (userId == null) {
            return null;
        }

        //收集起来再add进去即可
        List<CompletableFuture> futures = new ArrayList<>();

        CompletableFuture<Void> addressCompletableFuture = CompletableFuture.runAsync(() -> {
            //获取用户的地址列表
            Resp<List<MemberReceiveAddressEntity>> addressResp = this.gmallUmsClient.queryAddressByUserId(userId);
            List<MemberReceiveAddressEntity> addressEntityList = addressResp.getData();
            confirmVo.setAddresses(addressEntityList);
        }, threadPoolExecutor);

        //检查购物车是否缓存了购物车
        CompletableFuture<Void> bigCompletable = CompletableFuture.supplyAsync(() -> {
            Resp<List<Cart>> cartsResp = this.gmallCartClient.queryCheckCartsByUserId(userId);
            List<Cart> cartList = cartsResp.getData();
            if (CollectionUtils.isEmpty(cartList)) {
                throw new OrderException("请勾选购物车商品");
            }
            return cartList;
        }, threadPoolExecutor).thenAcceptAsync(cartList -> {
            //获取购物车中选中的商品信息 skuId count
            List<OrderItemVo> orderItemVoList = cartList.stream().map(cart -> {
                OrderItemVo orderItemVo = new OrderItemVo();
                Long skuId = cart.getSkuId();

                //异步编排
                CompletableFuture<Void> skuInfoCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<SkuInfoEntity> skuInfoEntityResp = this.gmallPmsClient.querySkuById(skuId);
                    SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
                    if (skuInfoEntity != null) {
                        orderItemVo.setSkuId(cart.getSkuId());
                        orderItemVo.setCount(cart.getCount());
                        orderItemVo.setWeight(skuInfoEntity.getWeight());
                        orderItemVo.setCount(cart.getCount());
                        orderItemVo.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
                        orderItemVo.setPrice(skuInfoEntity.getPrice());
                        orderItemVo.setTitle(skuInfoEntity.getSkuTitle());
                    }
                }, threadPoolExecutor);

                //异步编排保存销售属性
                CompletableFuture<Void> attrValueCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.gmallPmsClient.querySkuSaleAttrValuesBySkuId(skuId);
                    List<SkuSaleAttrValueEntity> attrValueEntities = saleAttrValueResp.getData();
                    orderItemVo.setSaleAttrValues(attrValueEntities);
                }, threadPoolExecutor);

                //异步编排根据id查询仓库的信息
                CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<WareSkuEntity>> wareResp = this.gmallWmsClient.queryWareSkusBySkuId(skuId);
                    List<WareSkuEntity> wareSkuEntityList = wareResp.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                        orderItemVo.setStore(wareSkuEntityList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                    }
                }, threadPoolExecutor);

                //要在里面先完成再去做其他
                CompletableFuture.allOf(skuInfoCompletableFuture, attrValueCompletableFuture, wareCompletableFuture).join();

                return orderItemVo;
            }).collect(Collectors.toList());
            confirmVo.setOrderItemVos(orderItemVoList);
        }, threadPoolExecutor);

        //积分信息
        CompletableFuture<Void> memberCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<MemberEntity> memberEntityResp = this.gmallUmsClient.queryMemberById(userId);
            MemberEntity memberEntity = memberEntityResp.getData();
            confirmVo.setBounds(memberEntity.getIntegration());
        }, threadPoolExecutor);


        //生成一个唯一标志，防重复提交订单，响应到页面有一份，还有一份保存到redis
        CompletableFuture<Void> tokenCompletableFuture = CompletableFuture.runAsync(() -> {
            String orderToken = IdWorker.getIdStr();
            confirmVo.setOrderToken(orderToken);
            this.stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + orderToken, orderToken);
        }, threadPoolExecutor);


        CompletableFuture.allOf(addressCompletableFuture, bigCompletable, memberCompletableFuture, tokenCompletableFuture).join();

        return confirmVo;
    }

    public OrderEntity submit(OrderSubmitVo submitVo) {
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        //获取orderToken
        String orderToken = submitVo.getOrderToken();
        //1防重复提交 查询redis中有没有orderToken，有第一次，放行并删除redis的orderToken

        //释放锁,其他才可以拿到锁(lua脚本)
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

        Long flag = this.stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Arrays.asList(TOKEN_PREFIX + orderToken), orderToken);
        if (flag == 0) {
            throw new OrderException("不可重复提交");
        }
        //2校验价格  总价一致
        List<OrderItemVo> itemVos = submitVo.getItemVos();//送货清单
        BigDecimal totalPrice = submitVo.getTotalPrice();//总价
        //没有勾选商品
        if (CollectionUtils.isEmpty(itemVos)) {
            throw new OrderException("没有购买商品，请到购物车勾选商品");
        }
        BigDecimal currentTotalPrice = itemVos.stream().map(item -> {
            Resp<SkuInfoEntity> skuInfoEntityResp = this.gmallPmsClient.querySkuById(item.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                return skuInfoEntity.getPrice().multiply(new BigDecimal(item.getCount()));
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();
        //判断价格和页面总价是否一致
        if (currentTotalPrice.compareTo(totalPrice) != 0) {
            throw new OrderException("页面过期，请刷新后再下单");
        }
        //3校验库存并锁定  一次性提示库存不够的信息(远程接口待开发)
        List<SkuLockVo> skuLockVos = itemVos.stream().map(item -> {
            SkuLockVo skuLockVo = new SkuLockVo();
            skuLockVo.setSkuId(item.getSkuId());
            skuLockVo.setCount(item.getCount());
            skuLockVo.setOrderToken(orderToken);
            return skuLockVo;
        }).collect(Collectors.toList());
        Resp<Object> wareResp = this.gmallWmsClient.checkAndLockStore(skuLockVos);
        if (wareResp.getCode() != 0) {
            throw new OrderException(wareResp.getMsg());
        }

//        int i = 1 / 0;
        //4下单创建  ，插入订单详情表里面
        Resp<OrderEntity> orderEntityResp = null;
        try {
            submitVo.setUserId(userInfo.getId());
            orderEntityResp = this.gmallOmsClient.saveOrder(submitVo);
            OrderEntity orderEntity = orderEntityResp.getData();
        } catch (Exception e) {
            e.printStackTrace();
            //发送消息给wms，解锁对应的库存
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE", "stock.unlock", orderToken);
            throw new OrderException("创建订单失败");
        }

        //5删除购物车信息（发送消息删除购物车）
        Map<Object, Object> map = new HashMap<>();
        map.put("userId", userInfo.getId());
        List<Long> skuIds = itemVos.stream().map(OrderItemVo::getSkuId).collect(Collectors.toList());
        map.put("skuIds", skuIds);
        amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANG", "cart.delete", map);

        if (orderEntityResp != null) {
            return orderEntityResp.getData();
        }
        return null;
    }

    public static void main(String[] args) {
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            System.out.println("执行线程");
        }, 0L, 1L, TimeUnit.SECONDS);
    }


}
