package com.atguigu.gmall.pms.service.impl;

import com.atguigu.gmall.pms.dao.AttrAttrgroupRelationDao;
import com.atguigu.gmall.pms.dao.AttrDao;
import com.atguigu.gmall.pms.dao.ProductAttrValueDao;
import com.atguigu.gmall.pms.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gmall.pms.entity.AttrEntity;
import com.atguigu.gmall.pms.entity.AttrGroupEntity;
import com.atguigu.gmall.pms.entity.ProductAttrValueEntity;
import com.atguigu.gmall.pms.vo.GroupVo;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.pms.dao.AttrGroupDao;
import com.atguigu.gmall.pms.service.AttrGroupService;
import org.springframework.util.CollectionUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrAttrgroupRelationDao relationDao;

    @Autowired
    private ProductAttrValueDao productAttrValueDao;

    @Autowired
    private AttrDao attrDao;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public PageVo queryGroupByPage(QueryCondition condition, Long catId) {

        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(condition),
                new QueryWrapper<AttrGroupEntity>().lambda()
                        .eq(catId != null, AttrGroupEntity::getCatelogId, catId)
        );

        return new PageVo(page);
    }

    @Override
    public GroupVo queryGroupWithAttrsByGid(Long gid) {
        GroupVo groupVo = new GroupVo();
        AttrGroupEntity attrGroupEntity = this.getById(gid);
        BeanUtils.copyProperties(attrGroupEntity, groupVo);

        List<AttrAttrgroupRelationEntity> relationList = this.relationDao
                .selectList(new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .lambda().eq(AttrAttrgroupRelationEntity::getAttrGroupId, gid));

        if (CollectionUtils.isEmpty(relationList)) {
            return groupVo;
        }
        groupVo.setRelations(relationList);
        //根据attrIds查询，所有的规格参数
        List<Long> attrIds = relationList.stream().map(relationEntity
                -> relationEntity.getAttrId()).collect(Collectors.toList());

        List<AttrEntity> attrEntityList = this.attrDao.selectBatchIds(attrIds);
        groupVo.setAttrEntities(attrEntityList);

        return groupVo;
    }

    @Override
    public List<GroupVo> queryGroupWithAttrsByCid(Long cid) {
        List<AttrGroupEntity> groupEntityList = this.list(new QueryWrapper<AttrGroupEntity>().lambda().eq(AttrGroupEntity::getCatelogId, cid));

        List<GroupVo> collect = groupEntityList.stream().map(groupEntity -> {
            GroupVo groupVo = this.queryGroupWithAttrsByGid(groupEntity.getAttrGroupId());
            return groupVo;
        }).collect(Collectors.toList());

        return collect;
    }

    @Override
    public List<ItemGroupVo> queryItemGroupVoByCidAndSpuId(Long cid, Long spuId) {
        List<AttrGroupEntity> attrGroupList = this.list(new QueryWrapper<AttrGroupEntity>().lambda().eq(AttrGroupEntity::getCatelogId, cid));
        return attrGroupList.stream().map(group -> {
            ItemGroupVo itemGroupVo = new ItemGroupVo();

            itemGroupVo.setName(group.getAttrGroupName());

            //查询规格参数和值
            List<AttrAttrgroupRelationEntity> attrAttrgroupRelationEntities = this.relationDao.selectList(new QueryWrapper<AttrAttrgroupRelationEntity>()
                    .lambda().eq(AttrAttrgroupRelationEntity::getAttrGroupId, group.getAttrGroupId()));
            List<Long> attrIds = attrAttrgroupRelationEntities.stream().map(AttrAttrgroupRelationEntity::getAttrId).collect(Collectors.toList());

            List<ProductAttrValueEntity> productAttrValueEntities = this.productAttrValueDao.selectList(new QueryWrapper<ProductAttrValueEntity>()
                    .lambda().eq(ProductAttrValueEntity::getSpuId,spuId).in(ProductAttrValueEntity::getAttrId,attrIds));

            itemGroupVo.setBaseAttrs(productAttrValueEntities);

            return itemGroupVo;
        }).collect(Collectors.toList());
    }

}