package com.atguigu.gmall.search.service;

import com.atguigu.gmall.search.pojo.Goods;
import com.atguigu.gmall.search.pojo.SearchParam;
import com.atguigu.gmall.search.pojo.SearchResponseAttrVO;
import com.atguigu.gmall.search.pojo.SearchResponseVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.*;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private static final Gson gson = new Gson();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private RestHighLevelClient restHighLevelClient;

    public SearchResponseVO search(SearchParam searchParam) throws IOException {

        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        SearchResponse response = this.restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        SearchResponseVO searchResponseVO = this.parseSearchResult(response);
        searchResponseVO.setPageSize(searchParam.getPageSize());
        searchResponseVO.setPageNum(searchParam.getPageNum());

        return searchResponseVO;

    }

    private SearchResponseVO parseSearchResult(SearchResponse response) throws JsonProcessingException {
        SearchResponseVO responseVO = new SearchResponseVO();
        //获取命中总记录数
        SearchHits hits = response.getHits();
        responseVO.setTotal(hits.getTotalHits());
        //品牌的聚合结果
        SearchResponseAttrVO brand = new SearchResponseAttrVO();
        brand.setName("品牌");

        Map<String, Aggregation> stringAggregationMap = response.getAggregations().asMap();
        ParsedLongTerms brandIdAgg = (ParsedLongTerms) stringAggregationMap.get("brandIdAgg");
        List<String> brandValues = brandIdAgg.getBuckets().stream().map(bucket -> {
            Map<String, String> map = new HashMap<>();
            map.put("id", bucket.getKeyAsString());

            Map<String, Aggregation> brandIdSubMap = bucket.getAggregations().asMap();
            ParsedStringTerms brandNameAgg = (ParsedStringTerms) brandIdSubMap.get("brandNameAgg");
            String brandName = brandNameAgg.getBuckets().get(0).getKeyAsString();
            map.put("name", brandName);
            try {
                return objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());
        brand.setValue(brandValues);
        responseVO.setBrand(brand);

        ParsedLongTerms categoryIdAgg = (ParsedLongTerms) stringAggregationMap.get("categoryIdAgg");
        List<String> categoryValues = categoryIdAgg.getBuckets().stream().map(bucket -> {
            Map<String, String> map = new HashMap<>();
            map.put("id", bucket.getKeyAsString());

            Map<String, Aggregation> categorySubMap = bucket.getAggregations().asMap();
            ParsedStringTerms categoryNameAgg = (ParsedStringTerms) categorySubMap.get("categoryNameAgg");
            String categoryName = categoryNameAgg.getBuckets().get(0).getKeyAsString();
            map.put("name", categoryName);
            try {
                return objectMapper.writeValueAsString(map);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
            return null;
        }).collect(Collectors.toList());

        SearchResponseAttrVO category = new SearchResponseAttrVO();
        category.setName("分类");
        category.setValue(categoryValues);
        responseVO.setCatelog(category);

        //解析查询列表
        List<Goods> goodsList = new ArrayList<>();
        SearchHit[] subHits = hits.getHits();
        for (SearchHit subHit : subHits) {
//            Goods goods = gson.fromJson(subHit.getSourceAsString(), Goods.class);
            Goods goods = objectMapper.readValue(subHit.getSourceAsString(), new TypeReference<Goods>() {
            });
            goods.setTitle(subHit.getHighlightFields().get("title").getFragments()[0].toString());
            goodsList.add(goods);
        }
        responseVO.setProducts(goodsList);

        //规格参数
        ParsedNested attrAgg = (ParsedNested)stringAggregationMap.get("attrAgg");
        ParsedLongTerms attrIdAgg = (ParsedLongTerms)attrAgg.getAggregations().get("attrIdAgg");
        List<Terms.Bucket> buckets = (List<Terms.Bucket>)attrIdAgg.getBuckets();
        if (!CollectionUtils.isEmpty(buckets)) {
            List<SearchResponseAttrVO> searchResponseAttrVOS = buckets.stream().map(bucket -> {
                SearchResponseAttrVO responseAttrVO = new SearchResponseAttrVO();
                //设置规格参数id
                responseAttrVO.setProductAttributeId(bucket.getKeyAsNumber().longValue());
                //设置规格参数名
                List<Terms.Bucket> nameBucket = (List<Terms.Bucket>)
                        ((ParsedStringTerms)(bucket.getAggregations().get("attrNameAgg"))).getBuckets();
                responseAttrVO.setName(nameBucket.get(0).getKeyAsString());
                //设置值规格参数值的列表
                List<Terms.Bucket> valueBucket = (List<Terms.Bucket>)
                        ((ParsedStringTerms)(bucket.getAggregations().get("attrValueAgg"))).getBuckets();
                List<String> values = valueBucket.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());
                responseAttrVO.setValue(values);

                return responseAttrVO;
            }).collect(Collectors.toList());
            responseVO.setAttrs(searchResponseAttrVOS);
        }




        return responseVO;
    }

    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        String keyword = searchParam.getKeyword();
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }

        //查询条件构造器
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        //构建查询条件和过滤条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchQuery("title", keyword).operator(Operator.AND));
        //构造过滤条件
        String[] brand = searchParam.getBrand();
        if (brand != null && brand.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", brand));
        }
        //构建分类
        String[] catelog3 = searchParam.getCatelog3();
        if (catelog3 != null && catelog3.length != 0) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("categoryId", catelog3));
        }
        //构建规格属性嵌套过滤
        String[] props = searchParam.getProps();
        if (props != null && props.length != 0) {
            for (String prop : props) {
                String[] split = StringUtils.split(prop, ":");
                if (split == null || split.length != 2) {
                    continue;
                }
                //以-分割处理出attrValues
                String[] attrValues = StringUtils.split(split[1], "-");

                //在构建嵌套查询
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

                BoolQueryBuilder subQuery = QueryBuilders.boolQuery();

                subQuery.must(QueryBuilders.termsQuery("attrs.attrId", split[0]));
                subQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));

                boolQuery.must(QueryBuilders.nestedQuery("attrs", subQuery, ScoreMode.None));

                boolQueryBuilder.filter(boolQuery);
            }
        }
        //价格区间过滤
        RangeQueryBuilder rangeQueryBuilder = QueryBuilders.rangeQuery("price");

        Integer priceTo = searchParam.getPriceTo();
        Integer priceFrom = searchParam.getPriceFrom();
        if (priceFrom != null) {
            rangeQueryBuilder.gte(priceFrom);
        }
        if (priceTo != null) {
            rangeQueryBuilder.lte(priceTo);
        }

        boolQueryBuilder.filter(rangeQueryBuilder);

        sourceBuilder.query(boolQueryBuilder);

        //构建分页
        Integer pageNum = searchParam.getPageNum();
        Integer pageSize = searchParam.getPageSize();

        sourceBuilder.from((pageNum - 1) * pageSize).size(pageSize);

        //构建排序
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)) {
            String[] split = StringUtils.split(order, ":");
            if (split != null && split.length == 2) {
                String field = null;
                switch (split[0]) {
                    case "1":
                        field = "sale";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                sourceBuilder.sort(field, StringUtils.equals("asc", split[1]) ? SortOrder.ASC : SortOrder.DESC);
            }
        }

        //构建高亮
        sourceBuilder.highlighter(new HighlightBuilder().field("title").preTags("<em>").postTags("</em>"));

        //构建聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("brandIdAgg").field("brandId")
                .subAggregation(AggregationBuilders.terms("brandNameAgg").field("brandName")));
        //分类聚合
        sourceBuilder.aggregation(AggregationBuilders.terms("categoryIdAgg").field("categoryId")
                .subAggregation(AggregationBuilders.terms("categoryNameAgg").field("categoryName")));
        //搜索的规格属性聚合
        sourceBuilder.aggregation(AggregationBuilders.nested("attrAgg", "attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        System.out.println(sourceBuilder.toString());

        //结果集的过滤
        sourceBuilder.fetchSource(new String[]{"skuId","pic","title","price"},null);

        //查询参数
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(sourceBuilder);

        return searchRequest;
    }
}
