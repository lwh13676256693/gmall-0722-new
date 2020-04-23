package com.atguigu.gmall.oms.dao;

import com.atguigu.gmall.oms.entity.OrderEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单
 * 
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-28 11:06:18
 */
@Mapper
public interface OrderDao extends BaseMapper<OrderEntity> {

    public int closeOrder(String orderToken);

    public int payOrder(String orderToken);
	
}
