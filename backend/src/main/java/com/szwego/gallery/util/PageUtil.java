package com.szwego.gallery.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.szwego.gallery.common.PageResponse;

public class PageUtil {
    private PageUtil() {
    }

    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        PageResponse<T> response = new PageResponse<T>();
        response.setPage(page.getCurrent());
        response.setSize(page.getSize());
        response.setTotal(page.getTotal());
        response.setPages(page.getPages());
        response.setRecords(page.getRecords());
        return response;
    }
}
