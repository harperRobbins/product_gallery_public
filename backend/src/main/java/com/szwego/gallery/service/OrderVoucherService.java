package com.szwego.gallery.service;

import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.dto.OrderVoucherAdminVO;
import com.szwego.gallery.dto.OrderVoucherCurrencyConvertRequest;
import com.szwego.gallery.dto.OrderVoucherDetailVO;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodSaveRequest;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodVO;
import com.szwego.gallery.dto.OrderVoucherPublicVO;
import com.szwego.gallery.dto.OrderVoucherSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShareVO;
import com.szwego.gallery.dto.OrderVoucherShippingAddressSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShippingAddressVO;

import java.util.List;

public interface OrderVoucherService {
    PageResponse<OrderVoucherAdminVO> page(Long page, Long size, String keyword, String status, String paymentStatus);

    OrderVoucherDetailVO detail(Long id);

    Long create(OrderVoucherSaveRequest request);

    Long update(Long id, OrderVoucherSaveRequest request);

    void voidVoucher(Long id);

    OrderVoucherDetailVO convertCurrency(Long id, OrderVoucherCurrencyConvertRequest request);

    OrderVoucherShareVO share(Long id);

    OrderVoucherPublicVO publicDetail(String publicCode);

    byte[] generatePoster(String publicCode) throws Exception;

    List<OrderVoucherShippingAddressVO> listShippingAddresses();

    Long createShippingAddress(OrderVoucherShippingAddressSaveRequest request);

    Long updateShippingAddress(Long id, OrderVoucherShippingAddressSaveRequest request);

    void deleteShippingAddress(Long id);

    List<OrderVoucherPaymentMethodVO> listPaymentMethods();

    Long createPaymentMethod(OrderVoucherPaymentMethodSaveRequest request);

    Long updatePaymentMethod(Long id, OrderVoucherPaymentMethodSaveRequest request);

    void deletePaymentMethod(Long id);
}
