package com.atguigu.gmall.wms.service;

import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;
import com.atguigu.gmall.wms.entity.FeightTemplateEntity;
import com.baomidou.mybatisplus.extension.service.IService;


/**
 * 运费模板
 *
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-15 21:10:40
 */
public interface FeightTemplateService extends IService<FeightTemplateEntity> {

    PageVo queryPage(QueryCondition params);
}

