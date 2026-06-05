package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.OrderVoucherPublicVO;
import com.szwego.gallery.service.OrderVoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderVoucherController {

    private final OrderVoucherService orderVoucherService;

    @GetMapping("/api/order-vouchers/public/{publicCode}")
    public ApiResponse<OrderVoucherPublicVO> publicDetail(@PathVariable("publicCode") String publicCode) {
        return ApiResponse.success(orderVoucherService.publicDetail(publicCode));
    }

    @GetMapping("/api/order-vouchers/poster/{publicCode}")
    public ResponseEntity<byte[]> poster(@PathVariable("publicCode") String publicCode) throws Exception {
        byte[] content = orderVoucherService.generatePoster(publicCode);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(content.length);
        return new ResponseEntity<byte[]>(content, headers, HttpStatus.OK);
    }
}
