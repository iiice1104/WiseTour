package com.wisetour.service;

import com.wisetour.dto.Result;

/**
 * 景区全文检索 Service
 */
public interface ISearchService {

    /**
     * 搜索景区/商户
     *
     * @param key     关键词（景区名称或地址），为空时查全部
     * @param typeId  景区类型过滤，为 null 时不过滤
     * @param current 当前页码，从 1 开始
     * @return 分页搜索结果
     */
    Result searchShop(String key, Long typeId, Integer current);
}
