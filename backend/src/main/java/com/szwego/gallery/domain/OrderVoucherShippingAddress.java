package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_voucher_shipping_address")
public class OrderVoucherShippingAddress extends BaseEntity {
    @TableId
    private Long id;
    private String label;
    private String receiverName;
    private String receiverPhone;
    private String country;
    private String state;
    private String city;
    private String addressLine1;
    private String addressLine2;
    private String postalCode;
    private String remark;
    private Integer enabled;
    private Integer sort;
}
