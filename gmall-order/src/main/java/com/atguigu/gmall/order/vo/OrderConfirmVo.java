package com.atguigu.gmall.order.vo;

import com.atguigu.gmall.oms.vo.OrderItemVo;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVo {

    //用户的购物地址
    private List<MemberReceiveAddressEntity> addresses;

    //购物车，但是不能用购物车的，要从数据库里面取
    private List<OrderItemVo> orderItemVos;

    //积分
    private Integer bounds;

    //防重复提交标识
    private String orderToken;

}
