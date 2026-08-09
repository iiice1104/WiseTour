package com.wisetour.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 景区/商户 ES 索引文档
 * 对应 MySQL tb_shop 表，用于全文检索
 * 分析器：name/address 使用 ik_max_word 索引、ik_smart 检索
 */
@Data
@Document(indexName = "shop")
public class ShopDoc {

    @Id
    private Long id;

    /**
     * 景区/商户名称，ik_max_word 细粒度分词索引，ik_smart 粗粒度检索
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String name;

    /**
     * 所属类型 id
     */
    @Field(type = FieldType.Keyword)
    private Long typeId;

    /**
     * 图片，仅存储不参与检索
     */
    @Field(type = FieldType.Keyword, index = false)
    private String images;

    /**
     * 商圈/地区，精确匹配
     */
    @Field(type = FieldType.Keyword)
    private String area;

    /**
     * 详细地址，参与全文检索
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String address;

    /**
     * 经度
     */
    @Field(type = FieldType.Double)
    private Double x;

    /**
     * 纬度
     */
    @Field(type = FieldType.Double)
    private Double y;

    /**
     * 均价（分）
     */
    @Field(type = FieldType.Long)
    private Long avgPrice;

    /**
     * 销售量/入园人次
     */
    @Field(type = FieldType.Integer)
    private Integer sold;

    /**
     * 评论数量
     */
    @Field(type = FieldType.Integer)
    private Integer comments;

    /**
     * 评分（×10，避免小数），如 45 = 4.5分
     */
    @Field(type = FieldType.Integer)
    private Integer score;

    /**
     * 营业时间，例如 08:00-18:00
     */
    @Field(type = FieldType.Keyword, index = false)
    private String openHours;
}
