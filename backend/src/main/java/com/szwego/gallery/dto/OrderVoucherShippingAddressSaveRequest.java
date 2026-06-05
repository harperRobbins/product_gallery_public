package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class OrderVoucherShippingAddressSaveRequest {
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
