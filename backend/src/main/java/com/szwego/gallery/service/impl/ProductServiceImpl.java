package com.szwego.gallery.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.BucketStat;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.config.OssProperties;
import com.szwego.gallery.domain.Category;
import com.szwego.gallery.domain.ProductI18n;
import com.szwego.gallery.domain.Product;
import com.szwego.gallery.domain.ProductImage;
import com.szwego.gallery.dto.DashboardStatVO;
import com.szwego.gallery.dto.LlmTranslationResult;
import com.szwego.gallery.dto.ProductBatchCategoryRequest;
import com.szwego.gallery.dto.ProductCardVO;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ProductPublishRequest;
import com.szwego.gallery.dto.ProductSummaryVO;
import com.szwego.gallery.mapper.ProductImageMapper;
import com.szwego.gallery.mapper.ProductI18nMapper;
import com.szwego.gallery.mapper.ProductMapper;
import com.szwego.gallery.service.CategoryService;
import com.szwego.gallery.service.LlmService;
import com.szwego.gallery.service.ProductService;
import com.szwego.gallery.util.LanguageUtil;
import com.szwego.gallery.util.PageUtil;
import com.szwego.gallery.util.TagUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductI18nMapper productI18nMapper;
    private final CategoryService categoryService;
    private final LlmService llmService;
    private final OssProperties ossProperties;
    private final JdbcTemplate jdbcTemplate;

    private static final long OSS_SIGN_EXPIRE_MILLIS = 60L * 60L * 1000L;
    private static final String OSS_PROCESS_KEY = "x-oss-process";
    private static final String LIST_THUMBNAIL_PROCESS = "image/resize,m_fill,w_360,h_360/quality,q_85";
    private static final Pattern TAG_CODE_LABELED_PATTERN = Pattern.compile("(?i)(?:款号|型号|货号|编号|code|style\\s*no|model)\\s*[:：#-]?\\s*([A-Za-z0-9][A-Za-z0-9-]{2,24})");
    private static final Pattern TAG_CODE_ALNUM_PATTERN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{1,4}-?\\d{3,8}[A-Za-z]?(?![A-Za-z0-9])");
    private static final Pattern TAG_CODE_DIGIT_PREFIX_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d[A-Za-z]{1,5}-?\\d{2,8}[A-Za-z]?(?![A-Za-z0-9])");
    private static final Pattern TAG_CODE_NUMERIC_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d{5,8}(?![A-Za-z0-9])");
    private static final Pattern TAG_DIMENSION_PATTERN = Pattern.compile("(?i)^\\d{1,3}(?:\\.\\d+)?[xX]\\d{1,3}(?:\\.\\d+)?(?:[xX]\\d{1,3}(?:\\.\\d+)?)?(?:cm|mm)?$");
    private static final Set<String> OSS_SIGNATURE_QUERY_KEYS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "expires",
            "signature",
            "ossaccesskeyid",
            "security-token",
            "x-oss-signature",
            "x-oss-signature-version",
            "x-oss-credential",
            "x-oss-date",
            "x-oss-expires",
            "x-oss-security-token",
            "x-amz-algorithm",
            "x-amz-credential",
            "x-amz-date",
            "x-amz-expires",
            "x-amz-signedheaders",
            "x-amz-signature",
            "x-amz-security-token",
            "x-id"
    )));
    private static final Map<String, String> TAG_TRANSLATION_EXACT_MAP = buildTagTranslationExactMap();
    private static final Map<String, String> TAG_TRANSLATION_CONTAINS_MAP = buildTagTranslationContainsMap();

    @PostConstruct
    public void initProductIndexes() {
        ensureIndex(
                "ws_album_product_import",
                "idx_formal_product_deleted",
                "CREATE INDEX idx_formal_product_deleted ON ws_album_product_import (formal_product_id, is_deleted)"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publish(ProductPublishRequest request) {
        checkCategoryExists(request.getCategoryId());
        checkSkuUnique(request.getSku(), null);
        validateImageUrls(request.getImageUrls());
        validateVideoUrl(request.getVideoUrl());

        Product product = new Product();
        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setSku(request.getSku());
        product.setTagsJson(TagUtil.toBilingualJson(request.getTags(), new ArrayList<String>()));
        product.setCoverImage(request.getImageUrls().get(0));
        product.setVideoUrl(request.getVideoUrl());
        product.setImageCount(request.getImageUrls().size());
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        product.setIsTop(request.getIsTop() != null && request.getIsTop() == 1 ? 1 : 0);
        product.setViews(0);
        productMapper.insert(product);
        String enTitle = request.getEnTitle();
        String enDescription = request.getEnDescription();
        if (!notBlank(enTitle) && !notBlank(enDescription)) {
            LlmTranslationResult translated = llmService.summarizeAndTranslateToEnglish(request.getTitle(), request.getDescription());
            if (translated != null) {
                enTitle = translated.getEnTitle();
                enDescription = translated.getEnDescription();
            }
        }
        saveEnglishTranslation(product.getId(), enTitle, enDescription);

        saveProductImages(product.getId(), request);
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long update(ProductPublishRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("商品ID不能为空");
        }
        Product existing = productMapper.selectById(request.getId());
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }

        checkCategoryExists(request.getCategoryId());
        checkSkuUnique(request.getSku(), request.getId());
        validateImageUrls(request.getImageUrls());
        validateVideoUrl(request.getVideoUrl());

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setCategoryId(request.getCategoryId());
        existing.setPrice(request.getPrice());
        existing.setSku(request.getSku());
        existing.setTagsJson(TagUtil.toBilingualJson(request.getTags(), new ArrayList<String>()));
        existing.setCoverImage(request.getImageUrls().get(0));
        existing.setVideoUrl(request.getVideoUrl());
        existing.setImageCount(request.getImageUrls().size());
        existing.setStatus(request.getStatus() == null ? existing.getStatus() : request.getStatus());
        if (request.getIsTop() != null) {
            existing.setIsTop(request.getIsTop() == 1 ? 1 : 0);
        }
        productMapper.updateById(existing);
        String enTitle = request.getEnTitle();
        String enDescription = request.getEnDescription();
        if (!notBlank(enTitle) && !notBlank(enDescription)) {
            LlmTranslationResult translated = llmService.summarizeAndTranslateToEnglish(request.getTitle(), request.getDescription());
            if (translated != null) {
                enTitle = translated.getEnTitle();
                enDescription = translated.getEnDescription();
            }
        }
        saveEnglishTranslation(existing.getId(), enTitle, enDescription);

        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, existing.getId()));
        saveProductImages(existing.getId(), request);
        return existing.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateCategory(ProductBatchCategoryRequest request) {
        if (request.getProductIds() == null || request.getProductIds().isEmpty()) {
            throw new BusinessException("请至少选择一个商品");
        }
        checkCategoryExists(request.getCategoryId());

        Set<Long> idSet = new LinkedHashSet<Long>();
        for (Long productId : request.getProductIds()) {
            if (productId != null) {
                idSet.add(productId);
            }
        }
        if (idSet.isEmpty()) {
            throw new BusinessException("未检测到有效商品ID");
        }

        List<Long> productIds = new ArrayList<Long>(idSet);
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .set(Product::getCategoryId, request.getCategoryId())
                .in(Product::getId, productIds));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTopStatus(Long id, Integer isTop) {
        if (id == null) {
            throw new BusinessException("商品ID不能为空");
        }
        if (isTop == null || (isTop != 0 && isTop != 1)) {
            throw new BusinessException("置顶状态不合法");
        }
        Product existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("商品不存在");
        }
        productMapper.update(null, new LambdaUpdateWrapper<Product>()
                .set(Product::getIsTop, isTop)
                .eq(Product::getId, id));
    }

    private void saveProductImages(Long productId, ProductPublishRequest request) {
        List<ProductImage> list = new ArrayList<ProductImage>();
        for (int i = 0; i < request.getImageUrls().size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setImageUrl(request.getImageUrls().get(i));
            image.setSort(i + 1);
            if (request.getImageSizesKb() != null && request.getImageSizesKb().size() > i) {
                image.setImageSizeKb(request.getImageSizesKb().get(i));
            } else {
                image.setImageSizeKb(0L);
            }
            list.add(image);
        }
        for (ProductImage image : list) {
            productImageMapper.insert(image);
        }
    }

    private void checkCategoryExists(Long categoryId) {
        Category category = categoryService.getById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在");
        }
    }

    private void checkSkuUnique(String sku, Long selfId) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getSku, sku);
        if (selfId != null) {
            wrapper.ne(Product::getId, selfId);
        }
        Long count = productMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("货号已存在");
        }
    }

    private void validateImageUrls(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException("至少上传1张商品图");
        }
        for (String url : imageUrls) {
            if (url == null || url.trim().isEmpty()) {
                throw new BusinessException("存在无效图片地址，请重新上传");
            }
            String normalized = url.trim().toLowerCase();
            if (normalized.startsWith("blob:") || normalized.contains("localhost")) {
                throw new BusinessException("检测到本地预览地址，请重新上传图片");
            }
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                throw new BusinessException("图片地址格式错误，请重新上传");
            }
        }
    }

    private void validateVideoUrl(String videoUrl) {
        if (videoUrl == null || videoUrl.trim().isEmpty()) {
            return;
        }
        String normalized = videoUrl.trim().toLowerCase();
        if (normalized.startsWith("blob:") || normalized.contains("localhost")) {
            throw new BusinessException("检测到本地视频预览地址，请重新上传视频");
        }
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new BusinessException("视频地址格式错误，请重新上传");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        productMapper.deleteById(id);
        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductDetailVO detail(Long id, boolean increaseView, String lang) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }

        if (increaseView) {
            productMapper.update(null, new LambdaUpdateWrapper<Product>()
                    .setSql("views = views + 1")
                    .eq(Product::getId, id));
            product.setViews(product.getViews() == null ? 1 : product.getViews() + 1);
        }

        String normalizedLang = LanguageUtil.normalize(lang);
        Map<Long, String> categoryNameMap = categoryService.nameMap(normalizedLang);
        ProductDetailVO vo = new ProductDetailVO();
        BeanUtils.copyProperties(product, vo);
        vo.setCoverImage(resolveDisplayUrl(vo.getCoverImage()));
        vo.setVideoUrl(resolveDisplayUrl(vo.getVideoUrl()));
        vo.setCategoryName(categoryNameMap.get(product.getCategoryId()));
        vo.setTags(resolveDisplayTags(product, normalizedLang));
        vo.setZhTags(resolveDisplayTags(product, LanguageUtil.DEFAULT_LANG));
        vo.setEnTags(resolveDisplayTags(product, LanguageUtil.EN_LANG));
        ProductI18n enTranslation = findProductTranslation(product.getId(), LanguageUtil.EN_LANG);
        if (enTranslation != null) {
            vo.setEnTitle(enTranslation.getTitle());
            vo.setEnDescription(enTranslation.getDescription());
        }
        if (!LanguageUtil.isDefaultLang(normalizedLang)) {
            ProductI18n currentTranslation = findProductTranslation(product.getId(), normalizedLang);
            if (currentTranslation != null) {
                if (notBlank(currentTranslation.getTitle())) {
                    vo.setTitle(currentTranslation.getTitle());
                }
                if (notBlank(currentTranslation.getDescription())) {
                    vo.setDescription(currentTranslation.getDescription());
                }
            }
        }

        List<String> imageUrls = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, id)
                        .orderByAsc(ProductImage::getSort))
                .stream().map(ProductImage::getImageUrl).map(this::resolveDisplayUrl).collect(Collectors.toList());
        vo.setImageUrls(imageUrls);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductCardVO> page(Long page, Long size, String keyword, String sku, Long categoryId, String tag, Integer status, Integer topOnly, String lang) {
        String normalizedLang = LanguageUtil.normalize(lang);
        List<String> queryTags = new ArrayList<String>();
        List<Long> translatedMatches = new ArrayList<Long>();
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            translatedMatches = findProductIdsByTranslatedKeyword(normalizedLang, kw);
            final List<Long> translatedKeywordMatches = translatedMatches;
            wrapper.and(w -> {
                w.like(Product::getTitle, kw)
                        .or().like(Product::getDescription, kw)
                        .or().like(Product::getSku, kw);
                if (!translatedKeywordMatches.isEmpty()) {
                    w.or().in(Product::getId, translatedKeywordMatches);
                }
            });
        }
        if (sku != null && !sku.trim().isEmpty()) {
            wrapper.like(Product::getSku, sku);
        }
        if (categoryId != null) {
            List<Long> categoryIds = categoryService.listDescendantIds(categoryId);
            if (categoryIds == null || categoryIds.isEmpty()) {
                wrapper.eq(Product::getCategoryId, -1L);
            } else {
                wrapper.in(Product::getCategoryId, categoryIds);
            }
        }
        if (tag != null && !tag.trim().isEmpty()) {
            for (String item : tag.split(",")) {
                if (item != null && !item.trim().isEmpty()) {
                    queryTags.add(item.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        if (topOnly != null && topOnly == 1) {
            wrapper.eq(Product::getIsTop, 1);
        }
        long safePage = page == null || page < 1 ? 1 : page;
        long safeSize = size == null || size < 1 ? 20 : size;
        if (queryTags.isEmpty()) {
            return pageWithoutTag(safePage, safeSize, keyword, sku, categoryId, status, topOnly, normalizedLang, translatedMatches);
        }
        if (queryTags.isEmpty()) {
            wrapper.select(
                    Product::getId,
                    Product::getIsTop,
                    Product::getCreateTime,
                    Product::getUpdateTime
            );
        } else {
            wrapper.select(
                    Product::getId,
                    Product::getTagsJson,
                    Product::getIsTop,
                    Product::getCreateTime,
                    Product::getUpdateTime
            );
        }

        List<Product> matchedProducts = productMapper.selectList(wrapper);
        if (!queryTags.isEmpty()) {
            matchedProducts = matchedProducts.stream()
                    .filter(item -> containsAnyTagIgnoreCase(item.getTagsJson(), queryTags))
                    .collect(Collectors.toList());
        }
        if (matchedProducts.isEmpty()) {
            Page<ProductCardVO> empty = new Page<ProductCardVO>(page, size, 0);
            empty.setPages(0);
            empty.setRecords(Collections.emptyList());
            return PageUtil.toPageResponse(empty);
        }

        matchedProducts.sort(new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) {
                int aTop = a.getIsTop() != null && a.getIsTop() == 1 ? 1 : 0;
                int bTop = b.getIsTop() != null && b.getIsTop() == 1 ? 1 : 0;
                if (aTop != bTop) {
                    return Integer.compare(bTop, aTop);
                }
                long aPrimary = resolveSourceSortTimestamp(a);
                long bPrimary = resolveSourceSortTimestamp(b);
                if (aPrimary != bPrimary) {
                    return Long.compare(bPrimary, aPrimary);
                }
                long aSecondary = resolveSecondarySortTimestamp(a);
                long bSecondary = resolveSecondarySortTimestamp(b);
                if (aSecondary != bSecondary) {
                    return Long.compare(bSecondary, aSecondary);
                }
                return Long.compare(b.getId() == null ? 0L : b.getId(), a.getId() == null ? 0L : a.getId());
            }
        });

        long total = matchedProducts.size();
        int fromIndex = (int) Math.min(total, (safePage - 1) * safeSize);
        int toIndex = (int) Math.min(total, fromIndex + safeSize);
        List<Product> pagedProducts = matchedProducts.subList(fromIndex, toIndex);

        Map<Long, String> categoryNameMap = categoryService.nameMap(normalizedLang);
        Page<ProductCardVO> voPage = new Page<ProductCardVO>(safePage, safeSize, total);
        voPage.setPages((total + safeSize - 1) / safeSize);

        List<Long> productIds = pagedProducts.stream().map(Product::getId).collect(Collectors.toList());
        Map<Long, ProductI18n> currentLangMap = loadProductTranslationMap(productIds, normalizedLang);
        Map<Long, ProductI18n> enLangMap = loadProductTranslationMap(productIds, LanguageUtil.EN_LANG);
        Map<Long, Product> productDetailMap = new HashMap<Long, Product>();
        if (!productIds.isEmpty()) {
            List<Product> pageProducts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .select(
                            Product::getId,
                            Product::getTitle,
                            Product::getCategoryId,
                            Product::getPrice,
                            Product::getSku,
                            Product::getTagsJson,
                            Product::getCoverImage,
                            Product::getVideoUrl,
                            Product::getImageCount,
                            Product::getStatus,
                            Product::getIsTop,
                            Product::getViews,
                            Product::getCreateTime,
                            Product::getUpdateTime
                    )
                    .in(Product::getId, productIds));
            for (Product product : pageProducts) {
                productDetailMap.put(product.getId(), product);
            }
        }
        Map<Long, List<String>> imageMap = new HashMap<Long, List<String>>();
        if (!productIds.isEmpty()) {
            List<ProductImage> productImages = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                    .select(
                            ProductImage::getProductId,
                            ProductImage::getImageUrl,
                            ProductImage::getSort
                    )
                    .in(ProductImage::getProductId, productIds)
                    .orderByAsc(ProductImage::getSort));
            for (ProductImage image : productImages) {
                List<String> urls = imageMap.computeIfAbsent(image.getProductId(), k -> new ArrayList<String>());
                urls.add(image.getImageUrl());
            }
        }

        List<ProductCardVO> records = pagedProducts.stream().map(item -> {
            Product displayProduct = productDetailMap.get(item.getId());
            if (displayProduct == null) {
                displayProduct = item;
            }
            ProductCardVO vo = new ProductCardVO();
            BeanUtils.copyProperties(displayProduct, vo);
            ProductI18n currentTranslation = currentLangMap.get(displayProduct.getId());
            if (currentTranslation != null && notBlank(currentTranslation.getTitle())) {
                vo.setTitle(currentTranslation.getTitle());
            }
            ProductI18n enTranslation = enLangMap.get(displayProduct.getId());
            if (enTranslation != null) {
                vo.setEnTitle(enTranslation.getTitle());
            }
            vo.setSourceTimestamp(resolveSourceSortTimestamp(displayProduct));
            vo.setCoverImage(resolveListImageUrl(vo.getCoverImage()));
            vo.setVideoUrl(resolveDisplayUrl(vo.getVideoUrl()));
            vo.setCategoryName(categoryNameMap.get(displayProduct.getCategoryId()));
            vo.setTags(resolveDisplayTags(displayProduct, normalizedLang));
            vo.setZhTags(resolveDisplayTags(displayProduct, LanguageUtil.DEFAULT_LANG));
            vo.setEnTags(resolveDisplayTags(displayProduct, LanguageUtil.EN_LANG));
            List<String> imageUrls = imageMap.get(displayProduct.getId());
            if (imageUrls == null || imageUrls.isEmpty()) {
                if (displayProduct.getCoverImage() != null && !displayProduct.getCoverImage().trim().isEmpty()) {
                    vo.setImageUrls(Collections.singletonList(resolveListImageUrl(displayProduct.getCoverImage())));
                } else {
                    vo.setImageUrls(Collections.emptyList());
                }
            } else {
                vo.setImageUrls(imageUrls.stream().map(this::resolveListImageUrl).collect(Collectors.toList()));
            }
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(records);

        return PageUtil.toPageResponse(voPage);
    }

    private PageResponse<ProductCardVO> pageWithoutTag(Long page,
                                                       Long size,
                                                       String keyword,
                                                       String sku,
                                                       Long categoryId,
                                                       Integer status,
                                                       Integer topOnly,
                                                       String normalizedLang,
                                                       List<Long> translatedMatches) {
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = categoryService.listDescendantIds(categoryId);
            if (categoryIds == null || categoryIds.isEmpty()) {
                Page<ProductCardVO> empty = new Page<ProductCardVO>(page, size, 0);
                empty.setPages(0);
                empty.setRecords(Collections.emptyList());
                return PageUtil.toPageResponse(empty);
            }
        }

        String normalizedKeyword = keyword == null ? null : keyword.trim();
        String normalizedSku = sku == null ? null : sku.trim();
        long total = productMapper.countForGalleryPage(
                status,
                topOnly,
                notBlank(normalizedKeyword) ? normalizedKeyword : null,
                notBlank(normalizedSku) ? normalizedSku : null,
                categoryIds,
                translatedMatches
        );
        if (total <= 0) {
            Page<ProductCardVO> empty = new Page<ProductCardVO>(page, size, 0);
            empty.setPages(0);
            empty.setRecords(Collections.emptyList());
            return PageUtil.toPageResponse(empty);
        }

        long offset = Math.max(0L, (page - 1) * size);
        List<Long> productIds = productMapper.selectGalleryPageIds(
                status,
                topOnly,
                notBlank(normalizedKeyword) ? normalizedKeyword : null,
                notBlank(normalizedSku) ? normalizedSku : null,
                categoryIds,
                translatedMatches,
                offset,
                size
        );
        if (productIds == null || productIds.isEmpty()) {
            Page<ProductCardVO> empty = new Page<ProductCardVO>(page, size, total);
            empty.setPages((total + size - 1) / size);
            empty.setRecords(Collections.emptyList());
            return PageUtil.toPageResponse(empty);
        }

        Page<ProductCardVO> voPage = new Page<ProductCardVO>(page, size, total);
        voPage.setPages((total + size - 1) / size);
        voPage.setRecords(buildProductCardRecords(productIds, normalizedLang));
        return PageUtil.toPageResponse(voPage);
    }

    private List<ProductCardVO> buildProductCardRecords(List<Long> productIds, String normalizedLang) {
        Map<Long, String> categoryNameMap = categoryService.nameMap(normalizedLang);
        Map<Long, ProductI18n> currentLangMap = loadProductTranslationMap(productIds, normalizedLang);
        Map<Long, ProductI18n> enLangMap = loadProductTranslationMap(productIds, LanguageUtil.EN_LANG);
        Map<Long, Product> productDetailMap = new HashMap<Long, Product>();
        if (!productIds.isEmpty()) {
            List<Product> pageProducts = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .select(
                            Product::getId,
                            Product::getTitle,
                            Product::getCategoryId,
                            Product::getPrice,
                            Product::getSku,
                            Product::getTagsJson,
                            Product::getCoverImage,
                            Product::getVideoUrl,
                            Product::getImageCount,
                            Product::getStatus,
                            Product::getIsTop,
                            Product::getViews,
                            Product::getCreateTime,
                            Product::getUpdateTime
                    )
                    .in(Product::getId, productIds));
            for (Product product : pageProducts) {
                productDetailMap.put(product.getId(), product);
            }
        }
        Map<Long, List<String>> imageMap = new HashMap<Long, List<String>>();
        if (!productIds.isEmpty()) {
            List<ProductImage> productImages = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                    .select(
                            ProductImage::getProductId,
                            ProductImage::getImageUrl,
                            ProductImage::getSort
                    )
                    .in(ProductImage::getProductId, productIds)
                    .orderByAsc(ProductImage::getSort));
            for (ProductImage image : productImages) {
                List<String> urls = imageMap.computeIfAbsent(image.getProductId(), k -> new ArrayList<String>());
                urls.add(image.getImageUrl());
            }
        }

        List<ProductCardVO> records = new ArrayList<ProductCardVO>();
        for (Long productId : productIds) {
            Product displayProduct = productDetailMap.get(productId);
            if (displayProduct == null) {
                continue;
            }
            ProductCardVO vo = new ProductCardVO();
            BeanUtils.copyProperties(displayProduct, vo);
            ProductI18n currentTranslation = currentLangMap.get(displayProduct.getId());
            if (currentTranslation != null && notBlank(currentTranslation.getTitle())) {
                vo.setTitle(currentTranslation.getTitle());
            }
            ProductI18n enTranslation = enLangMap.get(displayProduct.getId());
            if (enTranslation != null) {
                vo.setEnTitle(enTranslation.getTitle());
            }
            vo.setSourceTimestamp(resolveSourceSortTimestamp(displayProduct));
            vo.setCoverImage(resolveListImageUrl(vo.getCoverImage()));
            vo.setVideoUrl(resolveDisplayUrl(vo.getVideoUrl()));
            vo.setCategoryName(categoryNameMap.get(displayProduct.getCategoryId()));
            vo.setTags(resolveDisplayTags(displayProduct, normalizedLang));
            vo.setZhTags(resolveDisplayTags(displayProduct, LanguageUtil.DEFAULT_LANG));
            vo.setEnTags(resolveDisplayTags(displayProduct, LanguageUtil.EN_LANG));
            List<String> imageUrls = imageMap.get(displayProduct.getId());
            if (imageUrls == null || imageUrls.isEmpty()) {
                if (displayProduct.getCoverImage() != null && !displayProduct.getCoverImage().trim().isEmpty()) {
                    vo.setImageUrls(Collections.singletonList(resolveListImageUrl(displayProduct.getCoverImage())));
                } else {
                    vo.setImageUrls(Collections.emptyList());
                }
            } else {
                vo.setImageUrls(imageUrls.stream().map(this::resolveListImageUrl).collect(Collectors.toList()));
            }
            records.add(vo);
        }
        return records;
    }

    private long resolveSourceSortTimestamp(Product fallbackProduct) {
        if (fallbackProduct != null && fallbackProduct.getUpdateTime() != null) {
            return fallbackProduct.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (fallbackProduct != null && fallbackProduct.getCreateTime() != null) {
            return fallbackProduct.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return 0L;
    }

    private long resolveSecondarySortTimestamp(Product fallbackProduct) {
        if (fallbackProduct != null && fallbackProduct.getCreateTime() != null) {
            return fallbackProduct.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        return 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductSummaryVO summary(Integer status) {
        LambdaQueryWrapper<Product> totalWrapper = new LambdaQueryWrapper<Product>();
        if (status != null) {
            totalWrapper.eq(Product::getStatus, status);
        }
        Long total = productMapper.selectCount(totalWrapper);

        LambdaQueryWrapper<Product> newWrapper = new LambdaQueryWrapper<Product>()
                .ge(Product::getCreateTime, LocalDateTime.now().minusDays(7));
        if (status != null) {
            newWrapper.eq(Product::getStatus, status);
        }
        Long newCount = productMapper.selectCount(newWrapper);

        ProductSummaryVO vo = new ProductSummaryVO();
        vo.setTotalCount(total == null ? 0L : total);
        vo.setNewCount(newCount == null ? 0L : newCount);
        return vo;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatVO dashboardStats(boolean includeImageStorage) {
        DashboardStatVO vo = new DashboardStatVO();
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<Product>());
        Long categoryCount = (long) categoryService.nameMap(LanguageUtil.DEFAULT_LANG).size();
        Long imageCount = productImageMapper.selectCount(new LambdaQueryWrapper<ProductImage>());
        Long todayCount = productMapper.selectCount(new LambdaQueryWrapper<Product>()
                .ge(Product::getCreateTime, LocalDate.now().atStartOfDay())
                .lt(Product::getCreateTime, LocalDateTime.now()));

        vo.setTotalProducts(productCount == null ? 0L : productCount);
        vo.setTotalCategories(categoryCount == null ? 0L : categoryCount);
        vo.setTotalImages(imageCount == null ? 0L : imageCount);
        vo.setPublishedToday(todayCount == null ? 0L : todayCount);
        if (includeImageStorage) {
            long totalKb = calculateImageStorageKb();
            vo.setImageStorageMb(totalKb / 1024.0);
        }
        return vo;
    }

    private long calculateImageStorageKb() {
        Long ossKb = calculateOssImageStorageKb();
        if (ossKb != null) {
            return ossKb;
        }

        List<ProductImage> images = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .select(ProductImage::getImageSizeKb));
        long totalKb = 0L;
        for (ProductImage image : images) {
            totalKb += image.getImageSizeKb() == null ? 0L : image.getImageSizeKb();
        }
        return totalKb;
    }

    private Long calculateOssImageStorageKb() {
        if (!Boolean.TRUE.equals(ossProperties.getEnabled())
                || !notBlank(ossProperties.getEndpoint())
                || !notBlank(ossProperties.getBucketName())
                || !notBlank(ossProperties.getAccessKeyId())
                || !notBlank(ossProperties.getAccessKeySecret())) {
            return null;
        }

        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                    ossProperties.getEndpoint(),
                    ossProperties.getAccessKeyId(),
                    ossProperties.getAccessKeySecret()
            );
            BucketStat stat = ossClient.getBucketStat(ossProperties.getBucketName());
            long totalBytes = stat == null || stat.getStorageSize() == null ? 0L : Math.max(0L, stat.getStorageSize());
            return (totalBytes + 1023L) / 1024L;
        } catch (Exception ignore) {
            return null;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private void saveEnglishTranslation(Long productId, String enTitle, String enDescription) {
        if (productId == null) {
            return;
        }
        String title = trimToEmpty(enTitle);
        String description = trimToEmpty(enDescription);
        ProductI18n existing = findProductTranslation(productId, LanguageUtil.EN_LANG);
        if (!notBlank(title) && !notBlank(description)) {
            if (existing != null) {
                productI18nMapper.deleteById(existing.getId());
            }
            return;
        }

        if (existing == null) {
            ProductI18n row = new ProductI18n();
            row.setProductId(productId);
            row.setLangCode(LanguageUtil.EN_LANG);
            row.setTitle(title);
            row.setDescription(description);
            productI18nMapper.insert(row);
        } else {
            existing.setTitle(title);
            existing.setDescription(description);
            productI18nMapper.updateById(existing);
        }
    }

    private ProductI18n findProductTranslation(Long productId, String lang) {
        if (productId == null || !notBlank(lang)) {
            return null;
        }
        return productI18nMapper.selectOne(new LambdaQueryWrapper<ProductI18n>()
                .eq(ProductI18n::getProductId, productId)
                .eq(ProductI18n::getLangCode, LanguageUtil.normalize(lang))
                .last("limit 1"));
    }

    private Map<Long, ProductI18n> loadProductTranslationMap(List<Long> productIds, String lang) {
        Map<Long, ProductI18n> map = new HashMap<Long, ProductI18n>();
        if (productIds == null || productIds.isEmpty()) {
            return map;
        }
        String normalized = LanguageUtil.normalize(lang);
        if (LanguageUtil.isDefaultLang(normalized)) {
            return map;
        }
        List<ProductI18n> rows = productI18nMapper.selectList(new LambdaQueryWrapper<ProductI18n>()
                .in(ProductI18n::getProductId, productIds)
                .eq(ProductI18n::getLangCode, normalized));
        for (ProductI18n row : rows) {
            map.put(row.getProductId(), row);
        }
        return map;
    }

    private List<Long> findProductIdsByTranslatedKeyword(String lang, String keyword) {
        if (!notBlank(keyword) || LanguageUtil.isDefaultLang(lang)) {
            return new ArrayList<Long>();
        }
        List<ProductI18n> rows = productI18nMapper.selectList(new LambdaQueryWrapper<ProductI18n>()
                .select(ProductI18n::getProductId)
                .eq(ProductI18n::getLangCode, LanguageUtil.normalize(lang))
                .and(w -> w.like(ProductI18n::getTitle, keyword).or().like(ProductI18n::getDescription, keyword)));
        Set<Long> ids = new LinkedHashSet<Long>();
        for (ProductI18n row : rows) {
            if (row.getProductId() != null) {
                ids.add(row.getProductId());
            }
        }
        return new ArrayList<Long>(ids);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean containsAnyTagIgnoreCase(String tagsJson, List<String> queryTags) {
        if (queryTags == null || queryTags.isEmpty()) {
            return true;
        }
        List<String> tags = TagUtil.fromJsonAll(tagsJson);
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        Set<String> normalized = new LinkedHashSet<String>();
        for (String tag : tags) {
            if (!notBlank(tag)) {
                continue;
            }
            normalized.add(tag.trim().toLowerCase(Locale.ROOT));
        }
        if (normalized.isEmpty()) {
            return false;
        }
        for (String queryTag : queryTags) {
            if (normalized.contains(queryTag)) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveDisplayTags(Product product, String lang) {
        if (product == null) {
            return new ArrayList<String>();
        }
        LinkedHashSet<String> merged = new LinkedHashSet<String>();
        List<String> rawTags = expandCompositeTags(TagUtil.fromJsonByLang(product.getTagsJson(), lang));
        if (rawTags != null) {
            for (String rawTag : rawTags) {
                String translated = translateTag(rawTag, lang);
                if (notBlank(translated)) {
                    merged.add(translated.trim());
                }
            }
        }
        return new ArrayList<String>(merged);
    }

    private List<String> expandCompositeTags(List<String> tags) {
        LinkedHashSet<String> expanded = new LinkedHashSet<String>();
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<String>();
        }
        for (String raw : tags) {
            if (!notBlank(raw)) {
                continue;
            }
            String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim();
            if (normalized.isEmpty()) {
                continue;
            }
            String[] groups = normalized.split("[,，;/|、]+");
            for (String group : groups) {
                String segment = trimToEmpty(group);
                if (!notBlank(segment)) {
                    continue;
                }
                String[] tokens = segment.contains(" ") ? segment.split("\\s+") : new String[]{segment};
                for (String token : tokens) {
                    String tag = trimToEmpty(token);
                    if (notBlank(tag)) {
                        expanded.add(tag);
                    }
                }
            }
        }
        return new ArrayList<String>(expanded);
    }

    private String translateTag(String rawTag, String lang) {
        if (!notBlank(rawTag)) {
            return "";
        }
        String tag = rawTag.trim();
        String normalizedCode = normalizeCodeTag(tag);
        if (notBlank(normalizedCode)) {
            return normalizedCode;
        }
        if (LanguageUtil.isDefaultLang(lang)) {
            return tag;
        }

        String normalizedKey = normalizeTagKey(tag);
        String exact = TAG_TRANSLATION_EXACT_MAP.get(normalizedKey);
        if (notBlank(exact)) {
            return exact;
        }
        for (Map.Entry<String, String> entry : TAG_TRANSLATION_CONTAINS_MAP.entrySet()) {
            if (normalizedKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        String byPattern = translateTagByPattern(normalizedKey);
        if (notBlank(byPattern)) {
            return byPattern;
        }
        return tag;
    }

    private String normalizeCodeTag(String text) {
        if (!notBlank(text)) {
            return "";
        }
        String token = text.trim();
        if (!notBlank(token)) {
            return "";
        }
        if (!token.matches("(?i)^[A-Z0-9-]{3,24}$")) {
            return "";
        }
        if (!token.matches("(?i)^(?=.*[A-Z])(?=.*\\d)[A-Z0-9-]{3,24}$")) {
            return "";
        }
        return token.toUpperCase(Locale.ROOT);
    }

    private List<String> extractCodeTagsFromText(String title, String description, java.math.BigDecimal detectedPrice) {
        LinkedHashSet<String> codes = new LinkedHashSet<String>();
        String source = trimToEmpty(title) + "\n" + trimToEmpty(description);

        Matcher labeled = TAG_CODE_LABELED_PATTERN.matcher(source);
        while (labeled.find()) {
            addCodeTag(codes, labeled.group(1), detectedPrice);
        }

        Matcher alphaNum = TAG_CODE_ALNUM_PATTERN.matcher(source);
        while (alphaNum.find()) {
            addCodeTag(codes, alphaNum.group(), detectedPrice);
        }

        Matcher digitPrefix = TAG_CODE_DIGIT_PREFIX_PATTERN.matcher(source);
        while (digitPrefix.find()) {
            addCodeTag(codes, digitPrefix.group(), detectedPrice);
        }

        Matcher numeric = TAG_CODE_NUMERIC_PATTERN.matcher(source);
        while (numeric.find()) {
            addCodeTag(codes, numeric.group(), detectedPrice);
        }
        return new ArrayList<String>(codes);
    }

    private void addCodeTag(Set<String> collector, String rawToken, java.math.BigDecimal detectedPrice) {
        if (!notBlank(rawToken)) {
            return;
        }
        String code = rawToken.trim()
                .replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("[^A-Za-z0-9]+$", "");
        if (!notBlank(code)) {
            return;
        }
        if (code.length() < 3 || code.length() > 24) {
            return;
        }
        if (isLikelyPriceToken(code, detectedPrice)) {
            return;
        }
        if (isLikelyDimensionToken(code)) {
            return;
        }
        if (code.matches("20\\d{2}") || code.matches("19\\d{2}")) {
            return;
        }
        if (code.matches("(?i).*[A-Z].*")) {
            code = code.toUpperCase(Locale.ROOT);
        }
        collector.add(code);
    }

    private boolean isLikelyPriceToken(String token, java.math.BigDecimal detectedPrice) {
        if (!notBlank(token)) {
            return false;
        }
        String normalized = token.trim();
        if (normalized.matches("(?i)^P\\d{2,6}(?:\\.\\d{1,2})?$")) {
            return true;
        }
        if (detectedPrice == null) {
            return false;
        }
        String pricePlain = detectedPrice.stripTrailingZeros().toPlainString();
        String priceInt = detectedPrice.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
        return normalized.equalsIgnoreCase(pricePlain) || normalized.equalsIgnoreCase(priceInt);
    }

    private boolean isLikelyDimensionToken(String token) {
        if (!notBlank(token)) {
            return false;
        }
        String normalized = token.trim().replace('×', 'x');
        return TAG_DIMENSION_PATTERN.matcher(normalized).matches();
    }

    private String normalizeTagKey(String tag) {
        if (!notBlank(tag)) {
            return "";
        }
        String normalized = Normalizer.normalize(tag, Normalizer.Form.NFKC);
        return normalized.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String translateTagByPattern(String normalizedKey) {
        if (!notBlank(normalizedKey)) {
            return "";
        }
        if (normalizedKey.contains("男士斜挎包") || normalizedKey.contains("男斜挎包")) {
            return "Men's Crossbody";
        }
        if (normalizedKey.contains("保龄球包")) {
            return "Bowling Bag";
        }
        if (normalizedKey.contains("男布包") || normalizedKey.contains("男士布包")) {
            return "Men's Nylon Bag";
        }
        if (normalizedKey.contains("邮差包")) {
            return "Messenger Bag";
        }
        if (normalizedKey.contains("斜挎包")) {
            return "Crossbody Bag";
        }
        if (normalizedKey.contains("托特包")) {
            return "Tote Bag";
        }
        if (normalizedKey.contains("手提包")) {
            return "Top Handle Bag";
        }
        if (normalizedKey.contains("单肩包")) {
            return "Shoulder Bag";
        }
        if (normalizedKey.contains("布包")) {
            return "Nylon Bag";
        }
        return "";
    }

    private static Map<String, String> buildTagTranslationExactMap() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("新品", "New");
        map.put("新款", "New");
        map.put("上新", "New Arrival");
        map.put("现货", "In Stock");
        map.put("热卖", "Hot");
        map.put("爆款", "Best Seller");
        map.put("限量", "Limited");
        map.put("经典", "Classic");
        map.put("男款", "Men");
        map.put("女款", "Women");
        map.put("手提", "Top Handle");
        map.put("单肩", "Shoulder");
        map.put("斜挎", "Crossbody");
        map.put("双肩", "Backpack");
        map.put("托特", "Tote");
        map.put("邮差包", "Messenger");
        map.put("腋下包", "Underarm");
        map.put("钱包", "Wallet");
        map.put("卡包", "Card Holder");
        map.put("皮带", "Belt");
        map.put("鞋", "Shoes");
        map.put("服饰", "Apparel");
        map.put("配饰", "Accessories");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildTagTranslationContainsMap() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("普拉达", "Prada");
        map.put("葆蝶家", "Bottega Veneta");
        map.put("宝缇嘉", "Bottega Veneta");
        map.put("bv", "Bottega Veneta");
        map.put("古驰", "Gucci");
        map.put("香奈儿", "Chanel");
        map.put("路易威登", "Louis Vuitton");
        map.put("lv", "Louis Vuitton");
        map.put("迪奥", "Dior");
        map.put("爱马仕", "Hermes");
        map.put("巴黎世家", "Balenciaga");
        map.put("圣罗兰", "Saint Laurent");
        map.put("ysl", "Saint Laurent");
        map.put("罗意威", "Loewe");
        map.put("赛琳", "Celine");
        map.put("芬迪", "Fendi");
        map.put("缪缪", "Miu Miu");
        map.put("miu", "Miu Miu");
        map.put("宝格丽", "Bvlgari");
        map.put("卡地亚", "Cartier");
        map.put("思琳", "Celine");
        map.put("男士斜挎包", "Men's Crossbody");
        map.put("男斜挎包", "Men's Crossbody");
        map.put("男布包", "Men's Nylon Bag");
        map.put("男士布包", "Men's Nylon Bag");
        map.put("新款", "New");
        map.put("新品", "New");
        map.put("保龄球包", "Bowling Bag");
        return Collections.unmodifiableMap(map);
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }

    private String resolveDisplayUrl(String rawUrl) {
        return stripOssSignature(rawUrl);
    }

    private String resolveListImageUrl(String rawUrl) {
        return appendOssThumbnailProcess(stripOssSignature(rawUrl));
    }

    private String stripOssSignature(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return rawUrl;
        }
        String value = rawUrl.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex < 0) {
            return value;
        }
        int fragmentIndex = value.indexOf('#');
        String fragment = fragmentIndex >= 0 ? value.substring(fragmentIndex) : "";
        String mainPart = fragmentIndex >= 0 ? value.substring(0, fragmentIndex) : value;
        int mainQueryIndex = mainPart.indexOf('?');
        if (mainQueryIndex < 0) {
            return value;
        }
        String base = mainPart.substring(0, mainQueryIndex);
        String query = mainPart.substring(mainQueryIndex + 1);
        if (query.isEmpty()) {
            return base + fragment;
        }
        List<String> remainParts = new ArrayList<String>();
        boolean removed = false;
        for (String part : query.split("&")) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            String key = part;
            int equalIndex = part.indexOf('=');
            if (equalIndex >= 0) {
                key = part.substring(0, equalIndex);
            }
            String normalizedKey = key.trim().toLowerCase(Locale.ROOT);
            if (OSS_SIGNATURE_QUERY_KEYS.contains(normalizedKey)) {
                removed = true;
                continue;
            }
            remainParts.add(part);
        }
        if (!removed) {
            return value;
        }
        if (remainParts.isEmpty()) {
            return base + fragment;
        }
        return base + "?" + String.join("&", remainParts) + fragment;
    }

    private String appendOssThumbnailProcess(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return rawUrl;
        }
        String value = rawUrl.trim();
        int fragmentIndex = value.indexOf('#');
        String fragment = fragmentIndex >= 0 ? value.substring(fragmentIndex) : "";
        String mainPart = fragmentIndex >= 0 ? value.substring(0, fragmentIndex) : value;
        int queryIndex = mainPart.indexOf('?');
        if (queryIndex < 0) {
            return mainPart + "?" + OSS_PROCESS_KEY + "=" + LIST_THUMBNAIL_PROCESS + fragment;
        }
        String base = mainPart.substring(0, queryIndex);
        String query = mainPart.substring(queryIndex + 1);
        List<String> parts = new ArrayList<String>();
        if (!query.isEmpty()) {
            for (String part : query.split("&")) {
                if (part == null || part.isEmpty()) {
                    continue;
                }
                String key = part;
                int equalIndex = part.indexOf('=');
                if (equalIndex >= 0) {
                    key = part.substring(0, equalIndex);
                }
                if (OSS_PROCESS_KEY.equalsIgnoreCase(key.trim())) {
                    continue;
                }
                parts.add(part);
            }
        }
        parts.add(OSS_PROCESS_KEY + "=" + LIST_THUMBNAIL_PROCESS);
        return base + "?" + String.join("&", parts) + fragment;
    }

    private boolean isSignableOssUrl(String url) {
        if (!Boolean.TRUE.equals(ossProperties.getEnabled())) {
            return false;
        }
        if (isBlank(ossProperties.getEndpoint())
                || isBlank(ossProperties.getBucketName())
                || isBlank(ossProperties.getAccessKeyId())
                || isBlank(ossProperties.getAccessKeySecret())) {
            return false;
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String endpointHost = normalizeHost(ossProperties.getEndpoint());
            String bucketDomainHost = normalizeHost(ossProperties.getBucketDomain());
            String bucketHost = ossProperties.getBucketName() + "." + endpointHost;
            return host.equalsIgnoreCase(bucketHost)
                    || (!isBlank(bucketDomainHost) && host.equalsIgnoreCase(bucketDomainHost));
        } catch (Exception e) {
            return false;
        }
    }

    private String extractObjectKey(String url) {
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return null;
            }
            String objectKey = path.startsWith("/") ? path.substring(1) : path;
            if (objectKey.startsWith(ossProperties.getBucketName() + "/")) {
                objectKey = objectKey.substring(ossProperties.getBucketName().length() + 1);
            }
            return objectKey;
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeHost(String endpointOrDomain) {
        if (isBlank(endpointOrDomain)) {
            return "";
        }
        String value = endpointOrDomain.trim();
        if (value.startsWith("https://")) {
            value = value.substring("https://".length());
        } else if (value.startsWith("http://")) {
            value = value.substring("http://".length());
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
