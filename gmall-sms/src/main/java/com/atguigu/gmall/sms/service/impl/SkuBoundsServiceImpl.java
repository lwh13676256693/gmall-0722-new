package com.atguigu.gmall.sms.service.impl;

import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import com.atguigu.gmall.sms.dao.SkuFullReductionDao;
import com.atguigu.gmall.sms.dao.SkuLadderDao;
import com.atguigu.gmall.sms.entity.SkuFullReductionEntity;
import com.atguigu.gmall.sms.entity.SkuLadderEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.sms.dao.SkuBoundsDao;
import com.atguigu.gmall.sms.entity.SkuBoundsEntity;
import com.atguigu.gmall.sms.service.SkuBoundsService;
import org.springframework.transaction.annotation.Transactional;


@Service("skuBoundsService")
public class SkuBoundsServiceImpl extends ServiceImpl<SkuBoundsDao, SkuBoundsEntity> implements SkuBoundsService {

    @Autowired
    private SkuLadderDao skuLadderDao;

    @Autowired
    private SkuFullReductionDao reductionDao;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SkuBoundsEntity> page = this.page(
                new Query<SkuBoundsEntity>().getPage(params),
                new QueryWrapper<SkuBoundsEntity>()
        );

        return new PageVo(page);
    }

    @Transactional
    @Override
    public void saveSale(SkuSaleVo skuSaleVo) {
        //保存3张表,然后用fegin调用
        // sms_sku_bounds
        SkuBoundsEntity skuBoundsEntity = new SkuBoundsEntity();
        skuBoundsEntity.setSkuId(skuSaleVo.getSkuId());
        skuBoundsEntity.setGrowBounds(skuBoundsEntity.getGrowBounds());
        skuBoundsEntity.setBuyBounds(skuBoundsEntity.getBuyBounds());
        List<Integer> work = skuSaleVo.getWork();
        skuBoundsEntity.setWork(work.get(3)*1+work.get(2)*2+work.get(1)*4+work.get(0)*8);
        this.save(skuBoundsEntity);
        //sms_sku_ladder
        SkuLadderEntity skuLadderEntity = new SkuLadderEntity();
        skuLadderEntity.setSkuId(skuSaleVo.getSkuId());
        skuLadderEntity.setFullCount(skuSaleVo.getFullCount());
        skuLadderEntity.setDiscount(skuSaleVo.getDiscount());
        skuLadderEntity.setAddOther(skuSaleVo.getLadderAddOther());
        this.skuLadderDao.insert(skuLadderEntity);

        // sms_sku_full_reduction
        SkuFullReductionEntity reductionEntity = new SkuFullReductionEntity();
        reductionEntity.setSkuId(skuSaleVo.getSkuId());
        reductionEntity.setFullPrice(skuSaleVo.getFullPrice());
        reductionEntity.setReducePrice(skuSaleVo.getReducePrice());
        reductionEntity.setAddOther(skuSaleVo.getFullAddOther());
        this.reductionDao.insert(reductionEntity);


    }

    @Override
    public List<SaleVo> querySalesBySkuId(Long skuId) {
        List<SaleVo> saleVos = new ArrayList<>();
        //查询积分信息
        SkuBoundsEntity skuBoundsEntity = this.getOne(new QueryWrapper<SkuBoundsEntity>().lambda().eq(SkuBoundsEntity::getSkuId, skuId));
        if (skuBoundsEntity != null) {
            SaleVo boundsVo = new SaleVo();
            boundsVo.setType("积分");
            boundsVo.setDesc("成长积分送"+skuBoundsEntity.getGrowBounds()+",赠送积分送"+skuBoundsEntity.getBuyBounds());
            saleVos.add(boundsVo);
        }

        //查询打折信息
        SkuLadderEntity skuLadderEntity = this.skuLadderDao.selectOne(new QueryWrapper<SkuLadderEntity>().lambda().eq(SkuLadderEntity::getSkuId, skuId));
        if (skuLadderEntity != null) {
            SaleVo ladderVo = new SaleVo();
            ladderVo.setType("打折");
            ladderVo.setDesc("满"+skuLadderEntity.getFullCount()+"件,打"+skuLadderEntity.getDiscount().divide(new BigDecimal(10))+"折");
            saleVos.add(ladderVo);
        }

        //查询满减
        SkuFullReductionEntity skuFullReductionEntity = this.reductionDao.selectOne(new QueryWrapper<SkuFullReductionEntity>().lambda().eq(SkuFullReductionEntity::getSkuId, skuId));
        if (skuFullReductionEntity != null) {
            SaleVo reductionVo = new SaleVo();
            reductionVo.setType("满减");
            reductionVo.setDesc("满"+skuFullReductionEntity.getFullPrice()+"减"+skuFullReductionEntity.getReducePrice());
            saleVos.add(reductionVo);
        }

        return saleVos;
    }

}