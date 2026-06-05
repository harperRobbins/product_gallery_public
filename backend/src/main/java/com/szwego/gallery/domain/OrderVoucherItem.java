package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_voucher_item")
public class OrderVoucherItem extends BaseEntity {
    @TableId
    private Long id;
    private Long voucherId;
    private String sourceType;
    private Long productId;
    private String productTitleSnapshot;
    private String productSkuSnapshot;
    private String coverImageSnapshot;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
    private String remark;
    private Integer sort;
}
