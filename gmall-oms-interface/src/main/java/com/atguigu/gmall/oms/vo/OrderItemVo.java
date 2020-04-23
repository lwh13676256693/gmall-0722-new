package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.SaleVo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderItemVo {

    private Long skuId;
    private String title;
    private String defaultImage;
    private BigDecimal price;//数据库最新价格
    private Integer count;
    private Boolean store;
    private List<SkuSaleAttrValueEntity> saleAttrValues;//销售属性
    private List<SaleVo> sales;//营销信息
    private BigDecimal weight;

}
