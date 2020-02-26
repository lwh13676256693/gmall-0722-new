package com.atguigu.gmall.item.service;

import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.item.feign.GmallPmsClient;
import com.atguigu.gmall.item.feign.GmallSmsClient;
import com.atguigu.gmall.item.feign.GmallWmsClient;
import com.atguigu.gmall.item.vo.ItemVo;
import com.atguigu.gmall.pms.api.GmallPmsApi;
import com.atguigu.gmall.pms.entity.*;
import com.atguigu.gmall.pms.vo.ItemGroupVo;
import com.atguigu.gmall.sms.vo.SaleVo;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class ItemService {

    @Autowired
    private GmallPmsClient gmallPmsClient;

    @Autowired
    private GmallSmsClient gmallSmsClient;

    @Autowired
    private GmallWmsClient gmallWmsClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    public ItemVo queryItemVo(Long skuId) {
        ItemVo itemVo = new ItemVo();

        itemVo.setSkuId(skuId);
        //根据id查询sku
        CompletableFuture<Object> skuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Resp<SkuInfoEntity> skuResp = this.gmallPmsClient.querySkuById(skuId);
            SkuInfoEntity skuInfoEntity = skuResp.getData();
            if (skuInfoEntity == null) {
                return itemVo;
            }
            itemVo.setSkuTitle(skuInfoEntity.getSkuTitle());
            itemVo.setSubTitle(skuInfoEntity.getSkuSubtitle());
            itemVo.setPrice(skuInfoEntity.getPrice());
            itemVo.setWeight(skuInfoEntity.getWeight());
            itemVo.setSpuId(skuInfoEntity.getSpuId());
            return skuInfoEntity;
        }, threadPoolExecutor);

        //这个spuid是要从sku查询出来之后才可以用，所以要在这个后面
        CompletableFuture<Void> spuCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            //根据sku的spuid查询spu
            Resp<SpuInfoEntity> spuResp = this.gmallPmsClient.querySpuById(((SkuInfoEntity) sku).getSpuId());
            SpuInfoEntity spuInfoEntity = spuResp.getData();
            if (spuInfoEntity != null) {
                itemVo.setSpuName(spuInfoEntity.getSpuName());
            }
        }, threadPoolExecutor);

        //skuid查询出来图片
        CompletableFuture<Void> imageCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<SkuImagesEntity>> skuImagesResp = this.gmallPmsClient.querySkuImagesBySkuId(skuId);
            List<SkuImagesEntity> skuImagesEntityList = skuImagesResp.getData();
            itemVo.setPics(skuImagesEntityList);
        }, threadPoolExecutor);

        //根据sku中的brandId和CategoryId查询品牌和分类
        CompletableFuture<Void> brandCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<BrandEntity> brandResp = this.gmallPmsClient.queryBrandById(((SkuInfoEntity) sku).getBrandId());
            BrandEntity brandEntity = brandResp.getData();
            itemVo.setBrandEntity(brandEntity);
        }, threadPoolExecutor);


        CompletableFuture<Void> categoryCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<CategoryEntity> categoryEntityResp = this.gmallPmsClient.queryCategoryById(((SkuInfoEntity) sku).getCatalogId());
            CategoryEntity categoryEntity = categoryEntityResp.getData();
            itemVo.setCategoryEntity(categoryEntity);
        }, threadPoolExecutor);

        //根据skuid查询营销信息
        CompletableFuture<Void> saleCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<SaleVo>> saleResp = this.gmallSmsClient.querySalesBySkuId(skuId);
            List<SaleVo> saleVoList = saleResp.getData();
            itemVo.setSales(saleVoList);
        }, threadPoolExecutor);

        //根据skuid查询库存
        CompletableFuture<Void> wareCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<WareSkuEntity>> wareResp = this.gmallWmsClient.queryWareSkusBySkuId(skuId);
            List<WareSkuEntity> wareList = wareResp.getData();
            itemVo.setStore(wareList.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
        }, threadPoolExecutor);

        //根据spuid查询所有的skuid，再去查询所有的销售属性
        CompletableFuture<Void> saleAttrsCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<List<SkuSaleAttrValueEntity>> saleAttrsResp = this.gmallPmsClient.querySkuSaleAttrValuesBySpuId(((SkuInfoEntity) sku).getSpuId());
            List<SkuSaleAttrValueEntity> saleAttrValueEntityList = saleAttrsResp.getData();
            itemVo.setSaleAttrs(saleAttrValueEntityList);
        }, threadPoolExecutor);

        //根据spuid查询海报
        CompletableFuture<Void> descCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<SpuInfoDescEntity> spuInfoDescEntityResp = this.gmallPmsClient.querySpuDescBySpuId(((SkuInfoEntity) sku).getSpuId());
            SpuInfoDescEntity descEntity = spuInfoDescEntityResp.getData();
            if (descEntity != null) {
                String decript = descEntity.getDecript();
                String[] split = StringUtils.split(decript, ",");
                itemVo.setImages(Arrays.asList(split));
            }
        }, threadPoolExecutor);


        CompletableFuture<Void> itemCompletableFuture = skuCompletableFuture.thenAcceptAsync(sku -> {
            Resp<List<ItemGroupVo>> itemResp = this.gmallPmsClient.queryItemGroupVoByCidAndSpuId(((SkuInfoEntity) sku).getCatalogId(), ((SkuInfoEntity) sku).getSpuId());
            List<ItemGroupVo> itemGroupVoList = itemResp.getData();
            //根据spuid和cateid查询组及组下的值
            itemVo.setGroups(itemGroupVoList);
        }, threadPoolExecutor);

        CompletableFuture.allOf(spuCompletableFuture, imageCompletableFuture, brandCompletableFuture,
                categoryCompletableFuture, saleCompletableFuture, wareCompletableFuture, saleAttrsCompletableFuture,
                descCompletableFuture, itemCompletableFuture).join();


        return itemVo;
    }

    public static void main(String[] args) {
        CompletableFuture.supplyAsync(() -> {
            System.out.println("runAsync");
//            int a=1/0;
            return "hhhh";
        }).whenComplete((t, u) -> {
            System.out.println("when t" + t);
            System.out.println("when u" + u);
        }).exceptionally(t -> {
            System.out.println("exce" + t);
            return "hello ex";
        }).handleAsync((t, u) -> {
            System.out.println("handle的t" + t);
            System.out.println("handle的u" + u);
            return "handle";
        });
    }
}
