package com.wisetour.repository;

import com.wisetour.entity.ShopDoc;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * 景区 ES 仓库
 * 继承 ElasticsearchRepository 获得基础 CRUD：
 *   save / saveAll / findById / deleteById 等
 * 复杂检索（多字段 MultiMatch、过滤、排序）通过 SearchServiceImpl
 * 注入 ElasticsearchRestTemplate 手写 NativeSearchQuery 实现
 */
public interface ShopRepository extends ElasticsearchRepository<ShopDoc, Long> {
}
