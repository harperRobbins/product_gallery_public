package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_voucher_payment_method")
public class OrderVoucherPaymentMethod extends BaseEntity {
    @TableId
    private Long id;
    private String name;
    private String type;
    private String description;
    private String accountValue;
    private String bankFieldsJson;
    private Integer enabled;
    private Integer sort;
}
