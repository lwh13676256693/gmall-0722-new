package com.atguigu.gmall.wms.vo;

import lombok.Data;

@Data
public class SkuLockVo {

    private Long skuId;
    private  Integer count;
    private  Long wareSkuId;//锁定库存的id
    private Boolean lock;//锁定商品的锁定状态
    private String orderToken;
}
