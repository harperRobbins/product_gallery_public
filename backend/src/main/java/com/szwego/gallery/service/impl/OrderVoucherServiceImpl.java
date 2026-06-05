package com.szwego.gallery.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.domain.OrderVoucherPaymentMethod;
import com.szwego.gallery.domain.OrderVoucherShippingAddress;
import com.szwego.gallery.domain.OrderVoucher;
import com.szwego.gallery.domain.OrderVoucherItem;
import com.szwego.gallery.dto.OrderVoucherAdminVO;
import com.szwego.gallery.dto.OrderVoucherBankFieldDTO;
import com.szwego.gallery.dto.OrderVoucherCurrencyConvertRequest;
import com.szwego.gallery.dto.OrderVoucherDetailVO;
import com.szwego.gallery.dto.OrderVoucherItemRequest;
import com.szwego.gallery.dto.OrderVoucherItemVO;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodSaveRequest;
import com.szwego.gallery.dto.OrderVoucherPaymentMethodVO;
import com.szwego.gallery.dto.OrderVoucherPaymentSelectionRequest;
import com.szwego.gallery.dto.OrderVoucherPaymentSelectionVO;
import com.szwego.gallery.dto.OrderVoucherPublicVO;
import com.szwego.gallery.dto.OrderVoucherSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShareVO;
import com.szwego.gallery.dto.OrderVoucherShippingAddressSaveRequest;
import com.szwego.gallery.dto.OrderVoucherShippingAddressVO;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ShopContactItemDTO;
import com.szwego.gallery.dto.ShopProfileVO;
import com.szwego.gallery.dto.CurrencyRateSnapshotVO;
import com.szwego.gallery.mapper.OrderVoucherItemMapper;
import com.szwego.gallery.mapper.OrderVoucherMapper;
import com.szwego.gallery.mapper.OrderVoucherPaymentMethodMapper;
import com.szwego.gallery.mapper.OrderVoucherShippingAddressMapper;
import com.szwego.gallery.service.CurrencyRateService;
import com.szwego.gallery.service.OrderVoucherService;
import com.szwego.gallery.service.ProductService;
import com.szwego.gallery.service.ShopProfileService;
import com.szwego.gallery.util.LanguageUtil;
import com.szwego.gallery.util.PageUtil;
import com.szwego.gallery.util.QrCodeUtil;
import com.szwego.gallery.util.ShortCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderVoucherServiceImpl implements OrderVoucherService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_VOID = "VOID";

    private static final String PAYMENT_UNPAID = "UNPAID";
    private static final String PAYMENT_PARTIAL = "PARTIAL";
    private static final String PAYMENT_PAID = "PAID";
    private static final String PAYMENT_METHOD_PAYPAL_TRANSFER = "PAYPAL_TRANSFER";
    private static final String PAYMENT_METHOD_PAYPAL_BILL = "PAYPAL_BILL";
    private static final String PAYMENT_METHOD_CREDIT_CARD_LINK = "CREDIT_CARD_LINK";
    private static final String PAYMENT_METHOD_BANK_TRANSFER = "BANK_TRANSFER";

    private volatile boolean schemaReady = false;

    private final OrderVoucherMapper orderVoucherMapper;
    private final OrderVoucherItemMapper orderVoucherItemMapper;
    private final OrderVoucherShippingAddressMapper orderVoucherShippingAddressMapper;
    private final OrderVoucherPaymentMethodMapper orderVoucherPaymentMethodMapper;
    private final ProductService productService;
    private final ShopProfileService shopProfileService;
    private final CurrencyRateService currencyRateService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.share.base-url:http://localhost:8080}")
    private String shareBaseUrl;

    @PostConstruct
    public void init() {
        ensureTable();
        schemaReady = true;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrderVoucherAdminVO> page(Long page, Long size, String keyword, String status, String paymentStatus) {
        ensureSchemaReady();
        long safePage = page == null || page < 1 ? 1 : page;
        long safeSize = size == null || size < 1 ? 10 : size;

        Page<OrderVoucher> query = new Page<OrderVoucher>(safePage, safeSize);
        LambdaQueryWrapper<OrderVoucher> wrapper = new LambdaQueryWrapper<OrderVoucher>()
                .orderByDesc(OrderVoucher::getUpdateTime)
                .orderByDesc(OrderVoucher::getId);
        if (!isBlank(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(OrderVoucher::getVoucherNo, kw)
                    .or().like(OrderVoucher::getCustomerName, kw)
                    .or().like(OrderVoucher::getCustomerContactValue, kw));
        }
        if (!isBlank(status)) {
            wrapper.eq(OrderVoucher::getStatus, normalizeStatus(status, null));
        }
        if (!isBlank(paymentStatus)) {
            wrapper.eq(OrderVoucher::getPaymentStatus, normalizePaymentStatus(paymentStatus));
        }
        Page<OrderVoucher> result = orderVoucherMapper.selectPage(query, wrapper);
        List<OrderVoucher> vouchers = result.getRecords();
        Map<Long, List<OrderVoucherItem>> itemMap = loadItemMap(vouchers.stream().map(OrderVoucher::getId).collect(Collectors.toList()));
        for (OrderVoucher voucher : vouchers) {
            if (voucher == null) {
                continue;
            }
            reconcileVoucherAmounts(voucher, itemMap.get(voucher.getId()), false);
        }

        Page<OrderVoucherAdminVO> voPage = new Page<OrderVoucherAdminVO>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(vouchers.stream().map(voucher -> {
            OrderVoucherAdminVO vo = new OrderVoucherAdminVO();
            BeanUtils.copyProperties(voucher, vo);
            vo.setItemSummary(buildItemSummary(itemMap.get(voucher.getId())));
            return vo;
        }).collect(Collectors.toList()));
        return PageUtil.toPageResponse(voPage);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderVoucherDetailVO detail(Long id) {
        ensureSchemaReady();
        OrderVoucher voucher = requireVoucher(id);
        List<OrderVoucherItem> items = loadItemsByVoucherId(id);
        reconcileVoucherAmounts(voucher, items, false);
        return toDetailVO(voucher, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OrderVoucherSaveRequest request) {
        ensureSchemaReady();
        VoucherCalcResult calc = normalizeRequest(request);
        OrderVoucher voucher = new OrderVoucher();
        fillVoucher(voucher, request, calc, true);
        orderVoucherMapper.insert(voucher);
        saveItems(voucher.getId(), calc.items);
        return voucher.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long update(Long id, OrderVoucherSaveRequest request) {
        ensureSchemaReady();
        OrderVoucher existing = requireVoucher(id);
        VoucherCalcResult calc = normalizeRequest(request);
        fillVoucher(existing, request, calc, false);
        orderVoucherMapper.updateById(existing);
        orderVoucherItemMapper.delete(new LambdaQueryWrapper<OrderVoucherItem>().eq(OrderVoucherItem::getVoucherId, id));
        saveItems(id, calc.items);
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidVoucher(Long id) {
        ensureSchemaReady();
        requireVoucher(id);
        orderVoucherMapper.update(null, new LambdaUpdateWrapper<OrderVoucher>()
                .set(OrderVoucher::getStatus, STATUS_VOID)
                .eq(OrderVoucher::getId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVoucherDetailVO convertCurrency(Long id, OrderVoucherCurrencyConvertRequest request) {
        ensureSchemaReady();
        OrderVoucher voucher = requireVoucher(id);
        if (!PAYMENT_UNPAID.equals(voucher.getPaymentStatus())) {
            throw new BusinessException("仅未付款账单支持币种转换");
        }
        if (voucher.getPaidAmount() != null && voucher.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("存在已付金额的账单不支持币种转换");
        }
        String sourceCurrency = normalizeSupportedCurrency(voucher.getCurrencyCode());
        String targetCurrency = normalizeSupportedCurrency(request == null ? null : request.getTargetCurrency());
        if (sourceCurrency.equals(targetCurrency)) {
            throw new BusinessException("目标币种与当前币种相同");
        }

        CurrencyRateSnapshotVO snapshot = currencyRateService.getLatestSnapshot();
        BigDecimal sourceRate = resolveCurrencyRate(snapshot, sourceCurrency);
        BigDecimal targetRate = resolveCurrencyRate(snapshot, targetCurrency);

        List<OrderVoucherItem> items = loadItemsByVoucherId(id);
        for (OrderVoucherItem item : items) {
            item.setUnitPrice(convertAmount(item.getUnitPrice(), sourceRate, targetRate));
            item.setLineAmount(calcLineAmount(item.getUnitPrice(), item.getQuantity()));
            orderVoucherItemMapper.updateById(item);
        }

        voucher.setCurrencyCode(targetCurrency);
        voucher.setSubtotalAmount(convertAmount(voucher.getSubtotalAmount(), sourceRate, targetRate));
        voucher.setShippingFee(convertAmount(voucher.getShippingFee(), sourceRate, targetRate));
        voucher.setDiscountAmount(convertAmount(voucher.getDiscountAmount(), sourceRate, targetRate));
        voucher.setTotalAmount(convertAmount(voucher.getTotalAmount(), sourceRate, targetRate));
        voucher.setPaidAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        voucher.setBalanceAmount(voucher.getTotalAmount());
        voucher.setPaymentStatus(PAYMENT_UNPAID);
        orderVoucherMapper.updateById(voucher);
        return toDetailVO(requireVoucher(id), loadItemsByVoucherId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVoucherShareVO share(Long id) {
        ensureSchemaReady();
        OrderVoucher voucher = requireVoucher(id);
        List<OrderVoucherItem> items = loadItemsByVoucherId(id);
        reconcileVoucherAmounts(voucher, items, true);
        ensureShareable(voucher);
        if (isExpired(voucher)) {
            throw new BusinessException("凭证已过期，不能发送");
        }
        LocalDateTime now = LocalDateTime.now();
        orderVoucherMapper.update(null, new LambdaUpdateWrapper<OrderVoucher>()
                .setSql("share_count = share_count + 1")
                .set(OrderVoucher::getLastSharedTime, now)
                .eq(OrderVoucher::getId, id));
        OrderVoucher refreshed = requireVoucher(id);

        OrderVoucherShareVO vo = new OrderVoucherShareVO();
        vo.setVoucherNo(refreshed.getVoucherNo());
        vo.setPublicCode(refreshed.getPublicCode());
        vo.setVoucherUrl(buildVoucherUrl(refreshed.getPublicCode()));
        vo.setPosterUrl(buildPosterUrl(refreshed.getPublicCode()));
        vo.setCopyText(buildCopyText(refreshed));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVoucherPublicVO publicDetail(String publicCode) {
        ensureSchemaReady();
        OrderVoucher voucher = requireVoucherByCode(publicCode);
        List<OrderVoucherItem> items = loadItemsByVoucherId(voucher.getId());
        reconcileVoucherAmounts(voucher, items, true);
        ensurePublicAccessible(voucher);
        if (isExpired(voucher)) {
            throw new BusinessException("凭证已过期");
        }
        LocalDateTime now = LocalDateTime.now();
        orderVoucherMapper.update(null, new LambdaUpdateWrapper<OrderVoucher>()
                .setSql("view_count = view_count + 1")
                .set(OrderVoucher::getLastViewTime, now)
                .eq(OrderVoucher::getId, voucher.getId()));
        OrderVoucher refreshed = requireVoucher(voucher.getId());
        return toPublicVO(refreshed, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderVoucherShippingAddressVO> listShippingAddresses() {
        ensureSchemaReady();
        List<OrderVoucherShippingAddress> rows = orderVoucherShippingAddressMapper.selectList(
                new LambdaQueryWrapper<OrderVoucherShippingAddress>()
                        .orderByDesc(OrderVoucherShippingAddress::getEnabled)
                        .orderByAsc(OrderVoucherShippingAddress::getSort)
                        .orderByAsc(OrderVoucherShippingAddress::getId)
        );
        List<OrderVoucherShippingAddressVO> result = new ArrayList<OrderVoucherShippingAddressVO>();
        for (OrderVoucherShippingAddress row : rows) {
            result.add(toShippingAddressVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShippingAddress(OrderVoucherShippingAddressSaveRequest request) {
        ensureSchemaReady();
        OrderVoucherShippingAddress address = new OrderVoucherShippingAddress();
        fillShippingAddress(address, request);
        orderVoucherShippingAddressMapper.insert(address);
        return address.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updateShippingAddress(Long id, OrderVoucherShippingAddressSaveRequest request) {
        ensureSchemaReady();
        OrderVoucherShippingAddress existing = requireShippingAddress(id);
        fillShippingAddress(existing, request);
        orderVoucherShippingAddressMapper.updateById(existing);
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShippingAddress(Long id) {
        ensureSchemaReady();
        requireShippingAddress(id);
        Long usedCount = orderVoucherMapper.selectCount(new LambdaQueryWrapper<OrderVoucher>()
                .eq(OrderVoucher::getShippingAddressId, id));
        if (usedCount != null && usedCount > 0) {
            throw new BusinessException("该收货地址已被账单使用，不能删除");
        }
        orderVoucherShippingAddressMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderVoucherPaymentMethodVO> listPaymentMethods() {
        ensureSchemaReady();
        List<OrderVoucherPaymentMethod> rows = orderVoucherPaymentMethodMapper.selectList(
                new LambdaQueryWrapper<OrderVoucherPaymentMethod>()
                        .orderByDesc(OrderVoucherPaymentMethod::getEnabled)
                        .orderByAsc(OrderVoucherPaymentMethod::getSort)
                        .orderByAsc(OrderVoucherPaymentMethod::getId)
        );
        List<OrderVoucherPaymentMethodVO> result = new ArrayList<OrderVoucherPaymentMethodVO>();
        for (OrderVoucherPaymentMethod row : rows) {
            result.add(toPaymentMethodVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPaymentMethod(OrderVoucherPaymentMethodSaveRequest request) {
        ensureSchemaReady();
        OrderVoucherPaymentMethod method = new OrderVoucherPaymentMethod();
        fillPaymentMethod(method, request);
        orderVoucherPaymentMethodMapper.insert(method);
        return method.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long updatePaymentMethod(Long id, OrderVoucherPaymentMethodSaveRequest request) {
        ensureSchemaReady();
        OrderVoucherPaymentMethod existing = requirePaymentMethod(id);
        fillPaymentMethod(existing, request);
        orderVoucherPaymentMethodMapper.updateById(existing);
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePaymentMethod(Long id) {
        ensureSchemaReady();
        requirePaymentMethod(id);
        List<OrderVoucher> matched = orderVoucherMapper.selectList(new LambdaQueryWrapper<OrderVoucher>()
                .isNotNull(OrderVoucher::getPaymentMethodsJson));
        for (OrderVoucher voucher : matched) {
            List<OrderVoucherPaymentSelectionVO> methods = parsePaymentSelections(voucher.getPaymentMethodsJson());
            for (OrderVoucherPaymentSelectionVO item : methods) {
                if (item != null && id.equals(item.getMethodId())) {
                    throw new BusinessException("该支付方式已被账单使用，不能删除");
                }
            }
        }
        orderVoucherPaymentMethodMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePoster(String publicCode) throws Exception {
        ensureSchemaReady();
        OrderVoucher voucher = requireVoucherByCode(publicCode);
        ensurePublicAccessible(voucher);
        if (isExpired(voucher)) {
            throw new BusinessException("凭证已过期");
        }
        List<OrderVoucherItem> items = loadItemsByVoucherId(voucher.getId());
        ShopProfileVO shopProfile = shopProfileService.getProfile();

        int width = 1080;
        int height = 1680;
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new Color(247, 246, 243));
            g.fillRect(0, 0, width, height);

            g.setColor(new Color(31, 41, 55));
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            g.drawString("订单凭证", 70, 90);

            g.setFont(new Font("SansSerif", Font.PLAIN, 26));
            g.setColor(new Color(82, 82, 91));
            g.drawString(blankFallback(shopProfile.getShopName(), "Product Gallery"), 70, 136);
            g.drawString("凭证号: " + blankFallback(voucher.getVoucherNo(), "-"), 70, 174);
            if (!isBlank(voucher.getCustomerName())) {
                g.drawString("客户: " + voucher.getCustomerName().trim(), 70, 212);
            }

            drawItemImage(g, firstImage(items), width);

            int textTop = 980;
            g.setColor(new Color(17, 24, 39));
            g.setFont(new Font("SansSerif", Font.BOLD, 38));
            for (String line : splitLines(buildItemSummary(items), 22, 3)) {
                g.drawString(line, 70, textTop);
                textTop += 48;
            }

            g.setColor(new Color(185, 28, 28));
            g.setFont(new Font("SansSerif", Font.BOLD, 54));
            g.drawString(currencySymbol(voucher.getCurrencyCode()) + " " + moneyText(voucher.getTotalAmount()), 70, textTop + 40);

            g.setColor(new Color(75, 85, 99));
            g.setFont(new Font("SansSerif", Font.PLAIN, 28));
            g.drawString("支付状态: " + paymentStatusLabel(voucher.getPaymentStatus()), 70, textTop + 92);
            g.drawString("数量: " + safeInt(voucher.getItemCount()), 70, textTop + 132);

            BufferedImage qr = QrCodeUtil.generate(buildVoucherUrl(voucher.getPublicCode()), 320, 320);
            g.drawImage(qr, 70, 1260, null);
            g.setColor(new Color(31, 41, 55));
            g.setFont(new Font("SansSerif", Font.BOLD, 34));
            g.drawString("扫码查看订单凭证", 430, 1405);
            g.setFont(new Font("SansSerif", Font.PLAIN, 22));
            g.setColor(new Color(107, 114, 128));
            g.drawString(buildVoucherUrl(voucher.getPublicCode()), 430, 1450);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", outputStream);
        return outputStream.toByteArray();
    }

    private void ensureSchemaReady() {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            ensureTable();
            schemaReady = true;
        }
    }

    private void ensureTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS order_voucher ("
                        + "id BIGINT NOT NULL,"
                        + "voucher_no VARCHAR(64) NOT NULL,"
                        + "public_code VARCHAR(32) NOT NULL,"
                        + "status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',"
                        + "customer_name VARCHAR(120) DEFAULT NULL,"
                        + "customer_contact_type VARCHAR(32) DEFAULT NULL,"
                        + "customer_contact_value VARCHAR(255) DEFAULT NULL,"
                        + "shipping_address_id BIGINT DEFAULT NULL,"
                        + "shipping_address_snapshot VARCHAR(1000) DEFAULT NULL,"
                        + "currency_code VARCHAR(16) DEFAULT NULL,"
                        + "item_count INT NOT NULL DEFAULT 0,"
                        + "subtotal_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "shipping_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "balance_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "payment_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',"
                        + "payment_methods_json MEDIUMTEXT DEFAULT NULL,"
                        + "remark VARCHAR(1000) DEFAULT NULL,"
                        + "internal_note VARCHAR(1000) DEFAULT NULL,"
                        + "expire_time DATETIME DEFAULT NULL,"
                        + "share_count INT NOT NULL DEFAULT 0,"
                        + "view_count INT NOT NULL DEFAULT 0,"
                        + "last_shared_time DATETIME DEFAULT NULL,"
                        + "last_view_time DATETIME DEFAULT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_voucher_no (voucher_no),"
                        + "UNIQUE KEY uk_public_code (public_code),"
                        + "KEY idx_status (status),"
                        + "KEY idx_payment_status (payment_status),"
                        + "KEY idx_shipping_address_id (shipping_address_id),"
                        + "KEY idx_customer_name (customer_name),"
                        + "KEY idx_update_time (update_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS order_voucher_item ("
                        + "id BIGINT NOT NULL,"
                        + "voucher_id BIGINT NOT NULL,"
                        + "source_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOM',"
                        + "product_id BIGINT DEFAULT NULL,"
                        + "product_title_snapshot VARCHAR(255) NOT NULL,"
                        + "product_sku_snapshot VARCHAR(100) DEFAULT NULL,"
                        + "cover_image_snapshot VARCHAR(1024) DEFAULT NULL,"
                        + "unit_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "quantity INT NOT NULL DEFAULT 1,"
                        + "line_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,"
                        + "remark VARCHAR(500) DEFAULT NULL,"
                        + "sort INT NOT NULL DEFAULT 0,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_voucher_id (voucher_id),"
                        + "KEY idx_product_id (product_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS order_voucher_shipping_address ("
                        + "id BIGINT NOT NULL,"
                        + "label VARCHAR(120) NOT NULL,"
                        + "receiver_name VARCHAR(120) DEFAULT NULL,"
                        + "receiver_phone VARCHAR(64) DEFAULT NULL,"
                        + "country VARCHAR(80) DEFAULT NULL,"
                        + "state VARCHAR(80) DEFAULT NULL,"
                        + "city VARCHAR(80) DEFAULT NULL,"
                        + "address_line1 VARCHAR(255) DEFAULT NULL,"
                        + "address_line2 VARCHAR(255) DEFAULT NULL,"
                        + "postal_code VARCHAR(64) DEFAULT NULL,"
                        + "remark VARCHAR(500) DEFAULT NULL,"
                        + "enabled TINYINT NOT NULL DEFAULT 1,"
                        + "sort INT NOT NULL DEFAULT 0,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_enabled_sort (enabled, sort)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS order_voucher_payment_method ("
                        + "id BIGINT NOT NULL,"
                        + "name VARCHAR(120) NOT NULL,"
                        + "type VARCHAR(32) NOT NULL,"
                        + "description VARCHAR(1000) DEFAULT NULL,"
                        + "account_value VARCHAR(255) DEFAULT NULL,"
                        + "bank_fields_json MEDIUMTEXT DEFAULT NULL,"
                        + "enabled TINYINT NOT NULL DEFAULT 1,"
                        + "sort INT NOT NULL DEFAULT 0,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_type_enabled_sort (type, enabled, sort)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        ensureColumn("order_voucher", "shipping_address_id", "ALTER TABLE order_voucher ADD COLUMN shipping_address_id BIGINT DEFAULT NULL");
        ensureColumn("order_voucher", "shipping_address_snapshot", "ALTER TABLE order_voucher ADD COLUMN shipping_address_snapshot VARCHAR(1000) DEFAULT NULL");
        ensureColumn("order_voucher", "payment_methods_json", "ALTER TABLE order_voucher ADD COLUMN payment_methods_json MEDIUMTEXT DEFAULT NULL");
    }

    private void ensureColumn(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }

    private OrderVoucher requireVoucher(Long id) {
        OrderVoucher voucher = orderVoucherMapper.selectById(id);
        if (voucher == null) {
            throw new BusinessException("订单凭证不存在");
        }
        return voucher;
    }

    private OrderVoucher requireVoucherByCode(String publicCode) {
        if (isBlank(publicCode)) {
            throw new BusinessException("凭证编码不能为空");
        }
        OrderVoucher voucher = orderVoucherMapper.selectOne(new LambdaQueryWrapper<OrderVoucher>()
                .eq(OrderVoucher::getPublicCode, publicCode.trim())
                .last("limit 1"));
        if (voucher == null) {
            throw new BusinessException("订单凭证不存在");
        }
        return voucher;
    }

    private List<OrderVoucherItem> loadItemsByVoucherId(Long voucherId) {
        if (voucherId == null) {
            return Collections.emptyList();
        }
        return orderVoucherItemMapper.selectList(new LambdaQueryWrapper<OrderVoucherItem>()
                .eq(OrderVoucherItem::getVoucherId, voucherId)
                .orderByAsc(OrderVoucherItem::getSort)
                .orderByAsc(OrderVoucherItem::getId));
    }

    private Map<Long, List<OrderVoucherItem>> loadItemMap(List<Long> voucherIds) {
        Map<Long, List<OrderVoucherItem>> map = new HashMap<Long, List<OrderVoucherItem>>();
        if (voucherIds == null || voucherIds.isEmpty()) {
            return map;
        }
        List<OrderVoucherItem> items = orderVoucherItemMapper.selectList(new LambdaQueryWrapper<OrderVoucherItem>()
                .in(OrderVoucherItem::getVoucherId, voucherIds)
                .orderByAsc(OrderVoucherItem::getSort)
                .orderByAsc(OrderVoucherItem::getId));
        for (OrderVoucherItem item : items) {
            List<OrderVoucherItem> group = map.get(item.getVoucherId());
            if (group == null) {
                group = new ArrayList<OrderVoucherItem>();
                map.put(item.getVoucherId(), group);
            }
            group.add(item);
        }
        return map;
    }

    private VoucherCalcResult normalizeRequest(OrderVoucherSaveRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        List<OrderVoucherItemRequest> sourceItems = request.getItems();
        if (sourceItems == null || sourceItems.isEmpty()) {
            throw new BusinessException("至少添加1个商品");
        }

        VoucherCalcResult result = new VoucherCalcResult();
        result.items = new ArrayList<OrderVoucherItem>();

        BigDecimal subtotal = BigDecimal.ZERO;
        int quantityTotal = 0;
        int sort = 1;
        for (int i = 0; i < sourceItems.size(); i += 1) {
            OrderVoucherItemRequest row = sourceItems.get(i);
            if (row == null) {
                continue;
            }
            OrderVoucherItem item = normalizeItem(row, i, sort);
            subtotal = subtotal.add(safeMoney(item.getLineAmount()));
            quantityTotal += safeInt(item.getQuantity());
            result.items.add(item);
            sort += 1;
        }
        if (result.items.isEmpty()) {
            throw new BusinessException("至少添加1个商品");
        }

        result.itemCount = quantityTotal;
        result.subtotalAmount = money(subtotal);
        result.shippingFee = money(request.getShippingFee());
        result.discountAmount = money(request.getDiscountAmount());
        result.totalAmount = money(result.subtotalAmount.add(result.shippingFee).subtract(result.discountAmount));
        if (result.totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("总金额不能为负");
        }
        result.paidAmount = money(request.getPaidAmount());
        BigDecimal balance = result.totalAmount.subtract(result.paidAmount);
        result.balanceAmount = money(balance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : balance);
        result.paymentStatus = calculatePaymentStatus(result.totalAmount, result.paidAmount);
        return result;
    }

    private OrderVoucherItem normalizeItem(OrderVoucherItemRequest row, int index, int sort) {
        Long productId = row.getProductId();
        ProductDetailVO product = null;
        if (productId != null) {
            product = productService.detail(productId, false, LanguageUtil.DEFAULT_LANG);
        }

        String title = trimToEmpty(row.getTitle());
        if (isBlank(title) && product != null) {
            title = trimToEmpty(product.getTitle());
        }
        if (isBlank(title)) {
            throw new BusinessException("第" + (index + 1) + "个商品缺少名称");
        }

        Integer quantity = row.getQuantity();
        if (quantity == null || quantity.intValue() < 1) {
            throw new BusinessException("第" + (index + 1) + "个商品数量至少为1");
        }

        BigDecimal unitPrice = row.getUnitPrice();
        if (unitPrice == null && product != null) {
            unitPrice = product.getPrice();
        }
        if (unitPrice == null) {
            throw new BusinessException("第" + (index + 1) + "个商品缺少单价");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("第" + (index + 1) + "个商品单价不能为负");
        }

        String imageUrl = trimToEmpty(row.getImageUrl());
        if (isBlank(imageUrl) && product != null) {
            imageUrl = trimToEmpty(product.getCoverImage());
        }

        String sku = trimToEmpty(row.getSku());
        if (isBlank(sku) && product != null) {
            sku = trimToEmpty(product.getSku());
        }

        OrderVoucherItem item = new OrderVoucherItem();
        item.setSourceType(productId == null ? "CUSTOM" : "SYSTEM");
        item.setProductId(productId);
        item.setProductTitleSnapshot(title);
        item.setProductSkuSnapshot(blankToNull(sku));
        item.setCoverImageSnapshot(blankToNull(imageUrl));
        item.setUnitPrice(money(unitPrice));
        item.setQuantity(quantity);
        item.setLineAmount(money(item.getUnitPrice().multiply(new BigDecimal(quantity.intValue()))));
        item.setRemark(blankToNull(trimToEmpty(row.getRemark())));
        item.setSort(row.getSort() == null ? sort : row.getSort());
        return item;
    }

    private void fillVoucher(OrderVoucher voucher, OrderVoucherSaveRequest request, VoucherCalcResult calc, boolean creating) {
        if (creating) {
            voucher.setVoucherNo(generateVoucherNo());
            voucher.setPublicCode(generatePublicCode());
            voucher.setShareCount(0);
            voucher.setViewCount(0);
        }
        voucher.setStatus(normalizeStatus(request.getStatus(), STATUS_ACTIVE));
        voucher.setCustomerName(blankToNull(trimToEmpty(request.getCustomerName())));
        voucher.setCustomerContactType(blankToNull(trimToEmpty(request.getCustomerContactType()).toUpperCase(Locale.ROOT)));
        voucher.setCustomerContactValue(blankToNull(trimToEmpty(request.getCustomerContactValue())));
        Long shippingAddressId = request.getShippingAddressId();
        String shippingSnapshot = null;
        if (shippingAddressId != null) {
            OrderVoucherShippingAddress address = requireShippingAddress(shippingAddressId);
            shippingSnapshot = buildShippingAddressDisplayText(address);
        }
        voucher.setShippingAddressId(shippingAddressId);
        voucher.setShippingAddressSnapshot(blankToNull(shippingSnapshot));
        voucher.setCurrencyCode(blankToNull(defaultIfBlank(request.getCurrencyCode(), "CNY").toUpperCase(Locale.ROOT)));
        voucher.setItemCount(calc.itemCount);
        voucher.setSubtotalAmount(calc.subtotalAmount);
        voucher.setShippingFee(calc.shippingFee);
        voucher.setDiscountAmount(calc.discountAmount);
        voucher.setTotalAmount(calc.totalAmount);
        voucher.setPaidAmount(calc.paidAmount);
        voucher.setBalanceAmount(calc.balanceAmount);
        voucher.setPaymentStatus(calc.paymentStatus);
        List<OrderVoucherPaymentSelectionVO> paymentSelections = normalizePaymentSelections(request.getPaymentMethods());
        voucher.setPaymentMethodsJson(paymentSelections.isEmpty() ? null : toJsonSafe(paymentSelections));
        voucher.setRemark(blankToNull(trimToEmpty(request.getRemark())));
        voucher.setInternalNote(blankToNull(trimToEmpty(request.getInternalNote())));
        voucher.setExpireTime(request.getExpireTime());
    }

    private void saveItems(Long voucherId, List<OrderVoucherItem> items) {
        if (voucherId == null || items == null) {
            return;
        }
        for (OrderVoucherItem item : items) {
            item.setVoucherId(voucherId);
            orderVoucherItemMapper.insert(item);
        }
    }

    private void reconcileVoucherAmounts(OrderVoucher voucher, List<OrderVoucherItem> items, boolean persist) {
        if (voucher == null || items == null || items.isEmpty()) {
            return;
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        int itemCount = 0;
        boolean itemDirty = false;
        for (OrderVoucherItem item : items) {
            if (item == null) {
                continue;
            }
            BigDecimal expectedLineAmount = calcLineAmount(item.getUnitPrice(), item.getQuantity());
            subtotal = subtotal.add(expectedLineAmount);
            itemCount += Math.max(1, safeInt(item.getQuantity()));
            if (compareMoney(item.getLineAmount(), expectedLineAmount) != 0) {
                item.setLineAmount(expectedLineAmount);
                if (persist) {
                    orderVoucherItemMapper.updateById(item);
                }
                itemDirty = true;
            }
        }
        BigDecimal normalizedSubtotal = money(subtotal);
        BigDecimal shippingFee = money(voucher.getShippingFee());
        BigDecimal discountAmount = money(voucher.getDiscountAmount());
        BigDecimal paidAmount = money(voucher.getPaidAmount());
        BigDecimal totalAmount = money(normalizedSubtotal.add(shippingFee).subtract(discountAmount));
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) {
            totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal balanceAmount = money(totalAmount.subtract(paidAmount));
        if (balanceAmount.compareTo(BigDecimal.ZERO) < 0) {
            balanceAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String paymentStatus = calculatePaymentStatus(totalAmount, paidAmount);

        boolean voucherDirty = false;
        if (safeInt(voucher.getItemCount()) != itemCount) {
            voucher.setItemCount(itemCount);
            voucherDirty = true;
        }
        if (compareMoney(voucher.getSubtotalAmount(), normalizedSubtotal) != 0) {
            voucher.setSubtotalAmount(normalizedSubtotal);
            voucherDirty = true;
        }
        if (compareMoney(voucher.getTotalAmount(), totalAmount) != 0) {
            voucher.setTotalAmount(totalAmount);
            voucherDirty = true;
        }
        if (compareMoney(voucher.getBalanceAmount(), balanceAmount) != 0) {
            voucher.setBalanceAmount(balanceAmount);
            voucherDirty = true;
        }
        if (!paymentStatus.equals(defaultIfBlank(voucher.getPaymentStatus(), PAYMENT_UNPAID))) {
            voucher.setPaymentStatus(paymentStatus);
            voucherDirty = true;
        }
        if (persist && (voucherDirty || itemDirty)) {
            orderVoucherMapper.updateById(voucher);
        }
    }

    private OrderVoucherDetailVO toDetailVO(OrderVoucher voucher, List<OrderVoucherItem> items) {
        OrderVoucherDetailVO vo = new OrderVoucherDetailVO();
        BeanUtils.copyProperties(voucher, vo);
        vo.setExpired(isExpired(voucher));
        vo.setVoucherUrl(buildVoucherUrl(voucher.getPublicCode()));
        vo.setPosterUrl(buildPosterUrl(voucher.getPublicCode()));
        vo.setPaymentMethods(parsePaymentSelections(voucher.getPaymentMethodsJson()));
        vo.setItems(toItemVOs(items));
        return vo;
    }

    private OrderVoucherPublicVO toPublicVO(OrderVoucher voucher, List<OrderVoucherItem> items) {
        ShopProfileVO shopProfile = shopProfileService.getProfile();
        OrderVoucherPublicVO vo = new OrderVoucherPublicVO();
        vo.setVoucherNo(voucher.getVoucherNo());
        vo.setPublicCode(voucher.getPublicCode());
        vo.setStatus(voucher.getStatus());
        vo.setPaymentStatus(voucher.getPaymentStatus());
        vo.setCustomerName(voucher.getCustomerName());
        vo.setShippingAddressSnapshot(voucher.getShippingAddressSnapshot());
        vo.setCurrencyCode(voucher.getCurrencyCode());
        vo.setItemCount(voucher.getItemCount());
        vo.setSubtotalAmount(voucher.getSubtotalAmount());
        vo.setShippingFee(voucher.getShippingFee());
        vo.setDiscountAmount(voucher.getDiscountAmount());
        vo.setTotalAmount(voucher.getTotalAmount());
        vo.setPaidAmount(voucher.getPaidAmount());
        vo.setBalanceAmount(voucher.getBalanceAmount());
        vo.setRemark(voucher.getRemark());
        vo.setExpireTime(voucher.getExpireTime());
        vo.setCreateTime(voucher.getCreateTime());
        vo.setExpired(isExpired(voucher));
        vo.setVoucherUrl(buildVoucherUrl(voucher.getPublicCode()));
        vo.setShopName(shopProfile.getShopName());
        vo.setShopLogo(shopProfile.getShopLogo());
        vo.setShopAnnouncement(shopProfile.getAnnouncement());
        vo.setPaymentMethods(parsePaymentSelections(voucher.getPaymentMethodsJson()));
        vo.setContacts(filterEnabledContacts(shopProfile.getContacts()));
        vo.setItems(toItemVOs(items));
        return vo;
    }

    private List<OrderVoucherItemVO> toItemVOs(List<OrderVoucherItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(item -> {
            OrderVoucherItemVO vo = new OrderVoucherItemVO();
            vo.setId(item.getId());
            vo.setVoucherId(item.getVoucherId());
            vo.setSourceType(item.getSourceType());
            vo.setProductId(item.getProductId());
            vo.setTitle(item.getProductTitleSnapshot());
            vo.setSku(item.getProductSkuSnapshot());
            vo.setImageUrl(item.getCoverImageSnapshot());
            vo.setUnitPrice(item.getUnitPrice());
            vo.setQuantity(item.getQuantity());
            vo.setLineAmount(item.getLineAmount());
            vo.setRemark(item.getRemark());
            vo.setSort(item.getSort());
            return vo;
        }).collect(Collectors.toList());
    }

    private void fillShippingAddress(OrderVoucherShippingAddress address, OrderVoucherShippingAddressSaveRequest request) {
        String label = trimToEmpty(request == null ? null : request.getLabel());
        if (isBlank(label)) {
            throw new BusinessException("收货地址名称不能为空");
        }
        if (label.length() > 120) {
            throw new BusinessException("收货地址名称过长");
        }
        String receiverName = trimToEmpty(request == null ? null : request.getReceiverName());
        String receiverPhone = trimToEmpty(request == null ? null : request.getReceiverPhone());
        String country = trimToEmpty(request == null ? null : request.getCountry());
        String state = trimToEmpty(request == null ? null : request.getState());
        String city = trimToEmpty(request == null ? null : request.getCity());
        String addressLine1 = trimToEmpty(request == null ? null : request.getAddressLine1());
        String addressLine2 = trimToEmpty(request == null ? null : request.getAddressLine2());
        String postalCode = trimToEmpty(request == null ? null : request.getPostalCode());
        String remark = trimToEmpty(request == null ? null : request.getRemark());

        address.setLabel(label);
        address.setReceiverName(blankToNull(receiverName));
        address.setReceiverPhone(blankToNull(receiverPhone));
        address.setCountry(blankToNull(country));
        address.setState(blankToNull(state));
        address.setCity(blankToNull(city));
        address.setAddressLine1(blankToNull(addressLine1));
        address.setAddressLine2(blankToNull(addressLine2));
        address.setPostalCode(blankToNull(postalCode));
        address.setRemark(blankToNull(remark));
        Integer enabled = request == null ? null : request.getEnabled();
        address.setEnabled(enabled != null && enabled.intValue() == 0 ? 0 : 1);
        Integer sort = request == null ? null : request.getSort();
        address.setSort(sort == null ? 0 : sort);
    }

    private void fillPaymentMethod(OrderVoucherPaymentMethod method, OrderVoucherPaymentMethodSaveRequest request) {
        String name = trimToEmpty(request == null ? null : request.getName());
        if (isBlank(name)) {
            throw new BusinessException("支付方式名称不能为空");
        }
        if (name.length() > 120) {
            throw new BusinessException("支付方式名称过长");
        }
        String type = normalizePaymentMethodType(request == null ? null : request.getType());
        String description = trimToEmpty(request == null ? null : request.getDescription());
        String accountValue = trimToEmpty(request == null ? null : request.getAccountValue());
        List<OrderVoucherBankFieldDTO> bankFields = normalizeBankFields(request == null ? null : request.getBankFields(), false);

        if (PAYMENT_METHOD_PAYPAL_TRANSFER.equals(type) && isBlank(accountValue)) {
            throw new BusinessException("PayPal转账方式必须填写收款账号");
        }
        if (PAYMENT_METHOD_BANK_TRANSFER.equals(type) && bankFields.isEmpty()) {
            throw new BusinessException("银行转账方式至少配置1项银行信息");
        }
        if (!PAYMENT_METHOD_PAYPAL_TRANSFER.equals(type)) {
            accountValue = "";
        }
        if (!PAYMENT_METHOD_BANK_TRANSFER.equals(type)) {
            bankFields = Collections.emptyList();
        }

        method.setName(name);
        method.setType(type);
        method.setDescription(blankToNull(description));
        method.setAccountValue(blankToNull(accountValue));
        method.setBankFieldsJson(bankFields.isEmpty() ? null : toJsonSafe(bankFields));
        Integer enabled = request == null ? null : request.getEnabled();
        method.setEnabled(enabled != null && enabled.intValue() == 0 ? 0 : 1);
        Integer sort = request == null ? null : request.getSort();
        method.setSort(sort == null ? 0 : sort);
    }

    private OrderVoucherShippingAddressVO toShippingAddressVO(OrderVoucherShippingAddress row) {
        OrderVoucherShippingAddressVO vo = new OrderVoucherShippingAddressVO();
        BeanUtils.copyProperties(row, vo);
        vo.setDisplayText(buildShippingAddressDisplayText(row));
        return vo;
    }

    private String buildShippingAddressDisplayText(OrderVoucherShippingAddress address) {
        if (address == null) {
            return null;
        }
        List<String> segments = new ArrayList<String>();
        String label = trimToEmpty(address.getLabel());
        if (!isBlank(label)) {
            segments.add(label);
        }
        String receiver = trimToEmpty(address.getReceiverName());
        String phone = trimToEmpty(address.getReceiverPhone());
        if (!isBlank(receiver) || !isBlank(phone)) {
            segments.add((receiver + (isBlank(receiver) || isBlank(phone) ? "" : " ") + phone).trim());
        }
        List<String> area = new ArrayList<String>();
        if (!isBlank(address.getCountry())) {
            area.add(address.getCountry().trim());
        }
        if (!isBlank(address.getState())) {
            area.add(address.getState().trim());
        }
        if (!isBlank(address.getCity())) {
            area.add(address.getCity().trim());
        }
        if (!area.isEmpty()) {
            segments.add(String.join(", ", area));
        }
        if (!isBlank(address.getAddressLine1())) {
            segments.add(address.getAddressLine1().trim());
        }
        if (!isBlank(address.getAddressLine2())) {
            segments.add(address.getAddressLine2().trim());
        }
        if (!isBlank(address.getPostalCode())) {
            segments.add(address.getPostalCode().trim());
        }
        return segments.isEmpty() ? null : String.join("\n", segments);
    }

    private OrderVoucherShippingAddress requireShippingAddress(Long id) {
        if (id == null) {
            throw new BusinessException("收货地址不存在");
        }
        OrderVoucherShippingAddress address = orderVoucherShippingAddressMapper.selectById(id);
        if (address == null) {
            throw new BusinessException("收货地址不存在");
        }
        return address;
    }

    private OrderVoucherPaymentMethod requirePaymentMethod(Long id) {
        if (id == null) {
            throw new BusinessException("支付方式不存在");
        }
        OrderVoucherPaymentMethod method = orderVoucherPaymentMethodMapper.selectById(id);
        if (method == null) {
            throw new BusinessException("支付方式不存在");
        }
        return method;
    }

    private OrderVoucherPaymentMethodVO toPaymentMethodVO(OrderVoucherPaymentMethod row) {
        OrderVoucherPaymentMethodVO vo = new OrderVoucherPaymentMethodVO();
        BeanUtils.copyProperties(row, vo);
        vo.setBankFields(parseBankFields(row.getBankFieldsJson()));
        return vo;
    }

    private List<OrderVoucherPaymentSelectionVO> normalizePaymentSelections(List<OrderVoucherPaymentSelectionRequest> selected) {
        if (selected == null || selected.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderVoucherPaymentSelectionVO> result = new ArrayList<OrderVoucherPaymentSelectionVO>();
        Set<Long> methodIds = new HashSet<Long>();
        for (int i = 0; i < selected.size(); i += 1) {
            OrderVoucherPaymentSelectionRequest row = selected.get(i);
            if (row == null || row.getMethodId() == null) {
                continue;
            }
            Long methodId = row.getMethodId();
            if (!methodIds.add(methodId)) {
                throw new BusinessException("支付方式重复选择");
            }
            OrderVoucherPaymentMethod method = requirePaymentMethod(methodId);
            if (method.getEnabled() != null && method.getEnabled().intValue() == 0) {
                throw new BusinessException("支付方式已停用：" + method.getName());
            }
            String type = normalizePaymentMethodType(method.getType());
            OrderVoucherPaymentSelectionVO item = new OrderVoucherPaymentSelectionVO();
            item.setMethodId(methodId);
            item.setName(method.getName());
            item.setType(type);
            item.setDescription(method.getDescription());

            if (PAYMENT_METHOD_PAYPAL_TRANSFER.equals(type)) {
                if (isBlank(method.getAccountValue())) {
                    throw new BusinessException("PayPal转账账号未配置：" + method.getName());
                }
                item.setAccountValue(method.getAccountValue().trim());
            } else if (PAYMENT_METHOD_PAYPAL_BILL.equals(type) || PAYMENT_METHOD_CREDIT_CARD_LINK.equals(type)) {
                String payUrl = trimToEmpty(row.getPayUrl());
                if (isBlank(payUrl)) {
                    throw new BusinessException("请填写支付链接：" + method.getName());
                }
                if (!payUrl.startsWith("http://") && !payUrl.startsWith("https://")) {
                    throw new BusinessException("支付链接必须以 http:// 或 https:// 开头：" + method.getName());
                }
                item.setPayUrl(payUrl);
            } else if (PAYMENT_METHOD_BANK_TRANSFER.equals(type)) {
                List<OrderVoucherBankFieldDTO> bankFields = parseBankFields(method.getBankFieldsJson());
                if (bankFields.isEmpty()) {
                    throw new BusinessException("银行转账信息未配置：" + method.getName());
                }
                item.setBankFields(bankFields);
            } else {
                throw new BusinessException("不支持的支付方式类型：" + type);
            }
            result.add(item);
        }
        return result;
    }

    private List<OrderVoucherPaymentSelectionVO> parsePaymentSelections(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<OrderVoucherPaymentSelectionVO> rows = objectMapper.readValue(json, new TypeReference<List<OrderVoucherPaymentSelectionVO>>() {
            });
            if (rows == null || rows.isEmpty()) {
                return Collections.emptyList();
            }
            List<OrderVoucherPaymentSelectionVO> result = new ArrayList<OrderVoucherPaymentSelectionVO>();
            for (OrderVoucherPaymentSelectionVO row : rows) {
                if (row == null || row.getMethodId() == null) {
                    continue;
                }
                row.setType(normalizePaymentMethodType(row.getType()));
                row.setName(trimToEmpty(row.getName()));
                row.setDescription(blankToNull(trimToEmpty(row.getDescription())));
                row.setAccountValue(blankToNull(trimToEmpty(row.getAccountValue())));
                row.setPayUrl(blankToNull(trimToEmpty(row.getPayUrl())));
                row.setBankFields(normalizeBankFields(row.getBankFields(), true));
                result.add(row);
            }
            return result;
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    private String normalizePaymentMethodType(String raw) {
        String value = trimToEmpty(raw).toUpperCase(Locale.ROOT);
        if (PAYMENT_METHOD_PAYPAL_TRANSFER.equals(value)
                || PAYMENT_METHOD_PAYPAL_BILL.equals(value)
                || PAYMENT_METHOD_CREDIT_CARD_LINK.equals(value)
                || PAYMENT_METHOD_BANK_TRANSFER.equals(value)) {
            return value;
        }
        throw new BusinessException("支付方式类型不合法");
    }

    private List<OrderVoucherBankFieldDTO> parseBankFields(String json) {
        if (isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            List<OrderVoucherBankFieldDTO> rows = objectMapper.readValue(json, new TypeReference<List<OrderVoucherBankFieldDTO>>() {
            });
            return normalizeBankFields(rows, true);
        } catch (Exception ignore) {
            return Collections.emptyList();
        }
    }

    private List<OrderVoucherBankFieldDTO> normalizeBankFields(List<OrderVoucherBankFieldDTO> rows, boolean tolerateEmpty) {
        List<OrderVoucherBankFieldDTO> result = new ArrayList<OrderVoucherBankFieldDTO>();
        if (rows == null) {
            return result;
        }
        for (int i = 0; i < rows.size(); i += 1) {
            OrderVoucherBankFieldDTO row = rows.get(i);
            if (row == null) {
                continue;
            }
            String label = trimToEmpty(row.getLabel());
            String value = trimToEmpty(row.getValue());
            String copyValue = trimToEmpty(row.getCopyValue());
            if (isBlank(label) && isBlank(value) && isBlank(copyValue)) {
                continue;
            }
            if (isBlank(label) || isBlank(value)) {
                if (tolerateEmpty) {
                    continue;
                }
                throw new BusinessException("银行转账字段缺少名称或内容");
            }
            OrderVoucherBankFieldDTO item = new OrderVoucherBankFieldDTO();
            item.setLabel(label);
            item.setValue(value);
            item.setCopyValue(blankToNull(copyValue));
            result.add(item);
        }
        return result;
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<ShopContactItemDTO> filterEnabledContacts(List<ShopContactItemDTO> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return Collections.emptyList();
        }
        return contacts.stream()
                .filter(item -> item != null && (item.getEnabled() == null || item.getEnabled().intValue() == 1))
                .collect(Collectors.toList());
    }

    private void ensurePublicAccessible(OrderVoucher voucher) {
        if (voucher == null) {
            throw new BusinessException("订单凭证不存在");
        }
        if (STATUS_VOID.equals(voucher.getStatus())) {
            throw new BusinessException("凭证已作废");
        }
        if (!STATUS_ACTIVE.equals(voucher.getStatus())) {
            throw new BusinessException("凭证尚未启用");
        }
    }

    private void ensureShareable(OrderVoucher voucher) {
        if (voucher == null) {
            throw new BusinessException("订单凭证不存在");
        }
        if (STATUS_VOID.equals(voucher.getStatus())) {
            throw new BusinessException("已作废凭证不能发送");
        }
        if (!STATUS_ACTIVE.equals(voucher.getStatus())) {
            throw new BusinessException("草稿凭证不能发送，请先启用");
        }
    }

    private String buildItemSummary(List<OrderVoucherItem> items) {
        if (items == null || items.isEmpty()) {
            return "-";
        }
        List<String> titles = new ArrayList<String>();
        for (OrderVoucherItem item : items) {
            if (item == null || isBlank(item.getProductTitleSnapshot())) {
                continue;
            }
            String text = item.getProductTitleSnapshot().trim();
            if (item.getQuantity() != null && item.getQuantity().intValue() > 1) {
                text = text + " x" + item.getQuantity();
            }
            titles.add(text);
        }
        if (titles.isEmpty()) {
            return "-";
        }
        if (titles.size() <= 2) {
            return String.join(" / ", titles);
        }
        return titles.get(0) + " / " + titles.get(1) + " 等" + titles.size() + "项";
    }

    private String buildCopyText(OrderVoucher voucher) {
        StringBuilder builder = new StringBuilder();
        builder.append("您好，您的订单凭证已生成。");
        if (!isBlank(voucher.getVoucherNo())) {
            builder.append(" 凭证号：").append(voucher.getVoucherNo()).append("。");
        }
        builder.append(" 合计：").append(currencySymbol(voucher.getCurrencyCode()))
                .append(" ").append(moneyText(voucher.getTotalAmount())).append("。");
        builder.append(" 查看链接：").append(buildVoucherUrl(voucher.getPublicCode()));
        return builder.toString();
    }

    private String buildVoucherUrl(String publicCode) {
        return trimTrailingSlash(shareBaseUrl) + "/voucher/" + publicCode;
    }

    private String buildPosterUrl(String publicCode) {
        return trimTrailingSlash(shareBaseUrl) + "/api/order-vouchers/poster/" + publicCode;
    }

    private String trimTrailingSlash(String value) {
        String source = defaultIfBlank(value, "http://localhost:8080").trim();
        while (source.endsWith("/")) {
            source = source.substring(0, source.length() - 1);
        }
        return source;
    }

    private String generateVoucherNo() {
        String prefix = "OV" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 20; i += 1) {
            String candidate = prefix + ShortCodeUtil.randomCode(4).toUpperCase(Locale.ROOT);
            Long count = orderVoucherMapper.selectCount(new LambdaQueryWrapper<OrderVoucher>()
                    .eq(OrderVoucher::getVoucherNo, candidate));
            if (count == null || count.longValue() == 0L) {
                return candidate;
            }
        }
        throw new BusinessException("凭证号生成失败，请重试");
    }

    private String generatePublicCode() {
        for (int i = 0; i < 20; i += 1) {
            String code = ShortCodeUtil.randomCode(8);
            Long count = orderVoucherMapper.selectCount(new LambdaQueryWrapper<OrderVoucher>()
                    .eq(OrderVoucher::getPublicCode, code));
            if (count == null || count.longValue() == 0L) {
                return code;
            }
        }
        throw new BusinessException("公开凭证编码生成失败，请重试");
    }

    private String normalizeStatus(String status, String defaultValue) {
        String value = defaultIfBlank(status, defaultValue);
        if (isBlank(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (STATUS_DRAFT.equals(normalized) || STATUS_ACTIVE.equals(normalized) || STATUS_VOID.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("凭证状态不合法");
    }

    private String normalizePaymentStatus(String status) {
        String normalized = defaultIfBlank(status, "").trim().toUpperCase(Locale.ROOT);
        if (PAYMENT_UNPAID.equals(normalized) || PAYMENT_PARTIAL.equals(normalized) || PAYMENT_PAID.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("支付状态不合法");
    }

    private String calculatePaymentStatus(BigDecimal totalAmount, BigDecimal paidAmount) {
        BigDecimal total = safeMoney(totalAmount);
        BigDecimal paid = safeMoney(paidAmount);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PAYMENT_UNPAID;
        }
        if (paid.compareTo(total) >= 0) {
            return PAYMENT_PAID;
        }
        return PAYMENT_PARTIAL;
    }

    private boolean isExpired(OrderVoucher voucher) {
        return voucher != null
                && voucher.getExpireTime() != null
                && voucher.getExpireTime().isBefore(LocalDateTime.now());
    }

    private void drawItemImage(Graphics2D g, String imageUrl, int canvasWidth) {
        if (isBlank(imageUrl)) {
            g.setColor(new Color(229, 231, 235));
            g.fillRoundRect(70, 260, canvasWidth - 140, 620, 28, 28);
            g.setColor(new Color(156, 163, 175));
            g.setFont(new Font("SansSerif", Font.PLAIN, 28));
            g.drawString("未设置商品图片", 430, 575);
            return;
        }
        try {
            BufferedImage source = ImageIO.read(new URL(resolveAbsoluteUrl(imageUrl)));
            if (source == null) {
                return;
            }
            int targetW = canvasWidth - 140;
            int targetH = 620;
            Image scaled = source.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
            g.drawImage(scaled, 70, 260, null);
        } catch (Exception ignored) {
        }
    }

    private String firstImage(List<OrderVoucherItem> items) {
        if (items == null) {
            return "";
        }
        for (OrderVoucherItem item : items) {
            if (item != null && !isBlank(item.getCoverImageSnapshot())) {
                return item.getCoverImageSnapshot();
            }
        }
        return "";
    }

    private String resolveAbsoluteUrl(String value) {
        if (isBlank(value)) {
            return "";
        }
        String url = value.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return trimTrailingSlash(shareBaseUrl) + url;
        }
        return trimTrailingSlash(shareBaseUrl) + "/" + url;
    }

    private List<String> splitLines(String text, int maxChars, int maxLines) {
        List<String> lines = new ArrayList<String>();
        String clean = defaultIfBlank(text, "-").trim();
        int from = 0;
        while (from < clean.length() && lines.size() < maxLines) {
            int to = Math.min(clean.length(), from + maxChars);
            lines.add(clean.substring(from, to));
            from = to;
        }
        if (from < clean.length() && !lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (last.length() > 1) {
                last = last.substring(0, last.length() - 1) + "…";
            }
            lines.set(lines.size() - 1, last);
        }
        return lines;
    }

    private String paymentStatusLabel(String status) {
        if (PAYMENT_PAID.equals(status)) {
            return "已付清";
        }
        if (PAYMENT_PARTIAL.equals(status)) {
            return "部分付款";
        }
        return "未付款";
    }

    private String currencySymbol(String currencyCode) {
        String code = normalizeSupportedCurrency(currencyCode);
        Map<String, String> symbols = new LinkedHashMap<String, String>();
        symbols.put("CNY", "¥");
        symbols.put("USD", "$");
        symbols.put("EUR", "€");
        symbols.put("GBP", "£");
        symbols.put("JPY", "¥");
        return symbols.containsKey(code) ? symbols.get(code) : code;
    }

    private BigDecimal convertAmount(BigDecimal amount, BigDecimal sourceRate, BigDecimal targetRate) {
        BigDecimal safeAmount = money(amount);
        if (safeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return safeAmount;
        }
        if (sourceRate == null || sourceRate.compareTo(BigDecimal.ZERO) <= 0 || targetRate == null || targetRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("汇率数据无效，无法完成币种转换");
        }
        BigDecimal converted = safeAmount.divide(sourceRate, 8, RoundingMode.HALF_UP).multiply(targetRate);
        return converted.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calcLineAmount(BigDecimal unitPrice, Integer quantity) {
        int qty = safeInt(quantity);
        if (qty < 1) {
            qty = 1;
        }
        return money(safeMoney(unitPrice).multiply(BigDecimal.valueOf(qty)));
    }

    private int compareMoney(BigDecimal left, BigDecimal right) {
        return money(left).compareTo(money(right));
    }

    private BigDecimal resolveCurrencyRate(CurrencyRateSnapshotVO snapshot, String currencyCode) {
        if (snapshot == null || snapshot.getRates() == null || snapshot.getRates().isEmpty()) {
            throw new BusinessException("汇率快照为空，无法完成币种转换");
        }
        BigDecimal rate = snapshot.getRates().get(currencyCode);
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("缺少币种 " + currencyCode + " 的汇率，无法完成币种转换");
        }
        return rate;
    }

    private String normalizeSupportedCurrency(String currencyCode) {
        String code = defaultIfBlank(currencyCode, "CNY").trim().toUpperCase(Locale.ROOT);
        if ("CNY".equals(code) || "USD".equals(code) || "EUR".equals(code) || "GBP".equals(code) || "JPY".equals(code)) {
            return code;
        }
        throw new BusinessException("不支持的币种: " + code);
    }

    private String moneyText(BigDecimal value) {
        return money(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal money(BigDecimal value) {
        return safeMoney(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private String blankFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class VoucherCalcResult {
        private List<OrderVoucherItem> items;
        private Integer itemCount;
        private BigDecimal subtotalAmount;
        private BigDecimal shippingFee;
        private BigDecimal discountAmount;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal balanceAmount;
        private String paymentStatus;
    }
}
