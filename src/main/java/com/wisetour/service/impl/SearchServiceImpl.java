package com.wisetour.service.impl;

import com.wisetour.dto.Result;
import com.wisetour.entity.ShopDoc;
import com.wisetour.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 景区全文检索实现
 *
 * 检索逻辑分两层：
 *  1. 关键词匹配（must）：MultiMatch 同时搜索 name 和 address 字段，
 *     ES 会综合两字段的分数取最高值（best_fields 策略）
 *  2. 类型过滤（filter）：typeId 精确匹配，走 keyword 字段，不影响相关性评分
 *
 * 排序策略：
 *  - 有关键词时：按相关性评分降序（ES 默认 _score）
 *  - 无关键词（全量浏览）时：按评分 score 降序
 *
 * 分页：每页固定 5 条，current 从 1 开始
 */
@Slf4j
@Service
public class SearchServiceImpl implements ISearchService {

    private static final int PAGE_SIZE = 5;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public Result searchShop(String key, Long typeId, Integer current) {
        // 1. 构建 bool 查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 1.1 关键词全文检索：同时匹配 name 和 address，IK 分词器分析
        if (StringUtils.hasText(key)) {
            boolQuery.must(
                QueryBuilders.multiMatchQuery(key, "name", "address")
            );
        } else {
            // 无关键词时匹配所有文档
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        // 1.2 景区类型精确过滤（filter 不计入评分，性能更高）
        if (typeId != null && typeId > 0) {
            boolQuery.filter(QueryBuilders.termQuery("typeId", typeId));
        }

        // 2. 构建完整查询，含排序和分页
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                .withQuery(boolQuery)
                .withPageable(PageRequest.of(current - 1, PAGE_SIZE));

        // 无关键词时按景区评分降序排列，有关键词时使用 ES 相关性默认排序
        if (!StringUtils.hasText(key)) {
            queryBuilder.withSort(SortBuilders.fieldSort("score").order(SortOrder.DESC));
        }

        NativeSearchQuery searchQuery = queryBuilder.build();

        // 3. 执行搜索
        SearchHits<ShopDoc> searchHits = elasticsearchRestTemplate.search(searchQuery, ShopDoc.class);
        long total = searchHits.getTotalHits();

        List<ShopDoc> results = searchHits.getSearchHits()
                .stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        log.debug("ES 搜索景区：key={}, typeId={}, page={}, hits={}", key, typeId, current, total);
        return Result.ok(results, total);
    }
}
