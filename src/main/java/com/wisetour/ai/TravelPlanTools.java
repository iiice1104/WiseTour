package com.wisetour.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wisetour.entity.Shop;
import com.wisetour.entity.SeckillVoucher;
import com.wisetour.service.IShopService;
import com.wisetour.service.ISeckillVoucherService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * LangChain4j 工具集：LLM 可自主调用的景区服务工具
 * 每个 @Tool 方法对应一个 Agent 可使用的"技能"
 */
@Slf4j
@org.springframework.stereotype.Component
public class TravelPlanTools {

    @Resource
    private IShopService shopService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Redis GEO key 格式：shop:geo:{typeId}，0 表示全类型
    private static final String GEO_KEY_PREFIX = "shop:geo:";

    /**
     * 工具①：关键词搜索景区
     * LLM 会在需要查找具体景区信息时调用此工具
     */
    @Tool("根据关键词搜索景区或周边商户，返回名称、评分、人均价格、地址等信息。" +
          "关键词可以是景区名称、地区、类型（如历史文化、自然风光、古镇）等。")
    public String searchScenicSpots(
            @P("搜索关键词") String keywords,
            @P("景区类型ID，不限类型时传0") Integer typeId) {

        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .like(Shop::getName, keywords)
                .orderByDesc(Shop::getScore)
                .last("LIMIT 5");

        if (typeId != null && typeId > 0) {
            wrapper.eq(Shop::getTypeId, typeId);
        }

        List<Shop> shops = shopService.list(wrapper);

        if (shops == null || shops.isEmpty()) {
            return "未找到与\"" + keywords + "\"相关的景区信息。";
        }

        StringBuilder sb = new StringBuilder("搜索到以下景区：\n");
        for (int i = 0; i < shops.size(); i++) {
            Shop s = shops.get(i);
            sb.append(i + 1).append(". 【").append(s.getName()).append("】\n");
            sb.append("   地址：").append(s.getArea()).append(" ").append(s.getAddress()).append("\n");
            sb.append("   评分：").append(s.getScore() != null ? s.getScore() / 10.0 : "暂无").append(" 分\n");
            sb.append("   人均：").append(s.getAvgPrice() != null ? s.getAvgPrice() + " 元" : "暂无").append("\n");
            sb.append("   营业时间：").append(s.getOpenHours() != null ? s.getOpenHours() : "暂无").append("\n");
            sb.append("   景区ID：").append(s.getId()).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * 工具②：查询景区门票信息
     * LLM 会在需要了解票价或判断门票是否充足时调用
     */
    @Tool("查询指定景区的秒杀门票信息，包括票价、库存数量、有效时间段。" +
          "需要景区ID作为参数，景区ID可从搜索结果中获取。")
    public String getTicketInfo(@P("景区ID（tb_shop 表主键）") Long scenicId) {
        List<SeckillVoucher> vouchers = seckillVoucherService.list(
                new LambdaQueryWrapper<SeckillVoucher>()
                        .eq(SeckillVoucher::getVoucherId, scenicId)
                        .ge(SeckillVoucher::getEndTime, LocalDateTime.now())
                        .gt(SeckillVoucher::getStock, 0)
        );

        if (vouchers == null || vouchers.isEmpty()) {
            return "景区ID " + scenicId + " 当前无可用门票或秒杀活动已结束。";
        }

        StringBuilder sb = new StringBuilder("门票信息如下：\n");
        for (SeckillVoucher v : vouchers) {
            sb.append("- 优惠券ID：").append(v.getVoucherId()).append("\n");
            sb.append("  剩余库存：").append(v.getStock()).append(" 张\n");
            sb.append("  活动时间：")
              .append(v.getBeginTime()).append(" 至 ").append(v.getEndTime()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 工具③：查询附近景区
     * LLM 会在用户提供当前位置、想了解附近有什么景区时调用
     */
    @Tool("根据用户当前经纬度，查询附近一定范围内的景区，按距离由近到远排序。" +
          "经度范围 -180~180，纬度范围 -90~90。")
    public String getNearbyScenicSpots(
            @P("用户当前经度，例如北京故宫：116.4") double longitude,
            @P("用户当前纬度，例如北京故宫：39.9") double latitude,
            @P("搜索半径（千米），建议 5~20") int radiusKm) {

        // 遍历常见类型（1~10），实际项目中可从 ShopType 表动态获取
        StringBuilder sb = new StringBuilder();
        int found = 0;

        for (int typeId = 1; typeId <= 10 && found < 5; typeId++) {
            String geoKey = GEO_KEY_PREFIX + typeId;
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    stringRedisTemplate.opsForGeo().radius(
                            geoKey,
                            new Point(longitude, latitude),
                            new Distance(radiusKm, Metrics.KILOMETERS),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance()
                                    .sortAscending()
                                    .limit(3)
                    );

            if (results == null) continue;

            results.getContent().forEach(result -> {
                String shopIdStr = result.getContent().getName();
                double distance = result.getDistance().getValue();
                sb.append("- 景区ID：").append(shopIdStr)
                  .append("，距您约 ").append(String.format("%.1f", distance)).append(" km\n");
            });
            found += results.getContent().size();
        }

        if (sb.length() == 0) {
            return "在您附近 " + radiusKm + "km 范围内暂未找到景区信息。";
        }

        return "附近景区（按距离排序）：\n" + sb +
               "\n提示：可用景区ID调用门票查询工具获取票务信息。";
    }

    /**
     * 工具④：获取热门景区推荐
     * LLM 会在用户没有明确目的地、想要推荐时调用
     */
    @Tool("获取平台本周热门景区排行榜，返回人气最高的景区列表，适合在用户没有明确目的地时推荐。")
    public String getHotScenicSpots() {
        // ZSet key：blog:hot 或 shop:hot:week，score 为访问量/点赞数
        java.util.Set<String> topIds = stringRedisTemplate.opsForZSet()
                .reverseRange("shop:hot:week", 0, 4);

        if (topIds == null || topIds.isEmpty()) {
            // 降级：直接按评分排序查 DB
            List<Shop> shops = shopService.list(
                    new LambdaQueryWrapper<Shop>()
                            .orderByDesc(Shop::getScore)
                            .last("LIMIT 5")
            );
            if (shops.isEmpty()) return "暂无热门景区数据。";

            StringBuilder sb = new StringBuilder("本周推荐景区（按评分）：\n");
            for (int i = 0; i < shops.size(); i++) {
                Shop s = shops.get(i);
                sb.append(i + 1).append(". ").append(s.getName())
                  .append("（评分：").append(s.getScore() != null ? s.getScore() / 10.0 : "暂无")
                  .append("，人均：").append(s.getAvgPrice() != null ? s.getAvgPrice() + "元" : "暂无")
                  .append("，景区ID：").append(s.getId()).append("）\n");
            }
            return sb.toString();
        }

        List<Long> ids = topIds.stream().map(Long::parseLong).collect(Collectors.toList());
        List<Shop> shops = shopService.listByIds(ids);

        StringBuilder sb = new StringBuilder("本周热门景区排行：\n");
        for (int i = 0; i < shops.size(); i++) {
            Shop s = shops.get(i);
            sb.append(i + 1).append(". ").append(s.getName())
              .append("（评分：").append(s.getScore() != null ? s.getScore() / 10.0 : "暂无")
              .append("，景区ID：").append(s.getId()).append("）\n");
        }
        return sb.toString();
    }
}
