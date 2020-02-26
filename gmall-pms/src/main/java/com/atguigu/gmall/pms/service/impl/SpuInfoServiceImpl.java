package com.atguigu.gmall.pms.service.impl;

import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;
import com.atguigu.gmall.pms.dao.SkuInfoDao;
import com.atguigu.gmall.pms.dao.SpuInfoDao;
import com.atguigu.gmall.pms.dao.SpuInfoDescDao;
import com.atguigu.gmall.pms.entity.ProductAttrValueEntity;
import com.atguigu.gmall.pms.entity.SkuImagesEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.pms.entity.SpuInfoEntity;
import com.atguigu.gmall.pms.feign.GmallSmsClient;
import com.atguigu.gmall.pms.service.*;
import com.atguigu.gmall.pms.vo.BaseAttrVo;
import com.atguigu.gmall.pms.vo.SkuInfoVo;
import com.atguigu.gmall.pms.vo.SpuInfoVo;
import com.atguigu.gmall.sms.vo.SkuSaleVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.seata.spring.annotation.GlobalTransactional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SpuInfoDescDao descDao;

    @Autowired
    private ProductAttrValueService attrValueService;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private SpuInfoDescService spuInfoDescService;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Value("${item.rabbitmq.exchange}")
    private String EXCHANGE_NAME;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageVo(page);
    }

    @Override
    public PageVo querySpuPage(QueryCondition queryCondition, Long cid) {
        String key = queryCondition.getKey();
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(queryCondition),
                new QueryWrapper<SpuInfoEntity>()
                        .lambda().eq(cid != 0, SpuInfoEntity::getCatalogId, cid)
                        .and(StringUtils.isNotBlank(key), sql -> sql.eq(SpuInfoEntity::getId, key)
                                .or().like(SpuInfoEntity::getSpuName, key))
        );

        return new PageVo(page);
    }

    @Override
    @GlobalTransactional
    public void bigSave(SpuInfoVo spuInfoVo) {
        //1.保存spu相关的3张表
        Long spuInfoVoId = saveSpuInfo(spuInfoVo);

        //保存desc
        this.spuInfoDescService.saveSpuInfoDesc(spuInfoVo, spuInfoVoId);

        saveBaseAttrValue(spuInfoVo, spuInfoVoId);

        //2.保存sku相关的3张表
        saveSkuAndSale(spuInfoVo, spuInfoVoId);

//        int i = 1/0;
        sendMess("insert",spuInfoVoId);
    }

    private void sendMess(String type,Long spuId){
        this.amqpTemplate.convertAndSend(EXCHANGE_NAME,"item."+type,spuId);
    }

    private void saveSkuAndSale(SpuInfoVo spuInfoVo, Long spuInfoVoId) {
        List<SkuInfoVo> skus = spuInfoVo.getSkus();
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }
        skus.forEach(skuInfoVo -> {
            skuInfoVo.setSpuId(spuInfoVoId);
            skuInfoVo.setSkuCode(UUID.randomUUID().toString());
            skuInfoVo.setBrandId(spuInfoVo.getBrandId());
            skuInfoVo.setCatalogId(spuInfoVo.getCatalogId());
            List<String> images = skuInfoVo.getImages();
            //新增默认图片
            if (!CollectionUtils.isEmpty(images)) {
                skuInfoVo.setSkuDefaultImg(
                        StringUtils.isNoneBlank(skuInfoVo.getSkuDefaultImg()) ? skuInfoVo.getSkuDefaultImg() : images.get(0));
            }
            this.skuInfoDao.insert(skuInfoVo);
            Long skuId = skuInfoVo.getSkuId();

            if (!CollectionUtils.isEmpty(images)) {
                List<SkuImagesEntity> skuImagesList = images.stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setImgUrl(image);
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setDefaultImg(StringUtils.equals(skuInfoVo.getSkuDefaultImg(), image) ? 1 : 0);
                    return skuImagesEntity;
                }).collect(Collectors.toList());
                this.skuImagesService.saveBatch(skuImagesList);
            }
            List<SkuSaleAttrValueEntity> saleAttrs = skuInfoVo.getSaleAttrs();
            if (!CollectionUtils.isEmpty(saleAttrs)){
                //设计id再批量保存
                saleAttrs.forEach(skuSaleAttrValueEntity -> skuSaleAttrValueEntity.setSkuId(skuId));
                this.skuSaleAttrValueService.saveBatch(saleAttrs);
            }
            //3.营销的3张表
            SkuSaleVo skuSaleVo = new SkuSaleVo();
            BeanUtils.copyProperties(skuInfoVo,skuSaleVo);
            skuSaleVo.setSkuId(skuId);
            this.gmallSmsClient.saveSale(skuSaleVo);

        });
    }

    private void saveBaseAttrValue(SpuInfoVo spuInfoVo, Long spuInfoVoId) {
        List<BaseAttrVo> baseAttrs = spuInfoVo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<ProductAttrValueEntity> attrValueEntity = baseAttrs.stream().map(baseAttrVo -> {
                ProductAttrValueEntity attrValueEntitys = baseAttrVo;
                attrValueEntitys.setSpuId(spuInfoVoId);
                return attrValueEntitys;
            }).collect(Collectors.toList());
            this.attrValueService.saveBatch(attrValueEntity);
        }
    }

    //保存spu_info表的
    private Long saveSpuInfo(SpuInfoVo spuInfoVo) {
        spuInfoVo.setCreateTime(new Date());
        spuInfoVo.setUodateTime(spuInfoVo.getCreateTime());
        this.save(spuInfoVo);

        return spuInfoVo.getId();
    }

}