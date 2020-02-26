package com.atguigu.gmall.pms.service;

import com.atguigu.gmall.pms.entity.AttrAttrgroupRelationEntity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.QueryCondition;

import java.util.List;


/**
 * 属性&属性分组关联
 *
 * @author liangwenhao
 * @email lwh@atguigu.com
 * @date 2020-02-13 11:41:24
 */
public interface AttrAttrgroupRelationService extends IService<AttrAttrgroupRelationEntity> {

    PageVo queryPage(QueryCondition params);

    void deleteRelation(List<AttrAttrgroupRelationEntity> relationEntityList);
}

