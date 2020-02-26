package com.atguigu.gmall.sms.service;

import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;

import java.util.List;


/**
 * 商品sku积分设置
 *
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-13 17:15:21
 */
public interface SkuBoundsService extends IService<SkuBoundsEntity> {

    PageVo queryPage(QueryCondition params);

    void saveSale(SkuSaleVo skuSaleVo);

    List<SaleVo> querySalesBySkuId(Long skuId);
}

