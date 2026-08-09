package com.wisetour.controller;

import com.wisetour.dto.Result;
import com.wisetour.service.ISearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 景区全文搜索接口
 */
@RestController
@RequestMapping("/search")
public class SearchController {

    @Resource
    private ISearchService searchService;

    /**
     * 搜索景区
     *
     * @param key     搜索关键词，支持景区名称和地址的模糊匹配（IK 分词 + 拼音）
     * @param typeId  按景区类型过滤，不传则不过滤
     * @param current 页码，从 1 开始，默认第 1 页
     * @return 分页结果，含 data（景区列表）和 total（总数）
     *
     * 示例请求：
     *   GET /search/shop?key=故宫&typeId=1&current=1
     *   GET /search/shop?key=gugong          （拼音搜索，需 pinyin 分词插件）
     *   GET /search/shop?typeId=2&current=2  （按类型浏览，按评分排序）
     */
    @GetMapping("/shop")
    public Result searchShop(
            @RequestParam(required = false) String key,
            @RequestParam(required = false) Long typeId,
            @RequestParam(defaultValue = "1") Integer current) {
        return searchService.searchShop(key, typeId, current);
    }
}
