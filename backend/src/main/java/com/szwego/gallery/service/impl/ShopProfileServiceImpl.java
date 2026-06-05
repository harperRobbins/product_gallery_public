package com.szwego.gallery.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.domain.ShopProfile;
import com.szwego.gallery.dto.ShopContactItemDTO;
import com.szwego.gallery.dto.ShopCustomPageDTO;
import com.szwego.gallery.dto.ShopMenuItemDTO;
import com.szwego.gallery.dto.ShopMenuPageBlockDTO;
import com.szwego.gallery.dto.ShopMenuPageConfigDTO;
import com.szwego.gallery.dto.ShopPageMetaDTO;
import com.szwego.gallery.dto.ShopProfileSaveRequest;
import com.szwego.gallery.dto.ShopProfileVO;
import com.szwego.gallery.mapper.ShopProfileMapper;
import com.szwego.gallery.service.ShopProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopProfileServiceImpl implements ShopProfileService {

    private static final Long PROFILE_ID = 1L;
    private static final Set<String> MENU_TARGET_TYPES_WITH_PAGE = new LinkedHashSet<String>();
    private static final Set<String> MENU_PAGE_BLOCK_TYPES = new LinkedHashSet<String>();
    private static final Set<String> MENU_PAGE_LINK_TYPES = new LinkedHashSet<String>();
    private volatile boolean schemaReady = false;

    private final ShopProfileMapper shopProfileMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    static {
        MENU_TARGET_TYPES_WITH_PAGE.add("CATEGORY");
        MENU_TARGET_TYPES_WITH_PAGE.add("TAG");
        MENU_TARGET_TYPES_WITH_PAGE.add("CATEGORY_TAG");

        MENU_PAGE_BLOCK_TYPES.add("IMAGE");
        MENU_PAGE_BLOCK_TYPES.add("RICH_TEXT");
        MENU_PAGE_BLOCK_TYPES.add("NOTICE");
        MENU_PAGE_BLOCK_TYPES.add("COUNTDOWN");

        MENU_PAGE_LINK_TYPES.add("NONE");
        MENU_PAGE_LINK_TYPES.add("INTERNAL");
        MENU_PAGE_LINK_TYPES.add("EXTERNAL");
    }

    @PostConstruct
    public void init() {
        ensureTable();
        schemaReady = true;
    }

    @Override
    @Transactional(readOnly = true)
    public ShopProfileVO getProfile() {
        ShopProfile profile = loadOrCreateProfile();
        return toVO(profile);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShopProfileVO save(ShopProfileSaveRequest request) {
        ShopProfile existing = loadOrCreateProfile();
        boolean legacyContactTouched = request.getContactName() != null
                || request.getContactWechat() != null
                || request.getContactPhone() != null;
        List<ShopCustomPageDTO> customPages;
        if (request.getCustomPages() == null) {
            customPages = parseCustomPages(existing.getCustomPagesJson());
        } else {
            customPages = normalizeCustomPages(request.getCustomPages());
        }
        Set<String> customPageKeySet = new LinkedHashSet<String>();
        for (ShopCustomPageDTO page : customPages) {
            customPageKeySet.add(page.getKey());
        }
        List<ShopMenuItemDTO> menuItems;
        if (request.getMenuItems() == null) {
            menuItems = parseMenus(existing.getMenuConfigJson());
        } else {
            menuItems = normalizeMenuItems(request.getMenuItems(), customPageKeySet);
        }
        Set<String> menuIdSet = new LinkedHashSet<String>();
        for (ShopMenuItemDTO item : menuItems) {
            if (item != null && !isBlank(item.getId())) {
                menuIdSet.add(item.getId());
            }
        }
        List<ShopMenuPageConfigDTO> menuPageConfigs;
        if (request.getMenuPageConfigs() == null) {
            menuPageConfigs = parseMenuPageConfigs(existing.getMenuPageConfigJson());
        } else {
            menuPageConfigs = normalizeMenuPageConfigs(request.getMenuPageConfigs(), menuItems, menuIdSet);
        }
        List<ShopPageMetaDTO> pageMetas;
        if (request.getPageMetas() == null) {
            pageMetas = parsePageMetas(existing.getPageMetaJson());
        } else {
            pageMetas = normalizePageMetas(request.getPageMetas(), customPageKeySet);
        }

        if (request.getShopName() != null) {
            existing.setShopName(request.getShopName());
        }
        if (request.getShopLogo() != null) {
            existing.setShopLogo(request.getShopLogo());
        }
        if (request.getHeroBanner() != null) {
            existing.setHeroBanner(request.getHeroBanner());
        }
        if (request.getAnnouncement() != null) {
            existing.setAnnouncement(request.getAnnouncement());
        }
        if (request.getDomain() != null) {
            existing.setDomain(request.getDomain());
        }
        if (request.getLanguageLabel() != null) {
            existing.setLanguageLabel(request.getLanguageLabel());
        }
        if (request.getThemeColor() != null) {
            existing.setThemeColor(request.getThemeColor());
        }
        if (request.getContactName() != null) {
            existing.setContactName(request.getContactName());
        }
        if (request.getContactWechat() != null) {
            existing.setContactWechat(request.getContactWechat());
        }
        if (request.getContactPhone() != null) {
            existing.setContactPhone(request.getContactPhone());
        }
        if (request.getCopyrightText() != null) {
            existing.setCopyrightText(request.getCopyrightText());
        }
        if (request.getBlockSearchEngineCrawl() != null) {
            existing.setBlockSearchEngineCrawl(request.getBlockSearchEngineCrawl() == 1 ? 1 : 0);
        }
        List<ShopContactItemDTO> contacts;
        if (request.getContacts() != null) {
            contacts = normalizeContacts(request.getContacts());
        } else if (legacyContactTouched) {
            contacts = buildContactsFromLegacy(existing);
        } else {
            contacts = parseContacts(existing.getContactConfigJson(), existing);
        }
        existing.setContactConfigJson(toJsonSafe(contacts));
        syncLegacyContactFields(existing, contacts);
        existing.setMenuConfigJson(toJsonSafe(menuItems));
        existing.setMenuPageConfigJson(toJsonSafe(menuPageConfigs));
        existing.setCustomPagesJson(toJsonSafe(customPages));
        existing.setPageMetaJson(toJsonSafe(pageMetas));
        wsAlbumNormalize(existing);
        shopProfileMapper.updateById(existing);
        return toVO(existing);
    }

    private ShopProfile loadOrCreateProfile() {
        ensureSchemaReady();
        ShopProfile profile = shopProfileMapper.selectById(PROFILE_ID);
        if (profile != null) {
            return profile;
        }

        ShopProfile created = defaultProfile();
        shopProfileMapper.insert(created);
        return created;
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

    private ShopProfile defaultProfile() {
        ShopProfile profile = new ShopProfile();
        profile.setId(PROFILE_ID);
        profile.setShopName("白鲸•CELINE");
        profile.setAnnouncement("专做进口原厂皮，匠心工艺，不包邮不议价，可货比数家!!");
        profile.setLanguageLabel("EN");
        profile.setThemeColor("#10b981");
        profile.setDomain("");
        profile.setContactName("");
        profile.setContactWechat("");
        profile.setContactPhone("");
        profile.setContactConfigJson("[]");
        profile.setShopLogo("");
        profile.setHeroBanner("");
        profile.setCopyrightText("");
        profile.setMenuConfigJson("[]");
        profile.setMenuPageConfigJson("[]");
        profile.setCustomPagesJson("[]");
        profile.setPageMetaJson(toJsonSafe(defaultPageMetas()));
        profile.setBlockSearchEngineCrawl(0);
        return profile;
    }

    private void ensureTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS shop_profile ("
                        + "id BIGINT NOT NULL,"
                        + "shop_name VARCHAR(120) DEFAULT NULL,"
                        + "shop_logo VARCHAR(1024) DEFAULT NULL,"
                        + "hero_banner VARCHAR(1024) DEFAULT NULL,"
                        + "announcement VARCHAR(255) DEFAULT NULL,"
                        + "domain VARCHAR(255) DEFAULT NULL,"
                        + "language_label VARCHAR(16) DEFAULT NULL,"
                        + "theme_color VARCHAR(20) DEFAULT NULL,"
                        + "contact_name VARCHAR(64) DEFAULT NULL,"
                        + "contact_wechat VARCHAR(64) DEFAULT NULL,"
                        + "contact_phone VARCHAR(64) DEFAULT NULL,"
                        + "contact_config_json TEXT DEFAULT NULL,"
                        + "copyright_text VARCHAR(255) DEFAULT NULL,"
                        + "menu_config_json TEXT DEFAULT NULL,"
                        + "menu_page_config_json MEDIUMTEXT DEFAULT NULL,"
                        + "custom_pages_json MEDIUMTEXT DEFAULT NULL,"
                        + "page_meta_json MEDIUMTEXT DEFAULT NULL,"
                        + "block_search_engine_crawl TINYINT NOT NULL DEFAULT 0,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        ensureColumn("contact_config_json", "ALTER TABLE shop_profile ADD COLUMN contact_config_json TEXT DEFAULT NULL");
        ensureColumn("menu_config_json", "ALTER TABLE shop_profile ADD COLUMN menu_config_json TEXT DEFAULT NULL");
        ensureColumn("menu_page_config_json", "ALTER TABLE shop_profile ADD COLUMN menu_page_config_json MEDIUMTEXT DEFAULT NULL");
        ensureColumn("custom_pages_json", "ALTER TABLE shop_profile ADD COLUMN custom_pages_json MEDIUMTEXT DEFAULT NULL");
        ensureColumn("page_meta_json", "ALTER TABLE shop_profile ADD COLUMN page_meta_json MEDIUMTEXT DEFAULT NULL");
        ensureColumn("block_search_engine_crawl", "ALTER TABLE shop_profile ADD COLUMN block_search_engine_crawl TINYINT NOT NULL DEFAULT 0");
    }

    private void ensureColumn(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'shop_profile' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }

    private ShopProfileVO toVO(ShopProfile profile) {
        ShopProfileVO vo = new ShopProfileVO();
        vo.setShopName(profile.getShopName());
        vo.setShopLogo(profile.getShopLogo());
        vo.setHeroBanner(profile.getHeroBanner());
        vo.setAnnouncement(profile.getAnnouncement());
        vo.setDomain(profile.getDomain());
        vo.setLanguageLabel(profile.getLanguageLabel());
        vo.setThemeColor(profile.getThemeColor());
        vo.setContactName(profile.getContactName());
        vo.setContactWechat(profile.getContactWechat());
        vo.setContactPhone(profile.getContactPhone());
        vo.setContacts(parseContacts(profile.getContactConfigJson(), profile));
        vo.setCopyrightText(profile.getCopyrightText());
        vo.setMenuItems(parseMenus(profile.getMenuConfigJson()));
        vo.setMenuPageConfigs(parseMenuPageConfigs(profile.getMenuPageConfigJson()));
        vo.setCustomPages(parseCustomPages(profile.getCustomPagesJson()));
        List<ShopPageMetaDTO> pageMetas = parsePageMetas(profile.getPageMetaJson());
        vo.setPageMetas(pageMetas.isEmpty() ? defaultPageMetas() : pageMetas);
        vo.setBlockSearchEngineCrawl(profile.getBlockSearchEngineCrawl() != null && profile.getBlockSearchEngineCrawl() == 1 ? 1 : 0);
        return vo;
    }

    private List<ShopPageMetaDTO> defaultPageMetas() {
        List<ShopPageMetaDTO> rows = new ArrayList<ShopPageMetaDTO>();
        appendDefaultPageMeta(rows, "home", "default", "{{shopName}}", "");
        appendDefaultPageMeta(rows, "product", "default", "{{productTitle}} - {{shopName}}", "");
        appendDefaultPageMeta(rows, "voucher", "default", "Order Voucher {{voucherNo}} - {{shopName}}", "");
        appendDefaultPageMeta(rows, "admin-login", "default", "后台登录 - {{shopName}}", "");
        appendDefaultPageMeta(rows, "admin", "default", "商品相册管理后台", "");
        return rows;
    }

    private void appendDefaultPageMeta(List<ShopPageMetaDTO> rows, String pageKey, String langCode, String title, String description) {
        ShopPageMetaDTO item = new ShopPageMetaDTO();
        item.setPageKey(pageKey);
        item.setLangCode(langCode);
        item.setTitle(title);
        item.setDescription(description);
        rows.add(item);
    }

    private List<ShopMenuItemDTO> parseMenus(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<ShopMenuItemDTO>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ShopMenuItemDTO>>() {
            });
        } catch (Exception ignore) {
            return new ArrayList<ShopMenuItemDTO>();
        }
    }

    private List<ShopCustomPageDTO> parseCustomPages(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<ShopCustomPageDTO>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ShopCustomPageDTO>>() {
            });
        } catch (Exception ignore) {
            return new ArrayList<ShopCustomPageDTO>();
        }
    }

    private List<ShopMenuPageConfigDTO> parseMenuPageConfigs(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<ShopMenuPageConfigDTO>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ShopMenuPageConfigDTO>>() {
            });
        } catch (Exception ignore) {
            return new ArrayList<ShopMenuPageConfigDTO>();
        }
    }

    private List<ShopPageMetaDTO> parsePageMetas(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<ShopPageMetaDTO>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ShopPageMetaDTO>>() {
            });
        } catch (Exception ignore) {
            return new ArrayList<ShopPageMetaDTO>();
        }
    }

    private List<ShopContactItemDTO> parseContacts(String json, ShopProfile profile) {
        List<ShopContactItemDTO> list = new ArrayList<ShopContactItemDTO>();
        if (!isBlank(json)) {
            try {
                List<ShopContactItemDTO> parsed = objectMapper.readValue(json, new TypeReference<List<ShopContactItemDTO>>() {
                });
                list = normalizeContactsForRead(parsed);
            } catch (Exception ignore) {
                list = new ArrayList<ShopContactItemDTO>();
            }
        }
        if (!list.isEmpty()) {
            return list;
        }
        return buildContactsFromLegacy(profile);
    }

    private List<ShopContactItemDTO> normalizeContacts(List<ShopContactItemDTO> rawList) {
        List<ShopContactItemDTO> result = new ArrayList<ShopContactItemDTO>();
        if (rawList == null) {
            return result;
        }
        for (int i = 0; i < rawList.size(); i++) {
            ShopContactItemDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String type = normalizeContactType(row.getType());
            String label = trimToEmpty(row.getLabel());
            String value = trimToEmpty(row.getValue());
            String copyValue = trimToEmpty(row.getCopyValue());
            boolean hasValue = !isBlank(label) || !isBlank(value) || !isBlank(copyValue);
            if (!hasValue) {
                continue;
            }
            if (isBlank(value)) {
                throw new BusinessException("第" + (i + 1) + "个联系方式缺少内容");
            }
            if (label.length() > 48) {
                throw new BusinessException("第" + (i + 1) + "个联系方式名称过长");
            }
            if (value.length() > 255) {
                throw new BusinessException("第" + (i + 1) + "个联系方式内容过长");
            }
            if (copyValue.length() > 255) {
                throw new BusinessException("第" + (i + 1) + "个联系方式复制内容过长");
            }
            ShopContactItemDTO item = new ShopContactItemDTO();
            item.setId(isBlank(row.getId()) ? ("contact-" + System.currentTimeMillis() + "-" + i) : row.getId().trim());
            item.setType(type);
            item.setLabel(isBlank(label) ? defaultContactLabel(type) : label);
            item.setValue(value);
            item.setCopyValue(isBlank(copyValue) ? value : copyValue);
            item.setSort(row.getSort() == null ? (i + 1) : row.getSort());
            item.setEnabled(row.getEnabled() != null && row.getEnabled() == 0 ? 0 : 1);
            result.add(item);
        }
        return result;
    }

    private List<ShopContactItemDTO> normalizeContactsForRead(List<ShopContactItemDTO> rawList) {
        List<ShopContactItemDTO> result = new ArrayList<ShopContactItemDTO>();
        if (rawList == null) {
            return result;
        }
        for (int i = 0; i < rawList.size(); i++) {
            ShopContactItemDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String type = normalizeContactType(row.getType());
            String label = trimToEmpty(row.getLabel());
            String value = trimToEmpty(row.getValue());
            String copyValue = trimToEmpty(row.getCopyValue());
            if (isBlank(value)) {
                continue;
            }
            ShopContactItemDTO item = new ShopContactItemDTO();
            item.setId(isBlank(row.getId()) ? ("contact-read-" + i) : row.getId().trim());
            item.setType(type);
            item.setLabel(isBlank(label) ? defaultContactLabel(type) : label);
            item.setValue(value);
            item.setCopyValue(isBlank(copyValue) ? value : copyValue);
            item.setSort(row.getSort() == null ? (i + 1) : row.getSort());
            item.setEnabled(row.getEnabled() != null && row.getEnabled() == 0 ? 0 : 1);
            result.add(item);
        }
        return result;
    }

    private List<ShopContactItemDTO> buildContactsFromLegacy(ShopProfile profile) {
        List<ShopContactItemDTO> result = new ArrayList<ShopContactItemDTO>();
        if (profile == null) {
            return result;
        }
        appendLegacyContact(result, "PERSON", defaultContactLabel("PERSON"), profile.getContactName());
        appendLegacyContact(result, "WECHAT", defaultContactLabel("WECHAT"), profile.getContactWechat());
        appendLegacyContact(result, "PHONE", defaultContactLabel("PHONE"), profile.getContactPhone());
        return result;
    }

    private void appendLegacyContact(List<ShopContactItemDTO> result, String type, String label, String value) {
        String val = trimToEmpty(value);
        if (isBlank(val)) {
            return;
        }
        ShopContactItemDTO item = new ShopContactItemDTO();
        item.setId("legacy-" + type.toLowerCase());
        item.setType(type);
        item.setLabel(label);
        item.setValue(val);
        item.setCopyValue(val);
        item.setSort(result.size() + 1);
        item.setEnabled(1);
        result.add(item);
    }

    private void syncLegacyContactFields(ShopProfile profile, List<ShopContactItemDTO> contacts) {
        profile.setContactName("");
        profile.setContactWechat("");
        profile.setContactPhone("");
        if (contacts == null) {
            return;
        }
        for (ShopContactItemDTO item : contacts) {
            if (item == null || (item.getEnabled() != null && item.getEnabled() == 0)) {
                continue;
            }
            String value = trimToEmpty(item.getCopyValue());
            if (isBlank(value)) {
                value = trimToEmpty(item.getValue());
            }
            if (isBlank(value)) {
                continue;
            }
            String type = normalizeContactType(item.getType());
            if ("PERSON".equals(type) && isBlank(profile.getContactName())) {
                profile.setContactName(value);
                continue;
            }
            if ("WECHAT".equals(type) && isBlank(profile.getContactWechat())) {
                profile.setContactWechat(value);
                continue;
            }
            if (("PHONE".equals(type) || "WHATSAPP".equals(type)) && isBlank(profile.getContactPhone())) {
                profile.setContactPhone(value);
            }
        }
    }

    private String normalizeContactType(String raw) {
        String value = trimToEmpty(raw).toUpperCase();
        if ("PERSON".equals(value) || "WECHAT".equals(value) || "WHATSAPP".equals(value)
                || "PHONE".equals(value) || "EMAIL".equals(value) || "CUSTOM".equals(value)) {
            return value;
        }
        return "CUSTOM";
    }

    private String defaultContactLabel(String type) {
        if ("PERSON".equals(type)) {
            return "联系人";
        }
        if ("WECHAT".equals(type)) {
            return "微信";
        }
        if ("WHATSAPP".equals(type)) {
            return "WhatsApp";
        }
        if ("PHONE".equals(type)) {
            return "电话";
        }
        if ("EMAIL".equals(type)) {
            return "邮箱";
        }
        return "联系方式";
    }

    private String toJsonSafe(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<ShopCustomPageDTO> normalizeCustomPages(List<ShopCustomPageDTO> rawList) {
        List<ShopCustomPageDTO> result = new ArrayList<ShopCustomPageDTO>();
        if (rawList == null) {
            return result;
        }
        Set<String> keySet = new LinkedHashSet<String>();
        for (int i = 0; i < rawList.size(); i++) {
            ShopCustomPageDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String key = normalizePageKey(row.getKey());
            String title = trimToEmpty(row.getTitle());
            String content = row.getContent() == null ? "" : row.getContent();
            boolean hasValue = !isBlank(key) || !isBlank(title) || !isBlank(content);
            if (!hasValue) {
                continue;
            }
            if (isBlank(key)) {
                throw new BusinessException("第" + (i + 1) + "个自定义页面缺少Key");
            }
            if (!keySet.add(key)) {
                throw new BusinessException("自定义页面Key重复：" + key);
            }
            ShopCustomPageDTO page = new ShopCustomPageDTO();
            page.setKey(key);
            page.setTitle(title);
            page.setContent(content);
            result.add(page);
        }
        return result;
    }

    private List<ShopMenuPageConfigDTO> normalizeMenuPageConfigs(List<ShopMenuPageConfigDTO> rawList,
                                                                 List<ShopMenuItemDTO> menuItems,
                                                                 Set<String> menuIdSet) {
        List<ShopMenuPageConfigDTO> result = new ArrayList<ShopMenuPageConfigDTO>();
        if (rawList == null) {
            return result;
        }
        Map<String, ShopMenuItemDTO> menuItemMap = new HashMap<String, ShopMenuItemDTO>();
        if (menuItems != null) {
            for (ShopMenuItemDTO item : menuItems) {
                if (item != null && !isBlank(item.getId())) {
                    menuItemMap.put(item.getId(), item);
                }
            }
        }
        Set<String> seenMenuIds = new LinkedHashSet<String>();
        for (int i = 0; i < rawList.size(); i++) {
            ShopMenuPageConfigDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String menuId = trimToEmpty(row.getMenuId());
            boolean hasBlocks = row.getBlocks() != null && !row.getBlocks().isEmpty();
            boolean hasValue = !isBlank(menuId) || hasBlocks;
            if (!hasValue) {
                continue;
            }
            if (isBlank(menuId)) {
                throw new BusinessException("第" + (i + 1) + "个菜单顶部活动位缺少关联菜单");
            }
            if (menuIdSet == null || !menuIdSet.contains(menuId)) {
                throw new BusinessException("第" + (i + 1) + "个菜单顶部活动位关联菜单不存在：" + menuId);
            }
            if (!seenMenuIds.add(menuId)) {
                throw new BusinessException("菜单顶部活动位重复关联菜单：" + menuId);
            }
            ShopMenuItemDTO menuItem = menuItemMap.get(menuId);
            if (menuItem == null || !MENU_TARGET_TYPES_WITH_PAGE.contains(trimToEmpty(menuItem.getTargetType()).toUpperCase())) {
                throw new BusinessException("菜单顶部活动位仅支持绑定商品列表类菜单");
            }

            ShopMenuPageConfigDTO config = new ShopMenuPageConfigDTO();
            config.setMenuId(menuId);
            config.setEnabled(row.getEnabled() != null && row.getEnabled() == 0 ? 0 : 1);
            config.setBlocks(normalizeMenuPageBlocks(row.getBlocks(), i + 1));
            result.add(config);
        }
        return result;
    }

    private List<ShopMenuPageBlockDTO> normalizeMenuPageBlocks(List<ShopMenuPageBlockDTO> rawList, int configIndex) {
        List<ShopMenuPageBlockDTO> result = new ArrayList<ShopMenuPageBlockDTO>();
        if (rawList == null) {
            return result;
        }
        Set<String> blockIds = new LinkedHashSet<String>();
        for (int i = 0; i < rawList.size(); i++) {
            ShopMenuPageBlockDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String type = trimToEmpty(row.getType()).toUpperCase();
            String title = trimToEmpty(row.getTitle());
            String subTitle = trimToEmpty(row.getSubTitle());
            String imageUrl = trimToEmpty(row.getImageUrl());
            String content = trimToEmpty(row.getContent());
            String buttonText = trimToEmpty(row.getButtonText());
            String linkType = trimToEmpty(row.getLinkType()).toUpperCase();
            String linkValue = trimToEmpty(row.getLinkValue());
            String startTime = trimToEmpty(row.getStartTime());
            String endTime = trimToEmpty(row.getEndTime());
            boolean hasValue = !isBlank(type) || !isBlank(title) || !isBlank(subTitle) || !isBlank(imageUrl)
                    || !isBlank(content) || !isBlank(buttonText) || !isBlank(linkValue)
                    || !isBlank(startTime) || !isBlank(endTime);
            if (!hasValue) {
                continue;
            }
            if (!MENU_PAGE_BLOCK_TYPES.contains(type)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个模块类型不支持");
            }
            if (isBlank(linkType)) {
                linkType = "NONE";
            }
            if (!MENU_PAGE_LINK_TYPES.contains(linkType)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个模块链接类型不支持");
            }
            if ("IMAGE".equals(type) && isBlank(imageUrl)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个图片模块缺少图片");
            }
            if ("RICH_TEXT".equals(type) && isBlank(title) && isBlank(content) && isBlank(imageUrl)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个图文模块内容为空");
            }
            if ("NOTICE".equals(type) && isBlank(title) && isBlank(content)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个公告模块内容为空");
            }
            if ("COUNTDOWN".equals(type) && isBlank(endTime)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个倒计时模块缺少结束时间");
            }
            if (!"NONE".equals(linkType) && isBlank(linkValue)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位的第" + (i + 1) + "个模块缺少跳转地址");
            }

            ShopMenuPageBlockDTO block = new ShopMenuPageBlockDTO();
            String id = isBlank(row.getId()) ? ("block-" + System.currentTimeMillis() + "-" + configIndex + "-" + i) : row.getId().trim();
            if (!blockIds.add(id)) {
                throw new BusinessException("第" + configIndex + "个菜单顶部活动位存在重复模块ID：" + id);
            }
            block.setId(id);
            block.setType(type);
            block.setTitle(title);
            block.setSubTitle(subTitle);
            block.setImageUrl(imageUrl);
            block.setContent(content);
            block.setButtonText(buttonText);
            block.setLinkType(linkType);
            block.setLinkValue(linkValue);
            block.setStartTime(startTime);
            block.setEndTime(endTime);
            block.setSort(row.getSort() == null ? (i + 1) : row.getSort());
            block.setEnabled(row.getEnabled() != null && row.getEnabled() == 0 ? 0 : 1);
            result.add(block);
        }
        return result;
    }

    private List<ShopPageMetaDTO> normalizePageMetas(List<ShopPageMetaDTO> rawList, Set<String> customPageKeySet) {
        List<ShopPageMetaDTO> result = new ArrayList<ShopPageMetaDTO>();
        if (rawList == null) {
            return result;
        }
        Set<String> keySet = new LinkedHashSet<String>();
        for (int i = 0; i < rawList.size(); i++) {
            ShopPageMetaDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String pageKey = normalizeMetaPageKey(row.getPageKey());
            String langCode = normalizeLangCode(row.getLangCode());
            String title = trimToEmpty(row.getTitle());
            String description = trimToEmpty(row.getDescription());
            boolean hasValue = !isBlank(pageKey) || !isBlank(langCode) || !isBlank(title) || !isBlank(description);
            if (!hasValue) {
                continue;
            }
            if (isBlank(pageKey)) {
                throw new BusinessException("第" + (i + 1) + "个页面SEO配置缺少页面标识");
            }
            if (isBlank(langCode)) {
                throw new BusinessException("第" + (i + 1) + "个页面SEO配置缺少语言");
            }
            if (pageKey.startsWith("page:")) {
                String customKey = pageKey.substring("page:".length());
                if (isBlank(customKey) || customPageKeySet == null || !customPageKeySet.contains(customKey)) {
                    throw new BusinessException("第" + (i + 1) + "个页面SEO配置关联的自定义页面不存在：" + customKey);
                }
            }
            String uniqueKey = pageKey + "|" + langCode;
            if (!keySet.add(uniqueKey)) {
                throw new BusinessException("页面SEO配置重复：" + pageKey + " / " + langCode);
            }
            ShopPageMetaDTO meta = new ShopPageMetaDTO();
            meta.setPageKey(pageKey);
            meta.setLangCode(langCode);
            meta.setTitle(title);
            meta.setDescription(description);
            result.add(meta);
        }
        return result;
    }

    private List<ShopMenuItemDTO> normalizeMenuItems(List<ShopMenuItemDTO> rawList, Set<String> customPageKeySet) {
        List<ShopMenuItemDTO> result = new ArrayList<ShopMenuItemDTO>();
        if (rawList == null) {
            return result;
        }
        for (int i = 0; i < rawList.size(); i++) {
            ShopMenuItemDTO row = rawList.get(i);
            if (row == null) {
                continue;
            }
            String name = trimToEmpty(row.getName());
            String parentId = trimToEmpty(row.getParentId());
            String targetType = normalizeTargetType(row.getTargetType());
            Long categoryId = row.getCategoryId();
            String tagName = normalizeTagNames(row.getTagName());
            String customPageKey = normalizePageKey(row.getCustomPageKey());
            boolean hasValue = !isBlank(name) || categoryId != null || !isBlank(tagName) || !isBlank(customPageKey);
            if (!hasValue) {
                continue;
            }
            if (isBlank(name)) {
                throw new BusinessException("第" + (i + 1) + "个菜单项缺少名称");
            }
            if ("CATEGORY".equals(targetType) && categoryId == null) {
                throw new BusinessException("第" + (i + 1) + "个菜单项需关联分类");
            }
            if ("TAG".equals(targetType) && isBlank(tagName)) {
                throw new BusinessException("第" + (i + 1) + "个菜单项需填写标签");
            }
            if ("CATEGORY_TAG".equals(targetType) && (categoryId == null || isBlank(tagName))) {
                throw new BusinessException("第" + (i + 1) + "个菜单项需同时配置分类和标签");
            }
            if ("CUSTOM_PAGE".equals(targetType)) {
                if (isBlank(customPageKey)) {
                    throw new BusinessException("第" + (i + 1) + "个菜单项需关联自定义页面");
                }
                if (customPageKeySet == null || !customPageKeySet.contains(customPageKey)) {
                    throw new BusinessException("第" + (i + 1) + "个菜单项关联页面不存在：" + customPageKey);
                }
            }

            ShopMenuItemDTO item = new ShopMenuItemDTO();
            item.setId(isBlank(row.getId()) ? ("menu-" + System.currentTimeMillis() + "-" + i) : row.getId().trim());
            item.setParentId(isBlank(parentId) ? "" : parentId);
            item.setName(name);
            item.setTargetType(targetType);
            item.setCategoryId(("CATEGORY".equals(targetType) || "CATEGORY_TAG".equals(targetType)) ? categoryId : null);
            item.setTagName(("TAG".equals(targetType) || "CATEGORY_TAG".equals(targetType)) ? tagName : "");
            item.setCustomPageKey("CUSTOM_PAGE".equals(targetType) ? customPageKey : "");
            item.setSort(row.getSort() == null ? (i + 1) : row.getSort());
            item.setEnabled(row.getEnabled() != null && row.getEnabled() == 0 ? 0 : 1);
            result.add(item);
        }
        validateMenuTree(result);
        return result;
    }

    private void validateMenuTree(List<ShopMenuItemDTO> items) {
        Map<String, ShopMenuItemDTO> idMap = new HashMap<String, ShopMenuItemDTO>();
        for (ShopMenuItemDTO item : items) {
            if (isBlank(item.getId())) {
                throw new BusinessException("菜单项ID不能为空");
            }
            if (idMap.containsKey(item.getId())) {
                throw new BusinessException("菜单项ID重复：" + item.getId());
            }
            idMap.put(item.getId(), item);
        }

        for (ShopMenuItemDTO item : items) {
            if (isBlank(item.getParentId())) {
                continue;
            }
            if (item.getId().equals(item.getParentId())) {
                throw new BusinessException("菜单项不能将自己设为父级：" + item.getName());
            }
            if (!idMap.containsKey(item.getParentId())) {
                throw new BusinessException("菜单项父级不存在：" + item.getName());
            }

            Set<String> seen = new LinkedHashSet<String>();
            String cursor = item.getId();
            while (!isBlank(cursor)) {
                if (!seen.add(cursor)) {
                    throw new BusinessException("菜单层级存在循环引用：" + item.getName());
                }
                ShopMenuItemDTO node = idMap.get(cursor);
                if (node == null || isBlank(node.getParentId())) {
                    break;
                }
                cursor = node.getParentId();
            }
        }
    }

    private String normalizeTargetType(String raw) {
        String value = trimToEmpty(raw).toUpperCase();
        if ("GROUP".equals(value) || "CATEGORY".equals(value) || "TAG".equals(value) || "CATEGORY_TAG".equals(value) || "CUSTOM_PAGE".equals(value)) {
            return value;
        }
        return "CATEGORY";
    }

    private String normalizeTagNames(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String[] arr = raw.split(",");
        Set<String> values = new LinkedHashSet<String>();
        for (String item : arr) {
            if (!isBlank(item)) {
                values.add(item.trim());
            }
        }
        return String.join(",", values);
    }

    private String normalizePageKey(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String key = raw.trim().toLowerCase();
        key = key.replaceAll("\\s+", "-");
        key = key.replaceAll("[^a-z0-9-_]", "");
        return key;
    }

    private String normalizeMetaPageKey(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        String value = raw.trim().toLowerCase();
        if (value.startsWith("page:")) {
            return "page:" + normalizePageKey(value.substring("page:".length()));
        }
        if ("home".equals(value) || "product".equals(value) || "voucher".equals(value) || "admin".equals(value) || "admin-login".equals(value)) {
            return value;
        }
        return normalizePageKey(value);
    }

    private String normalizeLangCode(String raw) {
        if (isBlank(raw)) {
            return "";
        }
        return raw.trim().replace('_', '-').toLowerCase();
    }

    private String trimToEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private boolean isBlank(String raw) {
        return raw == null || raw.trim().isEmpty();
    }

    private void wsAlbumNormalize(ShopProfile profile) {
        if (profile.getContactConfigJson() == null || profile.getContactConfigJson().trim().isEmpty()) {
            profile.setContactConfigJson("[]");
        }
        if (profile.getMenuConfigJson() == null || profile.getMenuConfigJson().trim().isEmpty()) {
            profile.setMenuConfigJson("[]");
        }
        if (profile.getCustomPagesJson() == null || profile.getCustomPagesJson().trim().isEmpty()) {
            profile.setCustomPagesJson("[]");
        }
        if (profile.getPageMetaJson() == null || profile.getPageMetaJson().trim().isEmpty()) {
            profile.setPageMetaJson("[]");
        }
    }
}
