package com.wisetour.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Canal flatMessage 格式反序列化 DTO
 * Canal 以伪 slave 身份订阅 MySQL binlog，解析后发布到 Kafka。
 * 开启 canal.mq.flatMessage=true 时消息为 JSON 格式，字段均为 String。
 *
 * 典型消息体：
 * {
 *   "database": "wisetour",
 *   "table":    "tb_shop",
 *   "type":     "INSERT",   // INSERT / UPDATE / DELETE
 *   "isDdl":    false,
 *   "data":     [{"id":"1","name":"故宫景区",...}],
 *   "old":      null        // UPDATE 时记录变更前的字段值
 * }
 */
@Data
public class CanalMessage {

    /** 数据库名 */
    private String database;

    /** 表名 */
    private String table;

    /** 操作类型：INSERT / UPDATE / DELETE */
    private String type;

    /** 是否 DDL 语句（建表/改表等），DDL 消息直接忽略 */
    private Boolean isDdl;

    /**
     * 变更后的行数据列表（一次操作可涉及多行）
     * 字段值均为 String，需按需转型
     */
    private List<Map<String, Object>> data;

    /**
     * UPDATE 前的旧字段值，仅 UPDATE 操作非空
     */
    private List<Map<String, Object>> old;
}
