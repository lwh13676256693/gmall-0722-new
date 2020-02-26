package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.SpuInfoDescEntity;
import com.atguigu.gmall.pms.vo.SpuInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;


/**
 * spu信息介绍
 *
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-13 11:41:23
 */
public interface SpuInfoDescService extends IService<SpuInfoDescEntity> {

    PageVo queryPage(QueryCondition params);

    void saveSpuInfoDesc(SpuInfoVo spuInfoVo, Long spuInfoVoId);
}

