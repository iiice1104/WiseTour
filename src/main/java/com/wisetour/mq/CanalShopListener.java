package com.wisetour.mq;

import cn.hutool.core.bean.BeanUtil;
import com.wisetour.config.KafkaConfig;
import com.wisetour.dto.CanalMessage;
import com.wisetour.entity.ShopDoc;
import com.wisetour.repository.ShopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Canal binlog → ES 同步消费者
 *
 * 架构：
 *   MySQL tb_shop 变更
 *     → Canal（伪 slave 订阅 binlog）
 *     → Kafka topic: canal.wisetour
 *     → 本监听器消费
 *     → 同步更新 ES shop 索引
 *
 * Canal 配置要点：
 *   canal.mq.flatMessage = true   // 启用 JSON 平铺格式
 *   canal.destinations = wisetour  // 实例名
 *   canal.instance.filter.regex = wisetour\\.tb_shop  // 只监听 tb_shop 表
 *
 * 注意：Canal flatMessage 里字段值均为 String，BeanUtil 会自动做类型转换。
 */
@Slf4j
@Component
public class CanalShopListener {

    /** 仅处理 tb_shop 表的变更 */
    private static final String TARGET_TABLE = "tb_shop";

    @Resource
    private ShopRepository shopRepository;

    /**
     * 消费 Canal 发布的 binlog 变更消息
     *
     * @param message Canal flatMessage 反序列化对象
     */
    @KafkaListener(topics = KafkaConfig.CANAL_TOPIC, groupId = "wisetour-canal-group")
    public void syncToEs(CanalMessage message) {
        // 1. 过滤：只处理 tb_shop 表，跳过 DDL 语句
        if (Boolean.TRUE.equals(message.getIsDdl())) {
            return;
        }
        if (!TARGET_TABLE.equals(message.getTable())) {
            return;
        }

        String type = message.getType();
        List<Map<String, Object>> dataList = message.getData();

        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        log.info("Canal 同步 tb_shop，操作类型: {}, 影响行数: {}", type, dataList.size());

        switch (type) {
            case "INSERT":
            case "UPDATE":
                // INSERT / UPDATE：将行数据转换为 ShopDoc 并批量写入 ES
                List<ShopDoc> docs = dataList.stream()
                        .map(row -> BeanUtil.toBean(row, ShopDoc.class))
                        .collect(Collectors.toList());
                shopRepository.saveAll(docs);
                log.debug("ES 索引 upsert {} 条景区数据", docs.size());
                break;

            case "DELETE":
                // DELETE：从行数据取 id，批量从 ES 删除
                List<Long> ids = dataList.stream()
                        .map(row -> Long.parseLong(String.valueOf(row.get("id"))))
                        .collect(Collectors.toList());
                shopRepository.deleteAllById(ids);
                log.debug("ES 索引删除 {} 条景区数据，ids: {}", ids.size(), ids);
                break;

            default:
                log.warn("Canal 收到未知操作类型: {}", type);
        }
    }
}
