package com.atguigu.gmall.search.listener;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.search.feign.GmallPmsClient;
import com.atguigu.gmall.search.feign.GmallWmsClient;
import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchAttr;
import com.atguigu.gmall.search.repository.GoodsRepository;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GoodsListener {

    @Autowired
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private GmallPmsClient gmallPmsClient;
    @Autowired
    private GmallWmsClient gmallWmsClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "gmall-search-queue",durable = "true"),
            //跟交换机一样
            exchange = @Exchange(value = "GMALL-PMS-EXCHANG",type = ExchangeTypes.TOPIC,
            ignoreDeclarationExceptions = "true"),
            key = {"item.insert","item.update"}
    ))
    public void listener(Long spuId){
        //查询里面的数据
        Resp<List<SkuInfoEntity>> listResp = this.gmallPmsClient.querySkusBySpuId(spuId);
        List<SkuInfoEntity> skuInfoEntityList = listResp.getData();
        //如果集合非空再把sku转化为goods对象
        if (!CollectionUtils.isEmpty(skuInfoEntityList)) {
            //把sku转化成goods对象
            List<Goods> goodsList = skuInfoEntityList.stream().map(skuInfoEntity -> {
                Goods goods = new Goods();
                //查询搜索属性和值
                Resp<List<ProductAttrValueEntity>> attrValueResp = this.gmallPmsClient.querySearchAttrValueBySpuId(skuInfoEntity.getSpuId());
                List<ProductAttrValueEntity> attrValueEntityList = attrValueResp.getData();
                if (!CollectionUtils.isEmpty(attrValueEntityList)) {
                    List<SearchAttr> searchAttrList = attrValueEntityList.stream().map(productAttrValueEntity -> {
                        SearchAttr searchAttr = new SearchAttr();
                        searchAttr.setAttrId(productAttrValueEntity.getAttrId());
                        searchAttr.setAttrName(productAttrValueEntity.getAttrName());
                        searchAttr.setAttrValue(productAttrValueEntity.getAttrValue());

                        return searchAttr;
                    }).collect(Collectors.toList());
                    goods.setAttrs(searchAttrList);
                }

                //查询品牌
                Resp<BrandEntity> brandEntityResp = this.gmallPmsClient.queryBrandById(skuInfoEntity.getBrandId());
                BrandEntity brandEntity = brandEntityResp.getData();
                if (brandEntity != null) {
                    goods.setBrandId(skuInfoEntity.getBrandId());
                    goods.setBrandName(brandEntity.getName());
                }
                //查询分类
                Resp<CategoryEntity> categoryEntityResp = this.gmallPmsClient.queryCategoryById(skuInfoEntity.getCatalogId());
                CategoryEntity categoryEntity = categoryEntityResp.getData();
                if (categoryEntity != null) {
                    goods.setCategoryId(skuInfoEntity.getCatalogId());
                    goods.setCategoryName(categoryEntity.getName());
                }

                Resp<SpuInfoEntity> spuInfoEntity = this.gmallPmsClient.querySpuById(spuId);
                SpuInfoEntity data = spuInfoEntity.getData();
                goods.setCreateTime(data.getCreateTime());
                goods.setPic(skuInfoEntity.getSkuDefaultImg());
                goods.setPrice(skuInfoEntity.getPrice());
                goods.setSale(0L);
                goods.setSkuId(skuInfoEntity.getSkuId());

                //查询库存信息
                Resp<List<WareSkuEntity>> queryWareSkusBySkuId = this.gmallWmsClient.queryWareSkusBySkuId(skuInfoEntity.getSkuId());
                List<WareSkuEntity> wareSkuEntityList = queryWareSkusBySkuId.getData();
                if (!CollectionUtils.isEmpty(wareSkuEntityList)) {
                    //list有一个对象有库存既有货
                    boolean flag = wareSkuEntityList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0);
                    goods.setStore(flag);
                }

                goods.setTitle(skuInfoEntity.getSkuTitle());

                return goods;
            }).collect(Collectors.toList());

            this.goodsRepository.saveAll(goodsList);

        }
    }
}
