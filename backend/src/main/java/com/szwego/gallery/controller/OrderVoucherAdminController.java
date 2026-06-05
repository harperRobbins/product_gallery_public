package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.dto.OrderVoucherAdminVO;
import com.szwego.gallery.dto.OrderVoucherCurrencyConvertRequest;
import com.szwego.gallery.dto.OrderVoucherDetailVO;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodSaveRequest;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodVO;
import com.szwego.gallery.dto.OrderVoucherSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShareVO;
import com.szwego.gallery.dto.OrderVoucherShippingAddressSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShippingAddressVO;
import com.szwego.gallery.service.OrderVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrderVoucherAdminController {

    private final OrderVoucherService orderVoucherService;

    @GetMapping("/api/admin/order-vouchers")
    public ApiResponse<PageResponse<OrderVoucherAdminVO>> page(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                               @RequestParam(value = "size", defaultValue = "10") Long size,
                                                               @RequestParam(value = "keyword", required = false) String keyword,
                                                               @RequestParam(value = "status", required = false) String status,
                                                               @RequestParam(value = "paymentStatus", required = false) String paymentStatus) {
        return ApiResponse.success(orderVoucherService.page(page, size, keyword, status, paymentStatus));
    }

    @GetMapping("/api/admin/order-vouchers/{id}")
    public ApiResponse<OrderVoucherDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(orderVoucherService.detail(id));
    }

    @PostMapping("/api/admin/order-vouchers")
    public ApiResponse<Long> create(@Validated @RequestBody OrderVoucherSaveRequest request) {
        return ApiResponse.success("订单凭证创建成功", orderVoucherService.create(request));
    }

    @PutMapping("/api/admin/order-vouchers/{id}")
    public ApiResponse<Long> update(@PathVariable("id") Long id,
                                    @Validated @RequestBody OrderVoucherSaveRequest request) {
        return ApiResponse.success("订单凭证更新成功", orderVoucherService.update(id, request));
    }

    @PostMapping("/api/admin/order-vouchers/{id}/void")
    public ApiResponse<Void> voidVoucher(@PathVariable("id") Long id) {
        orderVoucherService.voidVoucher(id);
        return ApiResponse.success("订单凭证已作废", null);
    }

    @PostMapping("/api/admin/order-vouchers/{id}/convert-currency")
    public ApiResponse<OrderVoucherDetailVO> convertCurrency(@PathVariable("id") Long id,
                                                             @Validated @RequestBody OrderVoucherCurrencyConvertRequest request) {
        return ApiResponse.success("账单币种转换成功", orderVoucherService.convertCurrency(id, request));
    }

    @PostMapping("/api/admin/order-vouchers/{id}/share")
    public ApiResponse<OrderVoucherShareVO> share(@PathVariable("id") Long id) {
        return ApiResponse.success(orderVoucherService.share(id));
    }

    @GetMapping("/api/admin/order-vouchers/shipping-addresses")
    public ApiResponse<List<OrderVoucherShippingAddressVO>> listShippingAddresses() {
        return ApiResponse.success(orderVoucherService.listShippingAddresses());
    }

    @PostMapping("/api/admin/order-vouchers/shipping-addresses")
    public ApiResponse<Long> createShippingAddress(@RequestBody OrderVoucherShippingAddressSaveRequest request) {
        return ApiResponse.success("收货地址创建成功", orderVoucherService.createShippingAddress(request));
    }

    @PutMapping("/api/admin/order-vouchers/shipping-addresses/{id}")
    public ApiResponse<Long> updateShippingAddress(@PathVariable("id") Long id,
                                                   @RequestBody OrderVoucherShippingAddressSaveRequest request) {
        return ApiResponse.success("收货地址更新成功", orderVoucherService.updateShippingAddress(id, request));
    }

    @DeleteMapping("/api/admin/order-vouchers/shipping-addresses/{id}")
    public ApiResponse<Void> deleteShippingAddress(@PathVariable("id") Long id) {
        orderVoucherService.deleteShippingAddress(id);
        return ApiResponse.success("收货地址已删除", null);
    }

    @GetMapping("/api/admin/order-vouchers/payment-methods")
    public ApiResponse<List<OrderVoucherPaymentMethodVO>> listPaymentMethods() {
        return ApiResponse.success(orderVoucherService.listPaymentMethods());
    }

    @PostMapping("/api/admin/order-vouchers/payment-methods")
    public ApiResponse<Long> createPaymentMethod(@RequestBody OrderVoucherPaymentMethodSaveRequest request) {
        return ApiResponse.success("支付方式创建成功", orderVoucherService.createPaymentMethod(request));
    }

    @PutMapping("/api/admin/order-vouchers/payment-methods/{id}")
    public ApiResponse<Long> updatePaymentMethod(@PathVariable("id") Long id,
                                                 @RequestBody OrderVoucherPaymentMethodSaveRequest request) {
        return ApiResponse.success("支付方式更新成功", orderVoucherService.updatePaymentMethod(id, request));
    }

    @DeleteMapping("/api/admin/order-vouchers/payment-methods/{id}")
    public ApiResponse<Void> deletePaymentMethod(@PathVariable("id") Long id) {
        orderVoucherService.deletePaymentMethod(id);
        return ApiResponse.success("支付方式已删除", null);
    }
}
